package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.*;
import com.filmforest.content.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 全局搜索接口
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private MovieService movieService;

    @Autowired
    private DramaService dramaService;

    @Autowired
    private VarietyService varietyService;

    @Autowired
    private AnimeService animeService;

    @Autowired
    private ShortDramaService shortDramaService;

    /**
     * 搜索接口（合并电影/剧集/综艺/动漫/短剧）
     */
    @GetMapping
    public Result<?> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.fail("关键词不能为空");
        }
        
        String kw = keyword.trim();
        int from = (page - 1) * size;
        
        // 分散查询各表，取并集后分页
        List<SearchResult> allResults = new ArrayList<>();
        
        // 电影
        List<SearchResult> movies = movieService.list(
                new LambdaQueryWrapper<Movie>()
                        .like(Movie::getTitle, kw)
                        .or().like(Movie::getAlias, kw)
                        .or().like(Movie::getActor, kw)
                        .or().like(Movie::getDirector, kw)
        ).stream().limit(50).map(m -> new SearchResult(
                m.getId(), "movie", m.getTitle(),
                m.getPosterUrl(), m.getYear(),
                m.getScoreDouban() != null ? m.getScoreDouban().doubleValue() : null,
                m.getStoryline()
        )).collect(Collectors.toList());
        allResults.addAll(movies);
        
        // 剧集
        List<SearchResult> dramas = dramaService.list(
                new LambdaQueryWrapper<Drama>()
                        .like(Drama::getTitle, kw)
                        .or().like(Drama::getAlias, kw)
                        .or().like(Drama::getActor, kw)
        ).stream().limit(50).map(d -> new SearchResult(
                d.getId(), "drama", d.getTitle(),
                d.getPosterUrl(), d.getYear(),
                d.getScoreDouban() != null ? d.getScoreDouban().doubleValue() : null,
                d.getStoryline()
        )).collect(Collectors.toList());
        allResults.addAll(dramas);
        
        // 综艺
        List<SearchResult> varieties = varietyService.list(
                new LambdaQueryWrapper<Variety>()
                        .like(Variety::getTitle, kw)
                        .or().like(Variety::getAlias, kw)
        ).stream().limit(50).map(v -> new SearchResult(
                v.getId(), "variety", v.getTitle(),
                v.getPosterUrl(), v.getYear(),
                v.getScoreDouban() != null ? v.getScoreDouban().doubleValue() : null,
                v.getStoryline()
        )).collect(Collectors.toList());
        allResults.addAll(varieties);
        
        // 动漫
        List<SearchResult> animes = animeService.list(
                new LambdaQueryWrapper<Anime>()
                        .like(Anime::getTitle, kw)
                        .or().like(Anime::getAlias, kw)
                        .or().like(Anime::getActor, kw)
        ).stream().limit(50).map(a -> new SearchResult(
                a.getId(), "anime", a.getTitle(),
                a.getPosterUrl(), a.getYear(),
                a.getScoreDouban() != null ? a.getScoreDouban().doubleValue() : null,
                a.getStoryline()
        )).collect(Collectors.toList());
        allResults.addAll(animes);
        
        // 短剧
        List<SearchResult> shortDramas = shortDramaService.list(
                new LambdaQueryWrapper<ShortDrama>()
                        .like(ShortDrama::getTitle, kw)
                        .or().like(ShortDrama::getAlias, kw)
        ).stream().limit(50).map(s -> new SearchResult(
                s.getId(), "short_drama", s.getTitle(),
                s.getPosterUrl(), s.getYear(),
                null,
                s.getStoryline()
        )).collect(Collectors.toList());
        allResults.addAll(shortDramas);
        
        // 简单分页
        int total = allResults.size();
        List<SearchResult> pageData = allResults.stream()
                .skip(from)
                .limit(size)
                .collect(Collectors.toList());
        
        // 封装分页结果
        PageWrap<SearchResult> pageWrap = new PageWrap<>(pageData, total, size);
        return Result.ok(pageWrap);
    }

    // 内部类：搜索结果
    public record SearchResult(
            Long id,
            String type,      // movie / drama / variety / anime / short_drama
            String title,
            String cover,
            Integer year,
            Double rating,
            String summary
    ) {}

    // 内部类：分页包装
    public record PageWrap<T>(List<T> records, long total, long size) {
        public long getPages() { return (total + size - 1) / size; }
    }
}
