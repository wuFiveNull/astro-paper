#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
env_file="${ENV_FILE:-$script_dir/.env}"
compose_file="${COMPOSE_FILE:-$script_dir/compose.production.yaml}"
backup_file="${1:-}"

if [[ "${CONFIRM_RESTORE:-}" != "YES" ]]; then
  printf 'Refusing to restore without CONFIRM_RESTORE=YES. This replaces database contents.\n' >&2
  exit 2
fi
if [[ ! -f "$env_file" || ! -f "$compose_file" ]]; then
  printf 'Private environment or compose file is missing.\n' >&2
  exit 1
fi
if [[ -z "$backup_file" || ! -f "$backup_file" ]]; then
  printf 'Usage: CONFIRM_RESTORE=YES %s /absolute/path/backup.sql.gz\n' "$0" >&2
  exit 1
fi
case "$backup_file" in
  *.sql.gz) ;;
  *) printf 'Backup must be a .sql.gz file.\n' >&2; exit 1 ;;
esac

gzip -t -- "$backup_file"
gzip -dc -- "$backup_file" | docker compose --env-file "$env_file" -f "$compose_file" exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
printf 'Restore completed from: %s\n' "$backup_file"
