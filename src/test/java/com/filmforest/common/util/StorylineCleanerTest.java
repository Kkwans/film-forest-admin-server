package com.filmforest.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorylineCleanerTest {

    @Test
    void keepsLongestExpandedCopyAndRemovesUiControls() {
        assertThat(StorylineCleaner.clean("短简介…展开全部短简介，随后展开完整故事。收起"))
                .isEqualTo("短简介，随后展开完整故事。");
    }

    @Test
    void doesNotDeleteNaturalUseOfCollapseWord() {
        assertThat(StorylineCleaner.clean("她收起行李，踏上新的旅程……"))
                .isEqualTo("她收起行李，踏上新的旅程……");
    }

    @Test
    void removesBracketedControlsWithoutJoiningDuplicateCopies() {
        assertThat(StorylineCleaner.clean("同一段简介[展开全部]同一段简介[收起]"))
                .isEqualTo("同一段简介");
    }
}
