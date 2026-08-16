package com.filmforest.crawler.source.pkmp4;

import com.filmforest.crawler.model.ParsedResource;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Pkmp4ResourceParser {

    private static final Pattern PASSWORD = Pattern.compile(
            "(?iu)(?:提取码|提取碼|密码|密碼|访问码|訪問碼|分享码|分享碼|"
                    + "分享密码|分享密碼|访问密码|訪問密碼|access\\s*code|"
                    + "access\\s*password|password|passcode|pwd)"
                    + "\\s*(?:[：:=]|[\\[【(（])?\\s*"
                    + "([^\\s,，;；。.!！？!?\\[\\]【】（）()]{1,50})");
    private static final Set<String> PASSWORD_QUERY_KEYS = Set.of(
            "pwd", "pw", "pass", "password", "passwd", "passcode", "code",
            "accesscode", "access_code", "accesskey", "access_key",
            "accesspassword", "access_password", "extractioncode", "extraction_code",
            "sharecode", "share_code", "sharepassword", "share_password", "提取码", "提取碼",
            "密码", "密碼", "访问码", "訪問碼", "访问密码", "訪問密碼",
            "分享码", "分享碼", "分享密码", "分享密碼");
    private static final Pattern NUMBERED_EPISODE = Pattern.compile("第\\s*(\\d+)\\s*[集期]");
    private static final Pattern LATIN_EPISODE = Pattern.compile("(?i)(?:EP?|Episode)\\s*0*(\\d+)");
    private static final Pattern UPDATED_EPISODE = Pattern.compile("更新至\\s*(\\d+)\\s*集");
    private static final Pattern SEASON = Pattern.compile("(?:第\\s*(\\d+)\\s*季|(?i:S)0*(\\d+))");

    public List<ParsedResource> parse(Document document, URI finalUri) {
        List<ParsedResource> resources = new ArrayList<>();
        parseDownloads(document, resources);
        parseOnline(document, finalUri, resources);
        Map<String, ParsedResource> unique = new LinkedHashMap<>();
        for (ParsedResource resource : resources) {
            unique.putIfAbsent(resource.kind() + "\u0000" + resource.url(), resource);
        }
        return List.copyOf(unique.values());
    }

    private void parseDownloads(Document document, List<ParsedResource> resources) {
        int order = 0;
        for (Element link : document.select("p.down-list3 > a[href], .down-list3 a[href], "
                + "[class*=down-list] a[href]")) {
            String rawUrl = link.attr("href").trim();
            String rawText = link.text().trim();
            String title = firstNonBlank(link.attr("title"), rawText);
            if (rawUrl.regionMatches(true, 0, "magnet:", 0, 7)) {
                resources.add(new ParsedResource(ParsedResource.Kind.MAGNET, title, rawUrl,
                        null, null, resolution(title), containsSubtitle(title), containsSpecialSubtitle(title),
                        null, null, null, order++, rawText, null, null));
                continue;
            }
            String url = link.absUrl("href").isBlank() ? rawUrl : link.absUrl("href");
            String diskType = diskType(url);
            String context = siblingContext(link);
            resources.add(new ParsedResource(ParsedResource.Kind.CLOUD, title, url,
                    diskType, password(url, title + " " + rawText + " " + context), null, false, false,
                    null, null, null, order++, rawText, null, null));
        }
    }

    boolean hasDownloadSection(Document document) {
        return document.selectFirst("p.down-list3, .down-list3, [class*=down-list]") != null;
    }

    boolean hasOnlineSection(Document document) {
        return document.selectFirst("ul.showplayul, a[href*=/py/]") != null;
    }

    private void parseOnline(Document document, URI finalUri, List<ParsedResource> resources) {
        int order = 0;
        Map<String, Element> links = new LinkedHashMap<>();
        Map<String, String> providers = new LinkedHashMap<>();
        for (Element group : document.select("ul.showplayul")) {
            Element heading = group.previousElementSibling();
            Element label = heading == null ? null : heading.selectFirst("span");
            String provider = label == null ? "" : label.text().trim();
            for (Element link : group.select("a[href*=/py/]")) {
                links.putIfAbsent(link.attr("href"), link);
                providers.putIfAbsent(link.attr("href"), provider);
            }
        }
        for (Element link : document.select("a[href*=/py/]")) {
            links.putIfAbsent(link.attr("href"), link);
        }
        for (Map.Entry<String, Element> entry : links.entrySet()) {
            Element link = entry.getValue();
            String title = link.text().trim();
            String provider = providers.getOrDefault(entry.getKey(), "");
            String sourceName = provider.isBlank() ? title : provider + " · " + title;
            String href = entry.getKey();
            URI resourceUri = finalUri.resolve(href);
            if (!resourceUri.getPath().startsWith("/py/")) continue;
            resources.add(new ParsedResource(ParsedResource.Kind.ONLINE, sourceName,
                    resourceUri.toString(), null, null, null, false, false,
                    season(title), episodeNumber(title), title, order++, title,
                    resourceUri.toString(), "EXTERNAL_PAGE"));
        }
    }

    static Integer episodeNumber(String title) {
        if (title == null) {
            return null;
        }
        for (Pattern pattern : List.of(NUMBERED_EPISODE, LATIN_EPISODE, UPDATED_EPISODE)) {
            Matcher matcher = pattern.matcher(title);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }

    static Integer season(String title) {
        if (title == null) {
            return 1;
        }
        Matcher matcher = SEASON.matcher(title);
        if (!matcher.find()) {
            return 1;
        }
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return Integer.parseInt(value);
    }

    static String diskType(String url) {
        String normalized = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("thunder:")) return "xunlei";
        String host = host(normalized);
        if (isDomain(host, "baidu.com")) return "baidu";
        if (isDomain(host, "quark.cn")) return "quark";
        if (isLanzouHost(host)) return "lanzou";
        if (isDomain(host, "xunlei.com")) return "xunlei";
        if (isDomain(host, "uc.cn")) return "uc";
        if (isDomain(host, "alipan.com") || isDomain(host, "aliyundrive.com")
                || isDomain(host, "ali.com")) return "ali";
        if (isDomain(host, "123pan.com") || isDomain(host, "123.com")) return "123";
        return "other";
    }

    private static String password(String url, String text) {
        String queryPassword = queryPassword(url);
        if (queryPassword != null) return queryPassword;
        Matcher matcher = PASSWORD.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String siblingContext(Element link) {
        StringBuilder context = new StringBuilder();
        Element parent = link.parent();
        for (int depth = 0; parent != null && depth < 3; depth++) {
            List<Node> children = parent.childNodes();
            int linkIndex = children.indexOf(link);
            if (linkIndex >= 0) {
                if (parent.select("a[href]").size() <= 1) {
                    append(context, parent.text());
                } else {
                    appendAdjacent(context, children, linkIndex, 1);
                    appendAdjacent(context, children, linkIndex, -1);
                }
            }
            Element container = parent.parent();
            if (container == null) {
                break;
            }
            List<Node> siblings = container.childNodes();
            int index = siblings.indexOf(parent);
            if (container.select("a[href]").size() <= 1) {
                append(context, container.text());
            } else {
                appendAdjacent(context, siblings, index, 1);
                appendAdjacent(context, siblings, index, -1);
            }
            parent = container;
        }
        return context.toString();
    }

    private static void appendAdjacent(StringBuilder context, List<Node> siblings,
                                       int start, int direction) {
        if (start < 0) return;
        int index = start + direction;
        int inspected = 0;
        while (index >= 0 && index < siblings.size() && inspected++ < 6) {
            Node node = siblings.get(index);
            if (containsLink(node)) break;
            String value = node instanceof TextNode textNode ? textNode.text()
                    : node instanceof Element element ? element.text() : node.toString();
            append(context, value);
            index += direction;
        }
    }

    private static boolean containsLink(Node node) {
        return node instanceof Element element
                && ((element.is("a") && element.hasAttr("href"))
                || !element.select("a[href]").isEmpty());
    }

    private static void append(StringBuilder context, String value) {
        if (value != null && !value.isBlank()) context.append(' ').append(value);
    }

    private static String queryPassword(String url) {
        try {
            String query = URI.create(url).getRawQuery();
            if (query == null || query.isBlank()) return null;
            for (String parameter : query.split("&")) {
                String[] parts = parameter.split("=", 2);
                String key = decode(parts[0]).toLowerCase(Locale.ROOT);
                if (!PASSWORD_QUERY_KEYS.contains(key)) continue;
                String value = parts.length == 1 ? "" : decode(parts[1]);
                if (!value.isBlank()) return value.trim();
            }
        } catch (RuntimeException ignored) {
            // The normalizer will report an invalid URL without dropping the source item.
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String host(String value) {
        try {
            return URI.create(value).getHost();
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean isDomain(String host, String domain) {
        return host != null && (host.equals(domain) || host.endsWith("." + domain));
    }

    private static boolean isLanzouHost(String host) {
        return List.of("lanzou.com", "lanzouk.com", "lanzoui.com", "lanzouv.com",
                        "lanzoux.com", "lanzouj.com", "lanzoum.com")
                .stream().anyMatch(domain -> isDomain(host, domain));
    }

    private static String resolution(String text) {
        String normalized = text == null ? "" : text.toUpperCase(Locale.ROOT);
        if (normalized.contains("4K") || normalized.contains("2160")) return "4K";
        if (normalized.contains("1080") || normalized.contains("全高清")) return "1080P";
        if (normalized.contains("720")) return "720P";
        if (normalized.contains("480")) return "480P";
        return "Unknown";
    }

    private static boolean containsSubtitle(String text) {
        return text != null && (text.contains("中字") || text.contains("字幕") || text.contains("Sub"));
    }

    private static boolean containsSpecialSubtitle(String text) {
        return text != null && (text.contains("特效") || text.contains("特效字幕"));
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : second;
    }
}
