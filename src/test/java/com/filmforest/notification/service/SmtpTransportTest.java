package com.filmforest.notification.service;

import com.filmforest.notification.entity.SmtpSetting;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmtpTransportTest {

    private final SmtpTransport transport = new SmtpTransport();

    @Test
    void sendsUtf8MessageThroughAuthenticatedTestServer() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(ReplyMode.SUCCESS)) {
            transport.send(setting(server.port(), true), "secret", "admin@example.test", "爬虫失败", "任务已中断");

            assertThat(server.message()).contains(
                    "Subject: =?UTF-8?B?",
                    "Content-Type: text/plain; charset=UTF-8",
                    "Content-Transfer-Encoding: base64");
            assertThat(server.message()).doesNotContain("任务已中断");
        }
    }

    @Test
    void classifiesAuthenticationAndTemporaryAndPermanentRejections() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(ReplyMode.AUTH_FAIL)) {
            assertThatThrownBy(() -> transport.test(setting(server.port(), true), "wrong"))
                    .isInstanceOfSatisfying(SmtpService.SmtpDeliveryException.class,
                            error -> assertThat(error.category()).isEqualTo("AUTHENTICATION_FAILED"));
        }
        try (FakeSmtpServer server = new FakeSmtpServer(ReplyMode.TEMPORARY_REJECT)) {
            assertThatThrownBy(() -> transport.send(setting(server.port(), false), "", "admin@example.test", "测试", "内容"))
                    .isInstanceOfSatisfying(SmtpService.SmtpDeliveryException.class,
                            error -> assertThat(error.category()).isEqualTo("TEMPORARY_REJECTED"));
        }
        try (FakeSmtpServer server = new FakeSmtpServer(ReplyMode.PERMANENT_REJECT)) {
            assertThatThrownBy(() -> transport.send(setting(server.port(), false), "", "admin@example.test", "测试", "内容"))
                    .isInstanceOfSatisfying(SmtpService.SmtpDeliveryException.class,
                            error -> assertThat(error.category()).isEqualTo("PERMANENT_REJECTED"));
        }
    }

    private static SmtpSetting setting(int port, boolean authenticated) {
        SmtpSetting setting = new SmtpSetting();
        setting.setHost("127.0.0.1");
        setting.setPort(port);
        setting.setSecurityMode("NONE");
        setting.setFromEmail("forest@example.test");
        setting.setFromName("影视森林");
        if (authenticated) setting.setUsername("mailer");
        return setting;
    }

    private enum ReplyMode { SUCCESS, AUTH_FAIL, TEMPORARY_REJECT, PERMANENT_REJECT }

    private static final class FakeSmtpServer implements AutoCloseable {
        private final ServerSocket server;
        private final Thread thread;
        private final ReplyMode mode;
        private final AtomicReference<String> message = new AtomicReference<>("");

        private FakeSmtpServer(ReplyMode mode) throws IOException {
            this.mode = mode;
            server = new ServerSocket(0);
            thread = new Thread(this::serve, "smtp-test-server");
            thread.start();
        }

        private int port() { return server.getLocalPort(); }
        private String message() { return message.get(); }

        private void serve() {
            try (Socket socket = server.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
                reply(writer, "220 test-smtp");
                expect(reader, "EHLO");
                reply(writer, "250-test-smtp\r\n250 AUTH LOGIN");
                String command = reader.readLine();
                if (command != null && command.startsWith("AUTH LOGIN")) {
                    reply(writer, "334 VXNlcm5hbWU6");
                    reader.readLine();
                    reply(writer, "334 UGFzc3dvcmQ6");
                    reader.readLine();
                    if (mode == ReplyMode.AUTH_FAIL) { reply(writer, "535 authentication failed"); return; }
                    reply(writer, "235 authenticated");
                    command = reader.readLine();
                }
                if (command != null && command.startsWith("QUIT")) { reply(writer, "221 bye"); return; }
                if (command == null || !command.startsWith("MAIL FROM")) return;
                reply(writer, "250 sender ok");
                expect(reader, "RCPT TO");
                if (mode == ReplyMode.TEMPORARY_REJECT) { reply(writer, "450 retry later"); return; }
                if (mode == ReplyMode.PERMANENT_REJECT) { reply(writer, "550 recipient rejected"); return; }
                reply(writer, "250 recipient ok");
                expect(reader, "DATA");
                reply(writer, "354 continue");
                StringBuilder payload = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && !line.equals(".")) payload.append(line).append("\r\n");
                message.set(payload.toString());
                reply(writer, "250 queued");
                expect(reader, "QUIT");
                reply(writer, "221 bye");
            } catch (IOException ignored) {
            }
        }

        private static void expect(BufferedReader reader, String prefix) throws IOException {
            String line = reader.readLine();
            if (line == null || !line.startsWith(prefix)) throw new IOException("expected " + prefix);
        }

        private static void reply(BufferedWriter writer, String value) throws IOException {
            writer.write(value + "\r\n");
            writer.flush();
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(2_000);
        }
    }
}
