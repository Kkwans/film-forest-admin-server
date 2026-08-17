package com.filmforest.crawler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.crawler.dto.CrawlerAdapterDescriptor;
import com.filmforest.crawler.dto.CrawlerSourceDescriptor;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerSourceBinding;
import com.filmforest.crawler.mapper.CrawlerSourceBindingMapper;
import com.filmforest.crawler.source.SourceAdapterRegistry;
import com.filmforest.crawler.model.CrawlerSourceCapabilities;
import com.filmforest.common.type.ContentType;
import com.filmforest.resource.entity.ResourceSource;
import com.filmforest.resource.mapper.ResourceSourceMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CrawlerSourceCatalogService {

    private final ResourceSourceMapper sourceMapper;
    private final CrawlerSourceBindingMapper bindingMapper;
    private final SourceAdapterRegistry adapterRegistry;

    public CrawlerSourceCatalogService(ResourceSourceMapper sourceMapper,
                                       CrawlerSourceBindingMapper bindingMapper,
                                       SourceAdapterRegistry adapterRegistry) {
        this.sourceMapper = sourceMapper;
        this.bindingMapper = bindingMapper;
        this.adapterRegistry = adapterRegistry;
    }

    public List<CrawlerSourceDescriptor> listAvailableSources() {
        return sourceMapper.selectList(new LambdaQueryWrapper<ResourceSource>()
                        .eq(ResourceSource::getEnabled, 1)
                        .orderByAsc(ResourceSource::getSort)
                        .orderByAsc(ResourceSource::getId))
                .stream()
                .map(source -> {
                    List<CrawlerSourceBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<CrawlerSourceBinding>()
                                        .eq(CrawlerSourceBinding::getSourceId, source.getId())
                                        .eq(CrawlerSourceBinding::getEnabled, 1)
                                        .orderByAsc(CrawlerSourceBinding::getContentType));
                    Map<String, CrawlerSourceCapabilities> capabilities = new LinkedHashMap<>();
                    bindings.forEach(binding -> {
                        ContentType.fromValue(binding.getContentType()).ifPresent(type ->
                                capabilities.put(type.value(), adapterRegistry.require(binding.getAdapterCode())
                                        .capabilities(type)));
                    });
                    return new CrawlerSourceDescriptor(
                        source.getId(), source.getCode(), source.getName(), source.getUrl(),
                        bindings.stream()
                                .map(binding -> new CrawlerAdapterDescriptor(
                                        binding.getAdapterCode(), binding.getContentType()))
                                .toList(), capabilities);
                })
                .filter(source -> !source.adapters().isEmpty())
                .toList();
    }

    public void validateAndNormalize(CrawlerSchedule schedule) {
        if (schedule.getSourceId() == null) {
            throw new IllegalArgumentException("必须选择资源来源");
        }
        if (schedule.getAdapterCode() == null || schedule.getAdapterCode().isBlank()) {
            throw new IllegalArgumentException("必须选择来源适配器");
        }
        ResourceSource source = sourceMapper.selectById(schedule.getSourceId());
        if (source == null || !Integer.valueOf(1).equals(source.getEnabled())) {
            throw new IllegalArgumentException("资源来源不存在或未启用");
        }
        String adapterCode = adapterRegistry.require(schedule.getAdapterCode()).sourceCode();
        Long matches = bindingMapper.selectCount(new LambdaQueryWrapper<CrawlerSourceBinding>()
                .eq(CrawlerSourceBinding::getSourceId, source.getId())
                .eq(CrawlerSourceBinding::getAdapterCode, adapterCode)
                .eq(CrawlerSourceBinding::getContentType, schedule.getContentType())
                .eq(CrawlerSourceBinding::getEnabled, 1));
        if (matches == null || matches != 1L) {
            throw new IllegalArgumentException("所选来源不支持当前内容类型");
        }
        schedule.setAdapterCode(adapterCode);
        schedule.setSourceSite(adapterCode);
    }

    public CrawlerSourceCapabilities capabilities(String adapterCode, String contentType) {
        ContentType type = "short".equals(contentType)
                ? ContentType.SHORT_DRAMA
                : ContentType.fromValue(contentType)
                .orElseThrow(() -> new IllegalArgumentException("不支持的内容类型: " + contentType));
        return adapterRegistry.require(adapterCode).capabilities(type);
    }
}
