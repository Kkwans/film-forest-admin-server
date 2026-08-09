#!/usr/bin/env bash

set -Eeuo pipefail

readonly ARCHIVE_ROOT="/volume2/Project/film-forest/deploy/archives/phase8"
readonly PRODUCTION_DATABASE="film_forest"
readonly CLEAR_CONFIRMATION="clear-film-forest-phase8-archived-data"
readonly RESTORE_CONFIRMATION="restore-film-forest-phase8-isolated-copy"

readonly -a CLEAR_SCOPE_TABLES=(
  crawler_schedule_genre
  crawler_job_item_failure
  crawler_task_log
  crawler_source_item
  crawler_content_identity
  poster_enrichment_job
  content_poster_match
  content_tag
  user_movie_list_item
  resource_online
  resource_magnet
  resource_cloud
  episode_backup
  movie
  drama
  variety
  anime
  short_drama
  crawler_schedule
)

readonly -a PRESERVED_TABLES=(
  user
  user_movie_list
  content_type
  system_setting
  tag
  tag_content_type
  tag_source_alias
  resource_source
  crawler_source_adapter
  user_poster_setting
)

usage() {
  printf '%s\n' \
    '用法：scripts/phase8-data-lifecycle.sh safety-backup|backup|verify|restore-drill|clear|verify-cleared' \
    '' \
    '生产连接（容器模式）：' \
    '  FILM_FOREST_MYSQL_CONTAINER  MySQL 容器名' \
    '  FILM_FOREST_DB_USERNAME      默认 root' \
    '  FILM_FOREST_DB_PASSWORD      优先使用；仅通过 docker exec 环境传递' \
    '  FILM_FOREST_CONTAINER_PASSWORD_ENV  未提供上项时回退，默认 MYSQL_ROOT_PASSWORD' \
    '' \
    'safety-backup（迁移前原始库安全快照）/ backup（Phase 8 清空前正式归档）：' \
    '  FILM_FOREST_ARCHIVE_DIR      必须位于 deploy/archives/phase8 下' \
    '' \
    'restore-drill：' \
    '  FILM_FOREST_ARCHIVE_DIR' \
    '  FILM_FOREST_DRILL_MYSQL_CONTAINER  名称必须以 film-forest-phase8-restore- 开头' \
    '  FILM_FOREST_DRILL_CONFIRM=restore-film-forest-phase8-isolated-copy' \
    '' \
    'clear：' \
    '  FILM_FOREST_ARCHIVE_DIR（必须含 READY 与 RESTORE_VERIFIED）' \
    '  FILM_FOREST_CLEAR_CONFIRM=clear-film-forest-phase8-archived-data'
}

die() {
  printf '错误：%s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"
}

