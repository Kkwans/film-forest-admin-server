package com.filmforest.resource.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.resource.dto.ResourcePageQuery;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.entity.ResourceSource;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import com.filmforest.resource.mapper.ResourceSourceMapper;
import com.filmforest.resource.service.impl.ResourceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceImplTest {

    @Mock private ResourceOnlineMapper onlineMapper;
    @Mock private ResourceMagnetMapper magnetMapper;
    @Mock private ResourceCloudMapper cloudMapper;
    @Mock private ResourceSourceMapper sourceMapper;

    private ResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableInfo(ResourceOnline.class);
        initializeTableInfo(ResourceMagnet.class);
        initializeTableInfo(ResourceCloud.class);
        service = new ResourceServiceImpl(onlineMapper, magnetMapper, cloudMapper, sourceMapper);
    }

    @Test
    void magnetPageUsesCanonicalTypeAndAllOperationalFilters() {
        ResourcePageQuery query = new ResourcePageQuery();
        query.setPage(2);
        query.setSize(25);
        query.setContentType("short");
        query.setSource("pkmp4");
        query.setStatus("active");
        query.setResolution("4K");
        query.setKeyword("森林");
        query.setSort("title");
        query.setOrder("asc");
        when(magnetMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.pageMagnet(query);

        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(25);
        ArgumentCaptor<Wrapper<ResourceMagnet>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(magnetMapper).selectPage(any(Page.class), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains(
                "content_type", "source_code", "enabled", "removed_at", "resolution", "title", "ASC");
        assertThat(parameters(wrapper.getValue()))
                .contains("short_drama", "pkmp4", 1, "4K", "%森林%");
    }

    @Test
    void cloudPageSupportsDiskTypeAndRemovedStatus() {
        ResourcePageQuery query = new ResourcePageQuery();
        query.setDiskType("quark");
        query.setStatus("REMOVED");
        when(cloudMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.pageCloud(query);

        ArgumentCaptor<Wrapper<ResourceCloud>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(cloudMapper).selectPage(any(Page.class), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("disk_type", "removed_at", "IS NOT NULL");
        assertThat(parameters(wrapper.getValue())).contains("quark");
    }

    @Test
    void publicOnlineLookupReturnsOnlyActiveResourcesAndNormalizesShortDrama() {
        when(onlineMapper.selectList(any())).thenReturn(List.of());

        service.listOnlineResources("short", 9L);

        ArgumentCaptor<Wrapper<ResourceOnline>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(onlineMapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("content_type", "content_id", "enabled", "removed_at");
        assertThat(parameters(wrapper.getValue())).contains("short_drama", 9L, 1);
    }

    @Test
    void insertDefaultsToEnabledAndRestoreClearsRemovedMarker() {
        ResourceMagnet resource = new ResourceMagnet();
        when(magnetMapper.insert(resource)).thenReturn(1);
        when(magnetMapper.update(any(), any())).thenReturn(1);

        service.saveMagnetResource(resource);
        assertThat(resource.getEnabled()).isEqualTo(1);
        assertThat(service.setMagnetEnabled(7L, true)).isTrue();

        ArgumentCaptor<Wrapper<ResourceMagnet>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(magnetMapper).update(any(), update.capture());
        assertThat(update.getValue().getSqlSegment()).contains("id");
        assertThat(((com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ResourceMagnet>) update.getValue()).getSqlSet())
                .contains("enabled", "removed_at");
        assertThat(parameters(update.getValue())).contains(7L, 1, null);
    }

    @Test
    void adminOnlineUpdatePersistsPlaybackContractWithoutOverwritingCrawlerMetadata() {
        ResourceOnline stored = new ResourceOnline();
        stored.setId(41L);
        stored.setSourceCode("pkmp4");
        stored.setSourcePageUrl("https://old.example.test/page");
        stored.setPlaybackType("EXTERNAL_PAGE");
        when(onlineMapper.selectById(41L)).thenReturn(stored);
        when(onlineMapper.update(any(), any())).thenReturn(1);

        ResourceOnline update = new ResourceOnline();
        update.setId(41L);
        update.setContentType("short");
        update.setContentId(9L);
        update.setSourceUrl(" https://example.test/play ");
        update.setSourceName(" 七味线路 ");
        update.setSourcePageUrl(" https://example.test/source-page ");
        update.setPlaybackType("hls");
        update.setEpisodeTitle("");
        update.setEnabled(1);
        update.setRawText("不得覆盖");
        update.setResourceKey("不得覆盖");
        update.setRemovedAt(java.time.LocalDateTime.now());

        service.saveOnlineResource(update);

        ArgumentCaptor<Wrapper<ResourceOnline>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(onlineMapper).update(any(), wrapper.capture());
        var sqlSet = ((com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ResourceOnline>) wrapper.getValue())
                .getSqlSet();
        assertThat(sqlSet)
                .contains("content_type", "source_code", "episode_title", "source_name", "source_url",
                        "source_page_url", "playback_type")
                .doesNotContain("raw_text", "resource_key", "removed_at", "last_seen_at");
        assertThat(parameters(wrapper.getValue())).contains("short_drama", "pkmp4", null, "七味线路",
                "https://example.test/play", "https://example.test/source-page", "HLS");
        assertThat(update.getPlaybackType()).isEqualTo("HLS");
    }

    @Test
    void adminOnlineSaveRejectsUnsafeUrlAndUnknownPlaybackType() {
        ResourceOnline unsafe = new ResourceOnline();
        unsafe.setContentType("movie");
        unsafe.setContentId(1L);
        unsafe.setSourceName("危险来源");
        unsafe.setSourceUrl("javascript:alert(1)");

        assertThatThrownBy(() -> service.saveOnlineResource(unsafe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP/HTTPS");

        ResourceOnline unknownType = new ResourceOnline();
        unknownType.setContentType("movie");
        unknownType.setContentId(1L);
        unknownType.setSourceName("测试来源");
        unknownType.setSourceUrl("https://example.test/play");
        unknownType.setPlaybackType("MAGIC");

        assertThatThrownBy(() -> service.saveOnlineResource(unknownType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("播放类型");
    }

    @Test
    void adminMagnetAndCloudUpdatesCanClearOptionalFields() {
        ResourceMagnet storedMagnet = new ResourceMagnet();
        storedMagnet.setId(51L);
        storedMagnet.setSourceCode("legacy");
        when(magnetMapper.selectById(51L)).thenReturn(storedMagnet);
        when(magnetMapper.update(any(), any())).thenReturn(1);

        ResourceMagnet magnet = new ResourceMagnet();
        magnet.setId(51L);
        magnet.setContentType("movie");
        magnet.setContentId(1L);
        magnet.setMagnetUrl(" magnet:?xt=urn:btih:abc ");
        magnet.setTitle("");
        magnet.setResolution(" ");
        service.saveMagnetResource(magnet);

        ArgumentCaptor<Wrapper<ResourceMagnet>> magnetUpdate = ArgumentCaptor.forClass(Wrapper.class);
        verify(magnetMapper).update(any(), magnetUpdate.capture());
        assertThat(((com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ResourceMagnet>) magnetUpdate.getValue())
                .getSqlSet()).contains("title", "resolution").doesNotContain("raw_text", "resource_key");
        assertThat(parameters(magnetUpdate.getValue())).contains("legacy", null, "magnet:?xt=urn:btih:abc");

        ResourceCloud storedCloud = new ResourceCloud();
        storedCloud.setId(61L);
        storedCloud.setSourceCode("legacy");
        when(cloudMapper.selectById(61L)).thenReturn(storedCloud);
        when(cloudMapper.update(any(), any())).thenReturn(1);

        ResourceCloud cloud = new ResourceCloud();
        cloud.setId(61L);
        cloud.setContentType("movie");
        cloud.setContentId(1L);
        cloud.setUrl(" https://example.test/share ");
        cloud.setPassword("");
        cloud.setTitle(" ");
        service.saveCloudResource(cloud);

        ArgumentCaptor<Wrapper<ResourceCloud>> cloudUpdate = ArgumentCaptor.forClass(Wrapper.class);
        verify(cloudMapper).update(any(), cloudUpdate.capture());
        assertThat(((com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ResourceCloud>) cloudUpdate.getValue())
                .getSqlSet()).contains("title", "password").doesNotContain("raw_text", "resource_key");
        assertThat(parameters(cloudUpdate.getValue())).contains("legacy", null, "https://example.test/share");
    }

    @Test
    void sourceCodeIsNormalizedOnCreateAndImmutableAfterwards() {
        ResourceSource created = new ResourceSource();
        created.setCode(" Source-X ");
        created.setName("扩展来源");
        created.setUrl("https://example.test");
        when(sourceMapper.insert(created)).thenReturn(1);

        service.saveSource(created);

        assertThat(created.getCode()).isEqualTo("source-x");
        assertThat(created.getEnabled()).isZero();
        assertThat(created.getSort()).isZero();

        ResourceSource stored = new ResourceSource();
        stored.setId(3L);
        stored.setCode("source-x");
        when(sourceMapper.selectById(3L)).thenReturn(stored);
        ResourceSource renamed = new ResourceSource();
        renamed.setId(3L);
        renamed.setCode("other-code");
        renamed.setName("扩展来源");
        renamed.setUrl("https://example.test");

        assertThatThrownBy(() -> service.saveSource(renamed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可修改");
    }

    @Test
    void productionOrReferencedSourcesCannotBeDeleted() {
        ResourceSource production = new ResourceSource();
        production.setId(1L);
        production.setCode("pkmp4");
        when(sourceMapper.selectById(1L)).thenReturn(production);

        assertThatThrownBy(() -> service.deleteSource(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("生产来源不可删除");

        ResourceSource referenced = new ResourceSource();
        referenced.setId(2L);
        referenced.setCode("source-x");
        when(sourceMapper.selectById(2L)).thenReturn(referenced);
        when(sourceMapper.deleteById(2L)).thenThrow(new DataIntegrityViolationException("fk"));

        assertThatThrownBy(() -> service.deleteSource(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已被爬虫适配器、计划或资源引用");
    }

    @Test
    void rejectsUnknownFiltersAndUnboundedPageSizesBeforeSql() {
        ResourcePageQuery query = new ResourcePageQuery();
        query.setStatus("BROKEN");
        assertThatThrownBy(() -> service.pageCloud(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTIVE");

        query.setStatus(null);
        query.setContentType("movie;drop");
        assertThatThrownBy(() -> service.pageCloud(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内容类型");

        query.setContentType(null);
        query.setSize(101);
        assertThatThrownBy(() -> service.pageCloud(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 到 100");
    }

    private static java.util.Collection<Object> parameters(Wrapper<?> wrapper) {
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values();
    }

    private static void initializeTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) != null) return;
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "resource-service-test");
        assistant.setCurrentNamespace("resource-service-test." + entityType.getSimpleName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
