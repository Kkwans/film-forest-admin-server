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
    void insertDefaultsToEnabledAndTogglePersistsExplicitState() {
        ResourceMagnet resource = new ResourceMagnet();
        when(magnetMapper.insert(resource)).thenReturn(1);
        when(magnetMapper.updateById(any(ResourceMagnet.class))).thenReturn(1);

        service.saveMagnetResource(resource);
        assertThat(resource.getEnabled()).isEqualTo(1);
        assertThat(service.setMagnetEnabled(7L, false)).isTrue();

        ArgumentCaptor<ResourceMagnet> patch = ArgumentCaptor.forClass(ResourceMagnet.class);
        verify(magnetMapper).updateById(patch.capture());
        assertThat(patch.getValue().getId()).isEqualTo(7L);
        assertThat(patch.getValue().getEnabled()).isZero();
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
