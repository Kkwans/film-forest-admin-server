package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerSourceBinding;
import com.filmforest.crawler.mapper.CrawlerSourceBindingMapper;
import com.filmforest.crawler.source.CrawlerSourceAdapter;
import com.filmforest.crawler.source.SourceAdapterRegistry;
import com.filmforest.resource.entity.ResourceSource;
import com.filmforest.resource.mapper.ResourceSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerSourceCatalogServiceTest {

    @Mock private ResourceSourceMapper sourceMapper;
    @Mock private CrawlerSourceBindingMapper bindingMapper;
    @Mock private SourceAdapterRegistry adapterRegistry;
    @Mock private CrawlerSourceAdapter adapter;

    private CrawlerSourceCatalogService service;

    @BeforeEach
    void setUp() {
        service = new CrawlerSourceCatalogService(sourceMapper, bindingMapper, adapterRegistry);
    }

    @Test
    void listsOnlyEnabledSourceBindings() {
        ResourceSource source = source(1L, 1);
        CrawlerSourceBinding movie = binding("movie");
        CrawlerSourceBinding drama = binding("drama");
        when(sourceMapper.selectList(any())).thenReturn(List.of(source));
        when(bindingMapper.selectList(any())).thenReturn(List.of(drama, movie));
        when(adapterRegistry.require("pkmp4")).thenReturn(adapter);
        when(adapter.capabilities(any())).thenAnswer(invocation -> new com.filmforest.crawler.model.CrawlerSourceCapabilities(
                "pkmp4", invocation.getArgument(0, com.filmforest.common.type.ContentType.class).value(),
                Set.of("TIME"), Set.of(), false, "CHALLENGE", "来源页面需要复核"));

        var result = service.listAvailableSources();

        assertThat(result).singleElement().satisfies(descriptor -> {
            assertThat(descriptor.id()).isEqualTo(1L);
            assertThat(descriptor.code()).isEqualTo("pkmp4");
            assertThat(descriptor.adapters()).extracting("contentType")
                    .containsExactly("drama", "movie");
        });
    }

    @Test
    void validatesBindingAndWritesCanonicalCompatibilitySource() {
        ResourceSource source = source(1L, 1);
        when(sourceMapper.selectById(1L)).thenReturn(source);
        when(adapterRegistry.require("七味网")).thenReturn(adapter);
        when(adapter.sourceCode()).thenReturn("pkmp4");
        when(bindingMapper.selectCount(any())).thenReturn(1L);
        CrawlerSchedule schedule = schedule();
        schedule.setAdapterCode("七味网");

        service.validateAndNormalize(schedule);

        assertThat(schedule.getAdapterCode()).isEqualTo("pkmp4");
        assertThat(schedule.getSourceSite()).isEqualTo("pkmp4");
    }

    @Test
    void rejectsDisabledOrUnboundSources() {
        CrawlerSchedule schedule = schedule();
        when(sourceMapper.selectById(1L)).thenReturn(source(1L, 0));
        assertThatThrownBy(() -> service.validateAndNormalize(schedule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未启用");

        when(sourceMapper.selectById(1L)).thenReturn(source(1L, 1));
        when(adapterRegistry.require("pkmp4")).thenReturn(adapter);
        when(adapter.sourceCode()).thenReturn("pkmp4");
        when(bindingMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.validateAndNormalize(schedule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持当前内容类型");
    }

    private static ResourceSource source(Long id, int enabled) {
        ResourceSource source = new ResourceSource();
        source.setId(id);
        source.setCode("pkmp4");
        source.setName("七味网");
        source.setUrl("https://www.pkmp4.xyz/");
        source.setEnabled(enabled);
        return source;
    }

    private static CrawlerSourceBinding binding(String contentType) {
        CrawlerSourceBinding binding = new CrawlerSourceBinding();
        binding.setSourceId(1L);
        binding.setAdapterCode("pkmp4");
        binding.setContentType(contentType);
        binding.setEnabled(1);
        return binding;
    }

    private static CrawlerSchedule schedule() {
        CrawlerSchedule schedule = new CrawlerSchedule();
        schedule.setSourceId(1L);
        schedule.setAdapterCode("pkmp4");
        schedule.setContentType("movie");
        return schedule;
    }
}
