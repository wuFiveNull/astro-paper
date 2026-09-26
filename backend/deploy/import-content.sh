#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
env_file="${ENV_FILE:-$script_dir/.env}"
compose_file="${COMPOSE_FILE:-$script_dir/compose.production.yaml}"
sql_file="${1:-$script_dir/content-import.sql}"

if [[ ! -f "$env_file" || ! -f "$compose_file" ]]; then
  printf 'Private environment or compose file is missing.\n' >&2
  exit 1
fi
if [[ ! -f "$sql_file" ]]; then
  printf 'Import SQL file is missing: %s\n' "$sql_file" >&2
  exit 1
fi

docker compose --env-file "$env_file" -f "$compose_file" exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  < "$sql_file"
printf 'Content import completed from: %s\n' "$sql_file"
