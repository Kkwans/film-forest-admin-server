package com.filmforest.common.util;

/**
 * 简介文本清理工具
 * 清理爬虫数据中残留的 UI 文本（如"[展开全部]"按钮文字）
 */
public final class StorylineCleaner {

    private StorylineCleaner() {}

    /** 需要清理的 UI 残留文本（带方括号） */
    private static final String[] BRACKET_PATTERNS = {
        "[展开全部]", "[收起部分]", "[收起简介]", "[展开简介]",
        "[收起全文]", "[展开全文]", "[查看更多]", "[展开]",
        "[收起]", "[更多]", "[]"
    };

    /** 需要清理的 UI 残留文本（不带方括号） */
    private static final String[] PLAIN_PATTERNS = {
        "展开全部", "收起部分", "收起简介", "展开简介",
        "收起全文", "展开全文", "查看更多", "展开更多",
        "点击展开", "收起"
    };

    /**
     * 清理简介文本中的 UI 残留内容
     * @param text 原始简介文本
     * @return 清理后的文本
     */
    public static String clean(String text) {
        if (text == null || text.isEmpty()) return text;

        String result = text;

        // 1. 清理带方括号的变体
        for (String pattern : BRACKET_PATTERNS) {
            result = result.replace(pattern, "");
        }

        // 2. 清理不带方括号的变体
        for (String pattern : PLAIN_PATTERNS) {
            result = result.replace(pattern, "");
        }

        // 3. 清理末尾残留的省略号 + 空白
        result = result.replaceAll("[…]+\\.?$", "").trim();
        result = result.replaceAll("\\.{3,}$", "").trim();

        // 4. 合并多余空白
        result = result.replaceAll("\\s{2,}", " ").trim();

        return result;
    }
}
