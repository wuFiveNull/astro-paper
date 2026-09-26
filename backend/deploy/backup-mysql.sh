#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
env_file="${ENV_FILE:-$script_dir/.env}"
compose_file="${COMPOSE_FILE:-$script_dir/compose.production.yaml}"
backup_dir="${BACKUP_DIR:-$script_dir/backups}"
retention_days="${BACKUP_RETENTION_DAYS:-14}"

if [[ ! -f "$env_file" ]]; then
  printf 'Missing private environment file: %s\n' "$env_file" >&2
  exit 1
fi
if [[ ! -f "$compose_file" ]]; then
  printf 'Missing compose file: %s\n' "$compose_file" >&2
  exit 1
fi
if ! [[ "$retention_days" =~ ^[0-9]+$ ]]; then
  printf 'BACKUP_RETENTION_DAYS must be a non-negative integer.\n' >&2
  exit 1
fi

mkdir -p -- "$backup_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
database="${MYSQL_DATABASE:-astro_paper}"
output="$backup_dir/${database}-${timestamp}.sql.gz"

docker compose --env-file "$env_file" -f "$compose_file" exec -T mysql \
  sh -c 'exec mysqldump --single-transaction --quick --routines --triggers --events -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip -c > "$output"

if [[ ! -s "$output" ]]; then
  rm -f -- "$output"
  printf 'Backup output is empty: %s\n' "$output" >&2
  exit 1
fi

sha256sum "$output" > "$output.sha256"
find "$backup_dir" -type f -name '*.sql.gz' -mtime "+$retention_days" -delete
find "$backup_dir" -type f -name '*.sql.gz.sha256' -mtime "+$retention_days" -delete
printf 'Backup created: %s\n' "$output"
