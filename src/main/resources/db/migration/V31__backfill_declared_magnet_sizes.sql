-- 七味网历史资源标题/原始文本已经声明了大小（例如 [3.21G]），但 V29 只新增了字段，
-- 没有回填旧数据，导致详情页全部显示“未知”。这里把来源明确声明的大小转换为字节。
-- 排除裸 K 单位，避免把分辨率“4K”误识别为 4KB；来源未声明大小的 magnet 保持 NULL。
UPDATE `resource_magnet`
SET `size_bytes` = ROUND(
    CAST(REPLACE(
        REGEXP_SUBSTR(
            REGEXP_REPLACE(
                REGEXP_SUBSTR(
                    COALESCE(NULLIF(`raw_text`, ''), `title`),
                    '(^|[^0-9A-Za-z])([0-9]+([.,][0-9]+)?)[[:space:]]*(TB|T|GB|G|MB|M|KB|B)(?=$|[^A-Za-z])',
                    1, 1, 'i'
                ),
                '^[^0-9]*', ''
            ),
            '[0-9]+([.,][0-9]+)?'
        ), ',', '.') AS DECIMAL(20, 3))
    * CASE UPPER(REGEXP_SUBSTR(
        REGEXP_REPLACE(
            REGEXP_SUBSTR(
                COALESCE(NULLIF(`raw_text`, ''), `title`),
                '(^|[^0-9A-Za-z])([0-9]+([.,][0-9]+)?)[[:space:]]*(TB|T|GB|G|MB|M|KB|B)(?=$|[^A-Za-z])',
                1, 1, 'i'
            ),
            '^[^0-9]*', ''
        ),
        '(TB|T|GB|G|MB|M|KB|B)'
    ))
        WHEN 'TB' THEN POW(1024, 4)
        WHEN 'T' THEN POW(1024, 4)
        WHEN 'GB' THEN POW(1024, 3)
        WHEN 'G' THEN POW(1024, 3)
        WHEN 'MB' THEN POW(1024, 2)
        WHEN 'M' THEN POW(1024, 2)
        WHEN 'KB' THEN POW(1024, 1)
        ELSE 1
    END
)
WHERE `size_bytes` IS NULL
  AND `is_deleted` = 0
  AND REGEXP_LIKE(
      COALESCE(NULLIF(`raw_text`, ''), `title`),
      '(^|[^0-9A-Za-z])([0-9]+([.,][0-9]+)?)[[:space:]]*(TB|T|GB|G|MB|M|KB|B)(?=$|[^A-Za-z])',
      'i'
  );

-- 回滚边界：本迁移只填充原本为空且来源文本可解析的 size_bytes，不删除任何资源或覆盖已有大小。
