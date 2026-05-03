package com.filmforest.crawler.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 通用爬虫引擎：HTTP抓取 + 内容解析
 */
@Slf4j
@Component
public class CrawlerEngine {

    private final RestTemplate restTemplate;

    public CrawlerEngine() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 从目标URL抓取页面内容
     */
    public String fetchPage(String url, Map<String, String> headers) {
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            httpHeaders.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            httpHeaders.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            if (headers != null) {
                headers.forEach(httpHeaders::set);
            }
            HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("抓取页面失败: {}, error: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 通用GET请求
     */
    public String get(String url) {
        return fetchPage(url, null);
    }

    /**
     * 通用列表页抓取（翻页）
     * @param url 列表页URL
     * @param maxPages 最大页数
     * @param itemConsumer 每条数据的处理器
     * @param parser HTML解析器，传入整页HTML，返回对象列表
     */
    public <T> void crawlListPage(String url, int maxPages,
                                   Consumer<T> itemConsumer,
                                   Function<String, List<T>> parser) {
        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = url.contains("?") ? url + "&page=" + page : url + "?page=" + page;
            String html = fetchPage(pageUrl, null);
            if (html == null || html.isEmpty()) {
                log.warn("页面为空或抓取失败: {}", pageUrl);
                break;
            }
            try {
                List<T> items = parser.apply(html);
                if (items == null || items.isEmpty()) {
                    log.info("第{}页无数据，停止抓取", page);
                    break;
                }
                items.forEach(itemConsumer);
                log.info("第{}页抓取到{}条数据", page, items.size());
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("解析第{}页异常: {}", page, e.getMessage());
                break;
            }
        }
    }
}
