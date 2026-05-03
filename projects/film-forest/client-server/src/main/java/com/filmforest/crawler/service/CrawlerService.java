package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerSchedule;
import java.util.List;

public interface CrawlerService {

    List<CrawlerSchedule> listSchedules();

    CrawlerSchedule getSchedule(Long id);

    boolean saveSchedule(CrawlerSchedule schedule);

    boolean deleteSchedule(Long id);

    boolean startCrawler(Long id);

    boolean stopCrawler(Long id);

    boolean toggleEnabled(Long id, boolean enabled);

    Object getStatus();
}
