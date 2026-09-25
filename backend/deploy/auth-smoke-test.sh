#!/usr/bin/env bash
set -euo pipefail

deploy_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$deploy_dir"

project_name="${COMPOSE_PROJECT_NAME:-astro-paper-api-auth-smoke-local}"
api_host_port="${API_HOST_PORT:-18082}"
base_url="http://127.0.0.1:${api_host_port}"

if [[ ! "$project_name" =~ ^astro-paper-api-auth-smoke-[a-z0-9][a-z0-9-]*$ ]]; then
  printf 'COMPOSE_PROJECT_NAME must start with astro-paper-api-auth-smoke- so cleanup stays isolated.\n' >&2
  exit 1
fi

if [[ -e .env ]]; then
  printf 'Refusing to overwrite existing %s/.env\n' "$deploy_dir" >&2
  exit 1
fi
if [[ ! -f astro-paper-api.jar ]]; then
  printf 'Copy the locally built astro-paper-api.jar into %s first.\n' "$deploy_dir" >&2
  exit 1
fi

umask 077
db_password="$(openssl rand -hex 32)"
mysql_root_password="$(openssl rand -hex 32)"
admin_password="$(openssl rand -hex 24)"
user_password="$(openssl rand -hex 24)"
admin_username="codex-smoke-admin"
admin_email="codex-smoke-admin@example.test"
user_username="codex-smoke-user"
user_email="codex-smoke-user@example.test"

cat > .env <<EOF
API_HOST_PORT=${api_host_port}
WEB_HOST_PORT=18083
MYSQL_DATABASE=astro_paper_auth
DB_USERNAME=astro_paper_app
DB_PASSWORD=${db_password}
MYSQL_ROOT_PASSWORD=${mysql_root_password}
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_BOOTSTRAP_USERNAME=${admin_username}
ADMIN_BOOTSTRAP_EMAIL=${admin_email}
ADMIN_BOOTSTRAP_DISPLAY_NAME=Cloud Smoke Admin
ADMIN_BOOTSTRAP_PASSWORD=${admin_password}
SESSION_COOKIE_SECURE=false
EOF
chmod 600 .env

compose=(docker compose --project-name "$project_name" -f compose.test.yaml -f compose.auth-smoke.yaml)
smoke_tmp_dir="$(mktemp -d)"
admin_cookie_jar="${smoke_tmp_dir}/admin.cookies"
user_cookie_jar="${smoke_tmp_dir}/user.cookies"
response_file="${smoke_tmp_dir}/response.json"
touch "$admin_cookie_jar" "$user_cookie_jar"

cleanup() {
  "${compose[@]}" down --volumes >/dev/null 2>&1 || true
  rm -f -- "$admin_cookie_jar" "$user_cookie_jar" "$response_file" .env
  rmdir -- "$smoke_tmp_dir" 2>/dev/null || true
}
trap cleanup EXIT

fetch_csrf() {
  local cookie_jar="$1"
  local body header token
  body="$(curl --fail --silent --show-error --cookie "$cookie_jar" --cookie-jar "$cookie_jar" "${base_url}/api/v1/auth/csrf")"
  header="$(printf '%s' "$body" | sed -n 's/.*"headerName":"\([^"]*\)".*/\1/p')"
  token="$(printf '%s' "$body" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
  if [[ -z "$header" || -z "$token" ]]; then
    printf 'The CSRF endpoint did not return a usable token.\n' >&2
    return 1
  fi
  printf '%s\n%s\n' "$header" "$token"
}

request_json() {
  local method="$1" path="$2" cookie_jar="$3" header="$4" token="$5" payload="$6" expected="$7"
  local status
  status="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' \
    --cookie "$cookie_jar" --cookie-jar "$cookie_jar" \
    --header "${header}: ${token}" --header 'Content-Type: application/json' \
    --request "$method" --data "$payload" "${base_url}${path}")"
  if [[ "$status" != "$expected" ]]; then
    printf 'Expected HTTP %s from %s %s, received %s.\n' "$expected" "$method" "$path" "$status" >&2
    cat "$response_file" >&2
    return 1
  fi
}

wait_for_api() {
  local health
  for _ in $(seq 1 120); do
    health="$(curl --silent --max-time 2 "${base_url}/actuator/health" 2>/dev/null || true)"
    if [[ "$health" == *'"status":"UP"'* ]]; then return 0; fi
    sleep 1
  done
  "${compose[@]}" logs --tail=80 api >&2 || true
  printf 'API health did not reach UP.\n' >&2
  return 1
}

"${compose[@]}" up --detach mysql api
wait_for_api
curl --fail --silent --show-error "${base_url}/api/v1/health" >/dev/null

# Remove bootstrap credentials from the API container after the first account is created.
sed -i '/^ADMIN_BOOTSTRAP_/d' .env
printf 'ADMIN_BOOTSTRAP_ENABLED=false\n' >> .env
"${compose[@]}" up --detach --force-recreate api >/dev/null
wait_for_api

mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
admin_login_payload="$(printf '{"username":"%s","password":"%s"}' "$admin_username" "$admin_password")"
request_json POST /api/v1/auth/login "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$admin_login_payload" 200
grep -q '"roles":\["ADMIN"\]' "$response_file"

admin_me="$(curl --fail --silent --show-error --cookie "$admin_cookie_jar" "${base_url}/api/v1/auth/me")"
[[ "$admin_me" == *'"username":"codex-smoke-admin"'* ]]
roles_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$admin_cookie_jar" "${base_url}/api/v1/admin/roles")"
[[ "$roles_status" == 200 ]]

mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
user_payload="$(printf '{"username":"%s","email":"%s","password":"%s","displayName":"Cloud Smoke User"}' "$user_username" "$user_email" "$user_password")"
request_json POST /api/v1/admin/users "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$user_payload" 201
grep -q '"roles":\["USER"\]' "$response_file"
user_id="$(sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p' "$response_file")"
[[ -n "$user_id" ]]

mapfile -t user_csrf < <(fetch_csrf "$user_cookie_jar")
user_login_payload="$(printf '{"username":"%s","password":"%s"}' "$user_username" "$user_password")"
request_json POST /api/v1/auth/login "$user_cookie_jar" "${user_csrf[0]}" "${user_csrf[1]}" "$user_login_payload" 200
grep -q '"roles":\["USER"\]' "$response_file"

mapfile -t user_csrf < <(fetch_csrf "$user_cookie_jar")
request_json POST /api/v1/admin/users "$user_cookie_jar" "${user_csrf[0]}" "${user_csrf[1]}" "$user_payload" 403
user_roles_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$user_cookie_jar" "${base_url}/api/v1/admin/roles")"
[[ "$user_roles_status" == 403 ]]

mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
disable_payload='{"status":"DISABLED"}'
request_json PUT "/api/v1/admin/users/${user_id}/status" "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$disable_payload" 200
grep -q '"status":"DISABLED"' "$response_file"

inactive_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$user_cookie_jar" "${base_url}/api/v1/auth/me")"
[[ "$inactive_status" == 401 ]]

unset db_password mysql_root_password admin_password user_password
printf 'Cloud authentication smoke test passed: MySQL health, admin bootstrap/login, standard account creation/login, authorization denial, and account disable.\n'
