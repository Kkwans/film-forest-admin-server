package com.filmforest.crawler.service;

import com.filmforest.crawler.dto.CrawlerSchedulePreview;
import com.filmforest.crawler.dto.CrawlerSchedulePreviewRequest;
import com.filmforest.crawler.entity.CrawlerScheduleMode;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CrawlerScheduleDefinitionService {

    public static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final List<String> WEEKDAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    private static final Pattern INTERVAL_MINUTES = Pattern.compile("0 \\*/(\\d+) \\* \\* \\* \\*");
    private static final Pattern INTERVAL_HOURS = Pattern.compile("0 0 \\*/(\\d+) \\* \\* \\*");
    private static final Pattern DAILY = Pattern.compile("0 (\\d{1,2}) (\\d{1,2}) \\* \\* \\*");
    private static final Pattern WEEKLY = Pattern.compile("0 (\\d{1,2}) (\\d{1,2}) \\* \\* ([A-Z,]+)");
    private static final Pattern MONTHLY = Pattern.compile("0 (\\d{1,2}) (\\d{1,2}) (\\d{1,2}) \\* \\*");

    public CrawlerSchedulePreview preview(CrawlerSchedulePreviewRequest request) {
        String timezone = normalizeTimezone(request.timezone());
        Definition definition;
        if ((request.scheduleMode() == null || request.scheduleMode().isBlank())
                && request.cronExpression() != null && !request.cronExpression().isBlank()) {
            definition = recognize(request.cronExpression());
        } else {
            definition = normalize(request.scheduleMode(), request.scheduleConfig(), request.cronExpression());
        }
        List<ZonedDateTime> nextRuns = definition.cronExpression() == null
                ? List.of()
                : nextRuns(definition.cronExpression(), timezone, 5);
        return new CrawlerSchedulePreview(
                definition.cronExpression(), definition.mode().name(), definition.config(), timezone,
                describe(definition), nextRuns);
    }

    public Definition normalize(String modeValue, Map<String, Object> rawConfig, String rawCron) {
        if ((modeValue == null || modeValue.isBlank()) && rawCron != null && !rawCron.isBlank()) {
            return recognize(rawCron);
        }
        CrawlerScheduleMode mode = CrawlerScheduleMode.from(modeValue);
        Map<String, Object> config = rawConfig == null ? Map.of() : new LinkedHashMap<>(rawConfig);
        return switch (mode) {
            case MANUAL -> new Definition(mode, Map.of(), null);
            case INTERVAL -> interval(config);
            case DAILY -> daily(config);
            case WEEKLY -> weekly(config);
            case MONTHLY -> monthly(config);
            case CUSTOM_CRON -> customCron(rawCron, config);
        };
    }

    public Definition recognize(String rawCron) {
        String cron = normalizeCron(rawCron);
        Matcher minutes = INTERVAL_MINUTES.matcher(cron);
        if (minutes.matches()) {
            return interval(Map.of("unit", "minutes", "interval", Integer.parseInt(minutes.group(1))));
        }
        Matcher hours = INTERVAL_HOURS.matcher(cron);
        if (hours.matches()) {
            return interval(Map.of("unit", "hours", "interval", Integer.parseInt(hours.group(1))));
        }
        Matcher monthly = MONTHLY.matcher(cron);
        if (monthly.matches()) {
            return monthly(Map.of("minute", Integer.parseInt(monthly.group(1)),
                    "hour", Integer.parseInt(monthly.group(2)), "day", Integer.parseInt(monthly.group(3))));
        }
        Matcher weekly = WEEKLY.matcher(cron);
        if (weekly.matches() && Arrays.stream(weekly.group(3).split(",")).allMatch(WEEKDAYS::contains)) {
            return weekly(Map.of("minute", Integer.parseInt(weekly.group(1)),
                    "hour", Integer.parseInt(weekly.group(2)), "days", List.of(weekly.group(3).split(","))));
        }
        Matcher daily = DAILY.matcher(cron);
        if (daily.matches()) {
            return daily(Map.of("minute", Integer.parseInt(daily.group(1)),
                    "hour", Integer.parseInt(daily.group(2))));
        }
        validateCron(cron);
        return new Definition(CrawlerScheduleMode.CUSTOM_CRON, Map.of("cronExpression", cron), cron);
    }

    public String normalizeTimezone(String timezone) {
        String normalized = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone.trim();
        try {
            ZoneId.of(normalized);
            return normalized;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("无效时区: " + normalized, invalid);
        }
    }

    private Definition interval(Map<String, Object> config) {
        String unit = String.valueOf(config.getOrDefault("unit", "minutes")).toLowerCase(Locale.ROOT);
        int interval = integer(config, "interval", 1);
        String cron;
        if ("minutes".equals(unit)) {
            requireRange("分钟间隔", interval, 1, 59);
            cron = "0 */" + interval + " * * * *";
        } else if ("hours".equals(unit)) {
            requireRange("小时间隔", interval, 1, 23);
            cron = "0 0 */" + interval + " * * *";
        } else {
            throw new IllegalArgumentException("间隔单位只允许 minutes 或 hours");
        }
        return new Definition(CrawlerScheduleMode.INTERVAL,
                Map.of("unit", unit, "interval", interval), cron);
    }

    private Definition daily(Map<String, Object> config) {
        int hour = integer(config, "hour", 2);
        int minute = integer(config, "minute", 0);
        requireTime(hour, minute);
        return new Definition(CrawlerScheduleMode.DAILY,
                Map.of("hour", hour, "minute", minute), "0 " + minute + " " + hour + " * * *");
    }

    private Definition weekly(Map<String, Object> config) {
        int hour = integer(config, "hour", 2);
        int minute = integer(config, "minute", 0);
        requireTime(hour, minute);
        Object rawDays = config.get("days");
        if (!(rawDays instanceof List<?> days) || days.isEmpty()) {
            throw new IllegalArgumentException("每周定时至少选择一天");
        }
        List<String> normalizedDays = days.stream()
                .map(String::valueOf)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted((left, right) -> Integer.compare(WEEKDAYS.indexOf(left), WEEKDAYS.indexOf(right)))
                .toList();
        if (normalizedDays.stream().anyMatch(day -> !WEEKDAYS.contains(day))) {
            throw new IllegalArgumentException("每周日期只允许 MON 至 SUN");
        }
        return new Definition(CrawlerScheduleMode.WEEKLY,
                Map.of("days", normalizedDays, "hour", hour, "minute", minute),
                "0 " + minute + " " + hour + " * * " + String.join(",", normalizedDays));
    }

    private Definition monthly(Map<String, Object> config) {
        int day = integer(config, "day", 1);
        int hour = integer(config, "hour", 2);
        int minute = integer(config, "minute", 0);
        requireRange("每月日期", day, 1, 31);
        requireTime(hour, minute);
        return new Definition(CrawlerScheduleMode.MONTHLY,
                Map.of("day", day, "hour", hour, "minute", minute),
                "0 " + minute + " " + hour + " " + day + " * *");
    }

    private Definition customCron(String rawCron, Map<String, Object> config) {
        String configCron = config.get("cronExpression") == null ? null : String.valueOf(config.get("cronExpression"));
        String cron = normalizeCron(rawCron == null || rawCron.isBlank() ? configCron : rawCron);
        validateCron(cron);
        return new Definition(CrawlerScheduleMode.CUSTOM_CRON, Map.of("cronExpression", cron), cron);
    }

    private List<ZonedDateTime> nextRuns(String cron, String timezone, int count) {
        CronExpression expression = CronExpression.parse(cron);
        ZonedDateTime cursor = ZonedDateTime.now(ZoneId.of(timezone));
        List<ZonedDateTime> runs = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            cursor = expression.next(cursor);
            if (cursor == null) break;
            runs.add(cursor);
        }
        return List.copyOf(runs);
    }

    private String describe(Definition definition) {
        return switch (definition.mode()) {
            case MANUAL -> "仅手工启动，不自动运行";
            case INTERVAL -> "每隔 " + definition.config().get("interval")
                    + ("hours".equals(definition.config().get("unit")) ? " 小时" : " 分钟") + "运行";
            case DAILY -> String.format("每天 %02d:%02d 运行", definition.config().get("hour"), definition.config().get("minute"));
            case WEEKLY -> "每周 " + definition.config().get("days") + String.format(" %02d:%02d 运行",
                    definition.config().get("hour"), definition.config().get("minute"));
            case MONTHLY -> "每月 " + definition.config().get("day") + String.format(" 日 %02d:%02d 运行",
                    definition.config().get("hour"), definition.config().get("minute"));
            case CUSTOM_CRON -> "高级自定义 Cron（无法安全映射为图形向导）";
        };
    }

    private static String normalizeCron(String rawCron) {
        if (rawCron == null || rawCron.isBlank()) throw new IllegalArgumentException("Cron 表达式不能为空");
        String cron = rawCron.trim().replaceAll("\\s+", " ");
        if (cron.split(" ").length == 5) cron = "0 " + cron;
        return cron;
    }

    private static void validateCron(String cron) {
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("无效 Cron 表达式: " + cron, invalid);
        }
    }

    private static int integer(Map<String, Object> config, String key, int fallback) {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(key + " 必须是整数", invalid);
        }
    }

    private static void requireTime(int hour, int minute) {
        requireRange("小时", hour, 0, 23);
        requireRange("分钟", minute, 0, 59);
    }

    private static void requireRange(String label, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + "必须在 " + minimum + " 到 " + maximum + " 之间");
        }
    }

    public record Definition(CrawlerScheduleMode mode, Map<String, Object> config, String cronExpression) {
    }
}
