package com.filmforest.notification.service;

import com.filmforest.notification.entity.SmtpSetting;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 最小 SMTP 客户端，支持 NONE、STARTTLS、SSL 与 AUTH LOGIN，不引入额外运行时依赖。 */
@Component
public class SmtpTransport {
    private static final int TIMEOUT_MS = 10_000;

    public void test(SmtpSetting setting, String password) {
        try (Session session = connect(setting, password)) {
            session.command("QUIT", 221);
        } catch (IOException exception) {
            throw classify(exception);
        }
    }

    public void send(SmtpSetting setting, String password, String recipient, String subject, String body) {
        validateHeaderValue(recipient, "收件人");
        validateHeaderValue(setting.getFromEmail(), "发件人");
        validateHeaderValue(subject, "主题");
        try (Session session = connect(setting, password)) {
            session.command("MAIL FROM:<" + setting.getFromEmail() + ">", 250);
            session.command("RCPT TO:<" + recipient + ">", 250, 251);
            session.command("DATA", 354);
            String from = setting.getFromName() == null || setting.getFromName().isBlank()
                    ? setting.getFromEmail()
                    : encodedWord(setting.getFromName()) + " <" + setting.getFromEmail() + ">";
            String encodedBody = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(body.getBytes(StandardCharsets.UTF_8));
            session.writeRaw("From: " + from + "\r\n"
                    + "To: " + recipient + "\r\n"
                    + "Subject: " + encodedWord(subject) + "\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: base64\r\n\r\n"
                    + encodedBody + "\r\n.\r\n");
            session.expect(250);
            session.command("QUIT", 221);
        } catch (IOException exception) {
            throw classify(exception);
        }
    }

    private Session connect(SmtpSetting setting, String password) throws IOException {
        Socket socket = "SSL".equals(setting.getSecurityMode())
                ? sslSocket(setting.getHost(), setting.getPort())
                : plainSocket(setting.getHost(), setting.getPort());
        Session session = new Session(socket);
        try {
            session.expect(220);
            session.command("EHLO film-forest", 250);
            if ("STARTTLS".equals(setting.getSecurityMode())) {
                session.command("STARTTLS", 220);
                session = session.upgradeTls(setting.getHost(), setting.getPort());
                session.command("EHLO film-forest", 250);
            }
            if (setting.getUsername() != null && !setting.getUsername().isBlank()) {
                session.command("AUTH LOGIN", 334);
                session.command(Base64.getEncoder().encodeToString(setting.getUsername().getBytes(StandardCharsets.UTF_8)), 334);
                session.command(Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)), 235);
            }
            return session;
        } catch (IOException exception) {
            session.close();
            throw exception;
        }
    }

    private static Socket plainSocket(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        return socket;
    }

    private static Socket sslSocket(String host, int port) throws IOException {
        SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        socket.startHandshake();
        return socket;
    }

    private static String encodedWord(String value) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static void validateHeaderValue(String value, String label) {
        if (value == null || value.isBlank() || value.contains("\r") || value.contains("\n")) {
            throw new SmtpService.SmtpDeliveryException("CONFIGURATION_ERROR", label + "格式不合法");
        }
    }

    static SmtpService.SmtpDeliveryException classify(IOException exception) {
        if (exception instanceof SocketTimeoutException) return new SmtpService.SmtpDeliveryException("TIMEOUT", "SMTP 连接或响应超时");
        if (exception instanceof UnknownHostException) return new SmtpService.SmtpDeliveryException("DNS_ERROR", "无法解析 SMTP 服务器地址");
        if (exception instanceof ConnectException) return new SmtpService.SmtpDeliveryException("CONNECTION_REFUSED", "无法连接 SMTP 服务器");
        if (exception instanceof SmtpReplyException reply) {
            if (reply.code == 535) return new SmtpService.SmtpDeliveryException("AUTHENTICATION_FAILED", "SMTP 认证失败，请检查用户名、密码或授权码");
            if (reply.code >= 400 && reply.code < 500) return new SmtpService.SmtpDeliveryException("TEMPORARY_REJECTED", "SMTP 服务器暂时拒绝本次请求");
            if (reply.code >= 500) return new SmtpService.SmtpDeliveryException("PERMANENT_REJECTED", "SMTP 服务器永久拒绝本次请求");
        }
        return new SmtpService.SmtpDeliveryException("DELIVERY_FAILED", "SMTP 连接或投递失败");
    }

    private static final class Session implements AutoCloseable {
        private Socket socket;
        private BufferedReader reader;
        private BufferedWriter writer;

        private Session(Socket socket) throws IOException {
            bind(socket);
        }

        private void bind(Socket next) throws IOException {
            socket = next;
            reader = new BufferedReader(new InputStreamReader(next.getInputStream(), StandardCharsets.US_ASCII));
            writer = new BufferedWriter(new OutputStreamWriter(next.getOutputStream(), StandardCharsets.US_ASCII));
        }

        private Session upgradeTls(String host, int port) throws IOException {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket tls = (SSLSocket) factory.createSocket(socket, host, port, true);
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            tls.setSSLParameters(parameters);
            tls.setSoTimeout(TIMEOUT_MS);
            tls.startHandshake();
            bind(tls);
            return this;
        }

        private void command(String command, int... expected) throws IOException {
            writeRaw(command + "\r\n");
            expect(expected);
        }

        private void writeRaw(String value) throws IOException {
            writer.write(value);
            writer.flush();
        }

        private void expect(int... expected) throws IOException {
            String line = reader.readLine();
            if (line == null || line.length() < 3) throw new IOException("SMTP 响应不完整");
            int code;
            try { code = Integer.parseInt(line.substring(0, 3)); }
            catch (NumberFormatException exception) { throw new IOException("SMTP 响应码不合法", exception); }
            while (line.length() > 3 && line.charAt(3) == '-') {
                line = reader.readLine();
                if (line == null) throw new IOException("SMTP 多行响应不完整");
            }
            for (int candidate : expected) if (candidate == code) return;
            throw new SmtpReplyException(code);
        }

        @Override
        public void close() {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private static final class SmtpReplyException extends IOException {
        private final int code;
        private SmtpReplyException(int code) { super("SMTP reply " + code); this.code = code; }
    }
}