require_archive_dir() {
  local requested="${FILM_FOREST_ARCHIVE_DIR:-}"
  [[ -n "$requested" ]] || die '必须设置 FILM_FOREST_ARCHIVE_DIR'
  [[ "$requested" != *'/../'* && "$requested" != */.. ]] || die '归档目录不能包含上级路径'
  require_command realpath
  local canonical
  canonical=$(realpath -m -- "$requested")
  [[ "$canonical" = "$ARCHIVE_ROOT"/* ]] || die "归档目录必须位于 $ARCHIVE_ROOT 下"
  ARCHIVE_DIR="$canonical"
}

validate_identifier() {
  [[ "$1" =~ ^[a-zA-Z0-9_-]+$ ]] || die "非法标识符：$1"
}

production_container() {
  local container="${FILM_FOREST_MYSQL_CONTAINER:-}"
  [[ -n "$container" ]] || die '必须设置 FILM_FOREST_MYSQL_CONTAINER'
  validate_identifier "$container"
  PRODUCTION_CONTAINER="$container"
}

mysql_in_container() {
  local container="$1"
  local database="$2"
  shift 2
  local username="${FILM_FOREST_DB_USERNAME:-root}"
  local password_env="${FILM_FOREST_CONTAINER_PASSWORD_ENV:-MYSQL_ROOT_PASSWORD}"
  validate_identifier "$container"
  validate_identifier "$database"
  validate_identifier "$username"
  validate_identifier "$password_env"
  PHASE8_MYSQL_PASSWORD="${FILM_FOREST_DB_PASSWORD:-}" \
  docker exec -i -e PHASE8_MYSQL_PASSWORD "$container" sh -c '
    password_value=$(printenv PHASE8_MYSQL_PASSWORD)
    [ -n "$password_value" ] || password_value=$(printenv "$1")
    [ -n "$password_value" ] || { echo "容器密码环境变量为空" >&2; exit 64; }
    export MYSQL_PWD="$password_value"
    shift
    exec mysql --batch --skip-column-names "$@"
  ' phase8-mysql "$password_env" --user="$username" "$database" "$@"
}

mysql_admin_in_container() {
  local container="$1"
  shift
  local username="${FILM_FOREST_DB_USERNAME:-root}"
  local password_env="${FILM_FOREST_CONTAINER_PASSWORD_ENV:-MYSQL_ROOT_PASSWORD}"
  validate_identifier "$container"
  validate_identifier "$username"
  validate_identifier "$password_env"
  PHASE8_MYSQL_PASSWORD="${FILM_FOREST_DB_PASSWORD:-}" \
  docker exec -i -e PHASE8_MYSQL_PASSWORD "$container" sh -c '
    password_value=$(printenv PHASE8_MYSQL_PASSWORD)
    [ -n "$password_value" ] || password_value=$(printenv "$1")
    [ -n "$password_value" ] || { echo "容器密码环境变量为空" >&2; exit 64; }
    export MYSQL_PWD="$password_value"
    shift
    exec mysql --batch --skip-column-names "$@"
  ' phase8-mysql-admin "$password_env" --user="$username" "$@"
}

mysqldump_in_container() {
  local container="$1"
  local database="$2"
  local username="${FILM_FOREST_DB_USERNAME:-root}"
  local password_env="${FILM_FOREST_CONTAINER_PASSWORD_ENV:-MYSQL_ROOT_PASSWORD}"
  validate_identifier "$container"
  validate_identifier "$database"
  validate_identifier "$username"
  validate_identifier "$password_env"
  PHASE8_MYSQL_PASSWORD="${FILM_FOREST_DB_PASSWORD:-}" \
  docker exec -i -e PHASE8_MYSQL_PASSWORD "$container" sh -c '
    password_value=$(printenv PHASE8_MYSQL_PASSWORD)
    [ -n "$password_value" ] || password_value=$(printenv "$1")
    [ -n "$password_value" ] || { echo "容器密码环境变量为空" >&2; exit 64; }
    export MYSQL_PWD="$password_value"
    shift
    exec mysqldump "$@"
  ' phase8-dump "$password_env" --user="$username" \
    --single-transaction --quick --no-tablespaces --skip-triggers --hex-blob \
    --set-gtid-purged=OFF --default-character-set=utf8mb4 "$database"
}

count_tables() {
  local container="$1"
  local database="$2"
  local output="$3"
  shift 3
  local sql=''
  local table
  for table in "$@"; do
    validate_identifier "$table"
    sql+="SELECT '$table', COUNT(*) FROM \`$table\`;"
  done
  mysql_in_container "$container" "$database" --execute="$sql" > "$output"
}

count_all_tables() {
  local container="$1"
  local database="$2"
  local output="$3"
  local table_output
  local -a tables=()
  table_output=$(mysql_in_container "$container" "$database" --execute="
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
    ORDER BY table_name;
  ")
  [[ -n "$table_output" ]] || die '目标数据库没有可归档的数据表'
  mapfile -t tables <<< "$table_output"
  count_tables "$container" "$database" "$output" "${tables[@]}"
}

assert_required_tables() {
  local container="$1"
  local database="$2"
  local available
  available=$(mysql_in_container "$container" "$database" --execute="
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';
  ")
  local table
  local -a missing=()
  for table in "${CLEAR_SCOPE_TABLES[@]}" "${PRESERVED_TABLES[@]}"; do
    if ! grep -Fxq -- "$table" <<< "$available"; then
      missing+=("$table")
    fi
  done
  [[ "${#missing[@]}" -eq 0 ]] \
    || die "数据库结构尚未满足 Phase 8 归档要求，缺少表：${missing[*]}"
}

verify_archive() {
  local archive_dir="$1"
  [[ -f "$archive_dir/READY" ]] || die '归档尚未写入 READY 标记'
  [[ -f "$archive_dir/SHA256SUMS" ]] || die '归档缺少 SHA256SUMS'
  (
    cd "$archive_dir"
    sha256sum --check --strict SHA256SUMS
  )
}

archive_format() {
  local archive_dir="$1"
  awk -F '=' '$1 == "format" { print $2; exit }' "$archive_dir/manifest.txt"
}

safety_backup() {
  require_command docker
  require_command gzip
  require_command sha256sum
  production_container
  local container="$PRODUCTION_CONTAINER"
  require_archive_dir
  local archive_dir="$ARCHIVE_DIR"
  [[ ! -e "$archive_dir" ]] || die '归档目录已存在，拒绝覆盖'
  mkdir -p "$archive_dir"

  local schema_version
  schema_version=$(mysql_in_container "$container" "$PRODUCTION_DATABASE" --execute="
    SELECT COALESCE(
      (SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1),
      'unknown'
    );
  ")
  count_all_tables "$container" "$PRODUCTION_DATABASE" "$archive_dir/all-table-counts.tsv"
  mysqldump_in_container "$container" "$PRODUCTION_DATABASE" \
    | gzip -9 > "$archive_dir/film_forest.sql.gz"
  {
    printf 'format=film-forest-pre-migration-archive-v1\n'
    printf 'created_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'database=%s\n' "$PRODUCTION_DATABASE"
    printf 'flyway_schema_version=%s\n' "$schema_version"
    printf 'source_container=%s\n' "$container"
  } > "$archive_dir/manifest.txt"
  (
    cd "$archive_dir"
    sha256sum film_forest.sql.gz all-table-counts.tsv manifest.txt > SHA256SUMS
    sha256sum --check --strict SHA256SUMS
  )
  date -u +%Y-%m-%dT%H:%M:%SZ > "$archive_dir/READY"
  printf '迁移前安全快照已完成：%s\n' "$archive_dir"
}

backup() {
  require_command docker
  require_command gzip
  require_command grep
  require_command sha256sum
  production_container
  local container="$PRODUCTION_CONTAINER"
  require_archive_dir
  local archive_dir="$ARCHIVE_DIR"
  [[ ! -e "$archive_dir" ]] || die '归档目录已存在，拒绝覆盖'
  assert_required_tables "$container" "$PRODUCTION_DATABASE"
  mkdir -p "$archive_dir"

  local schema_version
  schema_version=$(mysql_in_container "$container" "$PRODUCTION_DATABASE" --execute="
    SELECT COALESCE(
      (SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1),
      'unknown'
    );
  ")

  count_tables "$container" "$PRODUCTION_DATABASE" \
    "$archive_dir/clear-scope-counts.tsv" "${CLEAR_SCOPE_TABLES[@]}"
  count_tables "$container" "$PRODUCTION_DATABASE" \
    "$archive_dir/preserved-counts.tsv" "${PRESERVED_TABLES[@]}"
  count_all_tables "$container" "$PRODUCTION_DATABASE" "$archive_dir/all-table-counts.tsv"

  mysqldump_in_container "$container" "$PRODUCTION_DATABASE" \
    | gzip -9 > "$archive_dir/film_forest.sql.gz"

  {
    printf 'format=film-forest-phase8-archive-v1\n'
    printf 'created_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'database=%s\n' "$PRODUCTION_DATABASE"
    printf 'flyway_schema_version=%s\n' "$schema_version"
    printf 'source_container=%s\n' "$container"
    printf 'clear_scope_table_count=%s\n' "${#CLEAR_SCOPE_TABLES[@]}"
    printf 'preserved_table_count=%s\n' "${#PRESERVED_TABLES[@]}"
  } > "$archive_dir/manifest.txt"

  (
    cd "$archive_dir"
    sha256sum film_forest.sql.gz clear-scope-counts.tsv preserved-counts.tsv \
      all-table-counts.tsv manifest.txt > SHA256SUMS
    sha256sum --check --strict SHA256SUMS
  )
  date -u +%Y-%m-%dT%H:%M:%SZ > "$archive_dir/READY"
  printf '归档已完成：%s\n' "$archive_dir"
}

restore_drill() {
  require_command awk
  require_command docker
  require_command diff
  require_command gzip
  require_command grep
  require_command sha256sum
  require_archive_dir
  local archive_dir="$ARCHIVE_DIR"
  verify_archive "$archive_dir"
  [[ "${FILM_FOREST_DRILL_CONFIRM:-}" = "$RESTORE_CONFIRMATION" ]] \
    || die '缺少隔离恢复确认短语'
  production_container
  local source_container="$PRODUCTION_CONTAINER"
  local target_container="${FILM_FOREST_DRILL_MYSQL_CONTAINER:-}"
  [[ "$target_container" = film-forest-phase8-restore-* ]] \
    || die '恢复目标容器名必须以 film-forest-phase8-restore- 开头'
  [[ "$target_container" != "$source_container" ]] || die '恢复目标不能是生产 MySQL 容器'
  validate_identifier "$target_container"
  local -r restore_database='film_forest_phase8_restore'

  mysql_admin_in_container "$target_container" --execute="
    DROP DATABASE IF EXISTS \`$restore_database\`;
    CREATE DATABASE \`$restore_database\`
      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  "
  gzip --decompress --stdout "$archive_dir/film_forest.sql.gz" \
    | mysql_in_container "$target_container" "$restore_database"

  count_all_tables "$target_container" "$restore_database" \
    "$archive_dir/restore-all-table-counts.tsv"
  diff -u "$archive_dir/all-table-counts.tsv" "$archive_dir/restore-all-table-counts.tsv"
  local format
  format=$(archive_format "$archive_dir")
  if [[ "$format" = 'film-forest-phase8-archive-v1' ]]; then
    assert_required_tables "$target_container" "$restore_database"
    count_tables "$target_container" "$restore_database" \
      "$archive_dir/restore-clear-scope-counts.tsv" "${CLEAR_SCOPE_TABLES[@]}"
    count_tables "$target_container" "$restore_database" \
      "$archive_dir/restore-preserved-counts.tsv" "${PRESERVED_TABLES[@]}"
    diff -u "$archive_dir/clear-scope-counts.tsv" "$archive_dir/restore-clear-scope-counts.tsv"
    diff -u "$archive_dir/preserved-counts.tsv" "$archive_dir/restore-preserved-counts.tsv"
    (
      cd "$archive_dir"
      sha256sum restore-clear-scope-counts.tsv restore-preserved-counts.tsv \
        restore-all-table-counts.tsv > RESTORE_SHA256SUMS
      sha256sum --check --strict RESTORE_SHA256SUMS
    )
  elif [[ "$format" = 'film-forest-pre-migration-archive-v1' ]]; then
    (
      cd "$archive_dir"
      sha256sum restore-all-table-counts.tsv > RESTORE_SHA256SUMS
      sha256sum --check --strict RESTORE_SHA256SUMS
    )
  else
    die "不支持的归档格式：$format"
  fi
  date -u +%Y-%m-%dT%H:%M:%SZ > "$archive_dir/RESTORE_VERIFIED"
  printf '隔离恢复核对通过：%s\n' "$target_container"
}

require_restored_formal_archive() {
  require_command awk
  require_command docker
  require_command diff
  require_command grep
  require_command sha256sum
  [[ "${FILM_FOREST_CLEAR_CONFIRM:-}" = "$CLEAR_CONFIRMATION" ]] \
    || die '缺少清空确认短语'
  require_archive_dir
  local archive_dir="$ARCHIVE_DIR"
  verify_archive "$archive_dir"
  [[ "$(archive_format "$archive_dir")" = 'film-forest-phase8-archive-v1' ]] \
    || die '清空只接受 Phase 8 正式归档，拒绝使用迁移前安全快照'
  [[ -f "$archive_dir/RESTORE_VERIFIED" ]] || die '隔离恢复演练尚未通过，禁止清空'
  [[ -f "$archive_dir/RESTORE_SHA256SUMS" ]] || die '隔离恢复演练缺少校验清单，禁止清空'
  (
    cd "$archive_dir"
    sha256sum --check --strict RESTORE_SHA256SUMS
  )
}

verify_cleared_state() {
  require_restored_formal_archive
  local archive_dir="$ARCHIVE_DIR"
  production_container
  local container="$PRODUCTION_CONTAINER"
  assert_required_tables "$container" "$PRODUCTION_DATABASE"

  count_tables "$container" "$PRODUCTION_DATABASE" \
    "$archive_dir/post-clear-counts.tsv" "${CLEAR_SCOPE_TABLES[@]}"
  if awk -F '\t' '$2 != 0 { exit 1 }' "$archive_dir/post-clear-counts.tsv"; then
    :
  else
    die '清空后仍存在非零业务表'
  fi
  count_tables "$container" "$PRODUCTION_DATABASE" \
    "$archive_dir/post-clear-preserved-counts.tsv" "${PRESERVED_TABLES[@]}"
  diff -u "$archive_dir/preserved-counts.tsv" "$archive_dir/post-clear-preserved-counts.tsv" \
    || die '清空过程中需保留数据发生变化'
  local clear_names="${CLEAR_SCOPE_TABLES[*]}"
  awk -F '\t' -v OFS='\t' -v clear_names="$clear_names" '
    BEGIN {
      count = split(clear_names, names, " ")
      for (i = 1; i <= count; i++) {
        cleared[names[i]] = 1
      }
    }
    $1 in cleared { $2 = 0 }
    { print }
  ' "$archive_dir/all-table-counts.tsv" > "$archive_dir/expected-post-clear-counts.tsv"
  count_all_tables "$container" "$PRODUCTION_DATABASE" \
    "$archive_dir/post-clear-all-table-counts.tsv"
  diff -u "$archive_dir/expected-post-clear-counts.tsv" \
    "$archive_dir/post-clear-all-table-counts.tsv" \
    || die '清空过程中非目标表发生变化'
  date -u +%Y-%m-%dT%H:%M:%SZ > "$archive_dir/CLEARED"
  printf '清空后全表核对通过；归档保留于：%s\n' "$archive_dir"
}

clear_archived_data() {
  require_restored_formal_archive
  local archive_dir="$ARCHIVE_DIR"
  production_container
  local container="$PRODUCTION_CONTAINER"
  assert_required_tables "$container" "$PRODUCTION_DATABASE"

  local runtime_counts
  runtime_counts=$(mktemp)
  local preserved_before
  preserved_before=$(mktemp)
  local runtime_all_counts
  runtime_all_counts=$(mktemp)
  PHASE8_RUNTIME_COUNTS="$runtime_counts"
  PHASE8_PRESERVED_BEFORE="$preserved_before"
  PHASE8_RUNTIME_ALL_COUNTS="$runtime_all_counts"
  trap 'rm -f -- "${PHASE8_RUNTIME_COUNTS:-}" "${PHASE8_PRESERVED_BEFORE:-}" "${PHASE8_RUNTIME_ALL_COUNTS:-}"' EXIT
  count_tables "$container" "$PRODUCTION_DATABASE" "$runtime_counts" "${CLEAR_SCOPE_TABLES[@]}"
  diff -u "$archive_dir/clear-scope-counts.tsv" "$runtime_counts" \
    || die '归档后数据已变化，禁止按旧归档清空'
  count_tables "$container" "$PRODUCTION_DATABASE" "$preserved_before" "${PRESERVED_TABLES[@]}"
  diff -u "$archive_dir/preserved-counts.tsv" "$preserved_before" \
    || die '需保留数据已变化，禁止清空'
  count_all_tables "$container" "$PRODUCTION_DATABASE" "$runtime_all_counts"
  diff -u "$archive_dir/all-table-counts.tsv" "$runtime_all_counts" \
    || die '归档后数据库存在未纳入清空子集的变化，禁止清空'

  local active_jobs
  active_jobs=$(mysql_in_container "$container" "$PRODUCTION_DATABASE" --execute="
    SELECT COUNT(*) FROM crawler_task_log
    WHERE status IN ('queued', 'running', 'cancel_requested');
  ")
  [[ "$active_jobs" = '0' ]] || die "仍有 $active_jobs 个活动爬虫 Job"
  local enabled_schedules
  enabled_schedules=$(mysql_in_container "$container" "$PRODUCTION_DATABASE" \
    --execute='SELECT COUNT(*) FROM crawler_schedule WHERE enabled = 1;')
  [[ "$enabled_schedules" = '0' ]] || die "仍有 $enabled_schedules 个启用计划"

  local delete_sql='START TRANSACTION;'
  local table
  for table in "${CLEAR_SCOPE_TABLES[@]}"; do
    delete_sql+="DELETE FROM \`$table\`;"
  done
  delete_sql+='COMMIT;'
  mysql_in_container "$container" "$PRODUCTION_DATABASE" --execute="$delete_sql"

  rm -f -- "$runtime_counts" "$preserved_before" "$runtime_all_counts"
  trap - EXIT
  verify_cleared_state
  printf '已按确认清单清空归档业务数据；归档保留于：%s\n' "$archive_dir"
}

case "${1:-}" in
  safety-backup) safety_backup ;;
  backup) backup ;;
  verify)
    require_command sha256sum
    require_archive_dir
    verify_archive "$ARCHIVE_DIR"
    ;;
  restore-drill) restore_drill ;;
  clear) clear_archived_data ;;
  verify-cleared) verify_cleared_state ;;
  *) usage; exit 2 ;;
esac
