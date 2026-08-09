package com.filmforest.content.model;

public enum ContentStatus {
    DRAFT(0),
    PUBLISHED(1),
    OFFLINE(2);

    private final int code;

    ContentStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static boolean isValid(int code) {
        return code == DRAFT.code || code == PUBLISHED.code || code == OFFLINE.code;
    }
}
