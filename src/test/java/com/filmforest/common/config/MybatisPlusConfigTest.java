package com.filmforest.common.config;

import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigTest {

    @Test
    void installsPaginationInterceptorForAdminPageQueries() {
        var interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .singleElement()
                .isInstanceOf(PaginationInnerInterceptor.class);
    }
}
