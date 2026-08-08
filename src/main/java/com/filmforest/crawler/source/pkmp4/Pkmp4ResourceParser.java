package com.filmforest.crawler.source.pkmp4;

import com.filmforest.crawler.model.ParsedResource;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Pkmp4ResourceParser {

    private static final Pattern PASSWORD = Pattern.compile("(?:提取码|密码|访问码)[：:]?\\s*([A-Za-z0-9]{3,8})");
    private static final Pattern NUMBERED_EPISODE = Pattern.compile("第\\s*(\\d+)\\s*[集期]");
    private static final Pattern LATIN_EPISODE = Pattern.compile("(?i)(?:EP?|Episode)\\s*0*(\\d+)");
    private static final Pattern UPDATED_EPISODE = Pattern.compile("更新至\\s*(\\d+)\\s*集");
    private static final Pattern SEASON = Pattern.compile("(?:第\\s*(\\d+)\\s*季|(?i:S)0*(\\d+))");

    public List<ParsedResource> parse(Document document, URI finalUri) {
        List<ParsedResource> resources = new ArrayList<>();
        parseDownloads(document, resources);
        parseOnline(document, finalUri, resources);
        return List.copyOf(resources);
    }

    private void parseDownloads(Document document, List<ParsedResource> resources) {
        int order = 0;
        for (Element link : document.select("p.down-list3 > a[href]")) {
            String url = link.attr("href").trim();
            String rawText = link.text().trim();
            String title = firstNonBlank(link.attr("title"), rawText);
            if (url.startsWith("magnet:")) {
                resources.add(new ParsedResource(ParsedResource.Kind.MAGNET, title, url,
                        null, null, resolution(title), containsSubtitle(title), containsSpecialSubtitle(title),
                        null, null, null, order++, rawText));
                continue;
            }
            String diskType = diskType(url);
            if (diskType == null) {
                continue;
            }
            resources.add(new ParsedResource(ParsedResource.Kind.CLOUD, title, url,
                    diskType, password(title + " " + rawText), null, false, false,
                    null, null, null, order++, rawText));
        }
    }

    private void parseOnline(Document document, URI finalUri, List<ParsedResource> resources) {
        int order = 0;
        for (Element link : document.select("a[href^=/py/]")) {
            String title = link.text().trim();
            String href = link.attr("href");
            resources.add(new ParsedResource(ParsedResource.Kind.ONLINE, title,
                    finalUri.resolve(href).toString(), null, null, null, false, false,
                    season(title), episodeNumber(title), title, order++, title));
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
        String normalized = url.toLowerCase(Locale.ROOT);
        if (normalized.contains("pan.baidu") || normalized.contains("baidu.com")) return "baidu";
        if (normalized.contains("quark")) return "quark";
        if (normalized.contains("lanzou") || normalized.contains("lanzouk")) return "lanzou";
        if (normalized.contains("xunlei") || normalized.contains("thunder")) return "xunlei";
        if (normalized.contains("uc.cn") || normalized.contains("drive.uc")) return "uc";
        if (normalized.contains("alipan") || normalized.contains("aliyundrive")) return "ali";
        if (normalized.contains("123pan") || normalized.contains("123.com")) return "123";
        return null;
    }

    private static String password(String text) {
        Matcher matcher = PASSWORD.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
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
