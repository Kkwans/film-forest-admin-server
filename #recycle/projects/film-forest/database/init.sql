-- =====================================================
-- 影视森林 (film-forest) 数据库建表脚本 v2
-- 基于七味网(pkmp4.xyz)调研后重新设计
-- MySQL 8.0
-- 修订：2026-04-25
-- =====================================================

CREATE DATABASE IF NOT EXISTS film_forest DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE film_forest;

-- -------------------------------------------------------
-- 字典表：内容类型（仅供参考，不做外键约束）
-- -------------------------------------------------------
CREATE TABLE content_type (
    id      INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(20) NOT NULL COMMENT '类型名称',
    code    VARCHAR(20) NOT NULL UNIQUE COMMENT '类型代码',
    sort    INT NOT NULL DEFAULT 0 COMMENT '排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容类型字典';

INSERT INTO content_type (name, code, sort) VALUES
('电影',   'movie',   1),
('剧集',   'drama',   2),
('综艺',   'variety', 3),
('动漫',   'anime',   4),
('短剧',   'short',   5);

-- -------------------------------------------------------
-- 1. 电影表 (movie)
-- -------------------------------------------------------
CREATE TABLE movie (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL COMMENT '中文名/标题',
    alias           JSON COMMENT '别名数组',
    poster_url      VARCHAR(500) COMMENT '封面URL',
    year            INT COMMENT '年份',
    director        JSON COMMENT '导演数组',
    writer          JSON COMMENT '编剧数组',
    actor           JSON COMMENT '主演数组',
    genre           JSON COMMENT '类型数组（有顺序）',
    region          JSON COMMENT '地区数组',
    language        JSON COMMENT '语言数组',
    release_date    VARCHAR(100) COMMENT '上映日期',
    duration        INT COMMENT '片长（分钟）',
    storyline       TEXT COMMENT '简介',
    score_douban    DECIMAL(3,1) COMMENT '豆瓣评分',
    score_imdb      DECIMAL(3,1) COMMENT 'IMDB评分',
    series_name     VARCHAR(200) COMMENT '系列名称（复仇者联盟等）',
    series_order    INT COMMENT '系列序号（第几部）',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-可播放',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_year (year),
    INDEX idx_title (title(100)),
    INDEX idx_series (series_name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电影表';

-- -------------------------------------------------------
-- 2. 电视剧表 (drama)
-- -------------------------------------------------------
CREATE TABLE drama (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL COMMENT '中文名/标题',
    alias           JSON COMMENT '别名数组',
    poster_url      VARCHAR(500) COMMENT '封面URL',
    year            INT COMMENT '年份',
    director        JSON COMMENT '导演数组',
    writer          JSON COMMENT '编剧数组',
    actor           JSON COMMENT '主演数组',
    genre           JSON COMMENT '类型数组',
    region          JSON COMMENT '地区数组',
    language        JSON COMMENT '语言数组',
    release_date    VARCHAR(100) COMMENT '首播日期',
    total_episode   INT COMMENT '总集数',
    storyline       TEXT COMMENT '简介',
    score_douban    DECIMAL(3,1) COMMENT '豆瓣评分',
    score_imdb      DECIMAL(3,1) COMMENT 'IMDB评分',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-更新中 2-已完结',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_year (year),
    INDEX idx_title (title(100)),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电视剧表';

-- -------------------------------------------------------
-- 3. 综艺表 (variety)
-- -------------------------------------------------------
CREATE TABLE variety (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL COMMENT '中文名/标题',
    alias           JSON COMMENT '别名数组',
    poster_url      VARCHAR(500) COMMENT '封面URL',
    year            INT COMMENT '年份',
    director        JSON COMMENT '导演/制作人数组',
    actor           JSON COMMENT '嘉宾/主持数组',
    genre           JSON COMMENT '类型数组',
    region          JSON COMMENT '地区数组',
    language        JSON COMMENT '语言数组',
    release_date    VARCHAR(100) COMMENT '首播日期',
    total_episode   INT COMMENT '总期数',
    storyline       TEXT COMMENT '简介',
    score_douban    DECIMAL(3,1) COMMENT '豆瓣评分',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_year (year),
    INDEX idx_title (title(100)),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综艺表';

-- -------------------------------------------------------
-- 4. 动漫表 (anime)
-- -------------------------------------------------------
CREATE TABLE anime (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL COMMENT '中文名/标题',
    alias           JSON COMMENT '别名数组',
    poster_url      VARCHAR(500) COMMENT '封面URL',
    year            INT COMMENT '年份',
    director        JSON COMMENT '导演数组',
    writer          JSON COMMENT '编剧数组',
    actor           JSON COMMENT '配音演员数组',
    genre           JSON COMMENT '类型数组',
    region          JSON COMMENT '地区数组',
    language        JSON COMMENT '语言数组',
    release_date    VARCHAR(100) COMMENT '首播日期',
    total_episode   INT COMMENT '总集数',
    storyline       TEXT COMMENT '简介',
    score_douban    DECIMAL(3,1) COMMENT '豆瓣评分',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_year (year),
    INDEX idx_title (title(100)),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动漫表';

-- -------------------------------------------------------
-- 5. 短剧表 (short_drama)
-- -------------------------------------------------------
CREATE TABLE short_drama (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL COMMENT '中文名/标题',
    alias           JSON COMMENT '别名数组',
    poster_url      VARCHAR(500) COMMENT '封面URL',
    year            INT COMMENT '年份',
    director        JSON COMMENT '导演数组',
    actor           JSON COMMENT '主演数组',
    genre           JSON COMMENT '类型数组',
    region          JSON COMMENT '地区数组',
    language        JSON COMMENT '语言数组',
    release_date    VARCHAR(100) COMMENT '上映日期',
    total_episode   INT COMMENT '总集数',
    duration        INT COMMENT '单集片长（分钟）',
    storyline       TEXT COMMENT '简介',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_year (year),
    INDEX idx_title (title(100)),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧表';

-- -------------------------------------------------------
-- 6. 剧集子表 (episode) — 通用剧集表
-- content_type 区分来源：drama/anime/variety/short
-- -------------------------------------------------------
CREATE TABLE episode (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    content_type    VARCHAR(20) NOT NULL COMMENT '内容类型',
    content_id      BIGINT UNSIGNED NOT NULL COMMENT '关联主表ID',
    season          INT NOT NULL DEFAULT 1 COMMENT '季',
    episode_number  INT NOT NULL COMMENT '集号/期号',
    title           VARCHAR(200) COMMENT '集标题（可选）',
    poster_url      VARCHAR(500) COMMENT '集封面（可选）',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_source_ep (content_type, content_id, season, episode_number),
    INDEX idx_content (content_type, content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧集子表';

-- -------------------------------------------------------
-- 7. 在线播放资源表 (resource_online) ⭐新增
-- -------------------------------------------------------
CREATE TABLE resource_online (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    content_type    VARCHAR(20) NOT NULL COMMENT '内容类型',
    content_id      BIGINT UNSIGNED NOT NULL COMMENT '关联主表ID',
    episode_id      BIGINT UNSIGNED COMMENT '关联剧集（可空）',
    source_name     VARCHAR(50) NOT NULL COMMENT '播放源名称',
    source_url     TEXT NOT NULL COMMENT '第三方播放链接',
    sort            INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_content (content_type, content_id),
    INDEX idx_episode (episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线播放资源表';

-- -------------------------------------------------------
-- 8. 磁力链接表 (resource_magnet)
-- -------------------------------------------------------
CREATE TABLE resource_magnet (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    content_type    VARCHAR(20) NOT NULL COMMENT '内容类型',
    content_id      BIGINT UNSIGNED NOT NULL COMMENT '关联主表ID',
    episode_id      BIGINT UNSIGNED COMMENT '关联剧集（可空）',
    title           VARCHAR(200) COMMENT '显示标题',
    magnet_url      TEXT NOT NULL COMMENT '磁力链接',
    resolution      VARCHAR(20) COMMENT '清晰度：4K/1080P/720P/480P',
    has_subtitle    TINYINT NOT NULL DEFAULT 0 COMMENT '是否有中字：0-无 1-有',
    is_special_sub  TINYINT NOT NULL DEFAULT 0 COMMENT '是否特效字幕：0-否 1-是（如有"特效"关键词）',
    sort            INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_content (content_type, content_id),
    INDEX idx_episode (episode_id),
    INDEX idx_resolution (resolution),
    INDEX idx_subtitle (has_subtitle),
    INDEX idx_special_sub (is_special_sub)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='磁力链接表';

-- -------------------------------------------------------
-- 9. 网盘链接表 (resource_cloud)
-- -------------------------------------------------------
CREATE TABLE resource_cloud (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    content_type    VARCHAR(20) NOT NULL COMMENT '内容类型',
    content_id      BIGINT UNSIGNED NOT NULL COMMENT '关联主表ID',
    episode_id      BIGINT UNSIGNED COMMENT '关联剧集（可空）',
    disk_type       VARCHAR(20) NOT NULL COMMENT '网盘类型：baidu/ali/xunlei/uc',
    title           VARCHAR(200) COMMENT '显示标题',
    url             TEXT NOT NULL COMMENT '网盘链接',
    password        VARCHAR(50) COMMENT '提取码',
    sort            INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_content (content_type, content_id),
    INDEX idx_episode (episode_id),
    INDEX idx_disk_type (disk_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网盘链接表';
