package com.filmforest.content.entity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagMappingContractTest {

    @BeforeAll
    static void initializeTableInfo() {
        if (TableInfoHelper.getTableInfo(Tag.class) != null) return;
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "tag-mapping-contract-test");
        assistant.setCurrentNamespace("tag-mapping-contract-test.Tag");
        TableInfoHelper.initTableInfo(assistant, Tag.class);
    }

    @Test
    void generatedSelectDoesNotUseReservedSystemAlias() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(Tag.class);

        assertThat(tableInfo.getAllSqlSelect())
                .contains("is_system AS system_flag")
                .doesNotContain("is_system AS system,");
    }

    @Test
    void jsonContractStillExposesSystemWithoutInternalFlag() throws Exception {
        Tag tag = new Tag();
        tag.setSystem(1);

        String json = new ObjectMapper().writeValueAsString(tag);

        assertThat(json).contains("\"system\":1").doesNotContain("systemFlag");
    }
}
