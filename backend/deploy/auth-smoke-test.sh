#!/usr/bin/env bash
set -euo pipefail

deploy_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$deploy_dir"

project_name="${COMPOSE_PROJECT_NAME:-astro-paper-api-smoke-local}"
api_host_port="${API_HOST_PORT:-18082}"
base_url="http://127.0.0.1:${api_host_port}"

if [[ ! "$project_name" =~ ^astro-paper-api-smoke-[a-z0-9][a-z0-9-]*$ ]]; then
  printf 'COMPOSE_PROJECT_NAME must start with astro-paper-api-smoke- so cleanup stays isolated.\n' >&2
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
admin_password="${SMOKE_ADMIN_PASSWORD:-$(openssl rand -hex 24)}"
user_password="$(openssl rand -hex 24)"
editor_password="$(openssl rand -hex 24)"
admin_username="codex-smoke-admin"
admin_email="codex-smoke-admin@example.test"
user_username="codex-smoke-user"
user_email="codex-smoke-user@example.test"
editor_username="codex-smoke-editor"
editor_email="codex-smoke-editor@example.test"

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
editor_cookie_jar="${smoke_tmp_dir}/editor.cookies"
response_file="${smoke_tmp_dir}/response.json"
anonymous_cookie_jar="${smoke_tmp_dir}/anonymous.cookies"
touch "$admin_cookie_jar" "$user_cookie_jar" "$editor_cookie_jar" "$anonymous_cookie_jar"

cleanup() {
  "${compose[@]}" down --volumes >/dev/null 2>&1 || true
  rm -f -- "$admin_cookie_jar" "$user_cookie_jar" "$editor_cookie_jar" "$anonymous_cookie_jar" "$response_file" .env
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

# Verify the API blocks repeated password guesses without affecting other names.
mapfile -t anonymous_csrf < <(fetch_csrf "$anonymous_cookie_jar")
invalid_login_payload='{"username":"rate-limited-smoke-user","password":"InvalidSmokePassword123!"}'
for _ in $(seq 1 10); do
  request_json POST /api/v1/auth/login "$anonymous_cookie_jar" "${anonymous_csrf[0]}" "${anonymous_csrf[1]}" "$invalid_login_payload" 401
done
request_json POST /api/v1/auth/login "$anonymous_cookie_jar" "${anonymous_csrf[0]}" "${anonymous_csrf[1]}" "$invalid_login_payload" 429

# Remove bootstrap credentials from the API container after the first account is created.
sed -i '/^ADMIN_BOOTSTRAP_/d' .env
printf 'ADMIN_BOOTSTRAP_ENABLED=false\n' >> .env
"${compose[@]}" up --detach --force-recreate api >/dev/null
wait_for_api

printf "%s\n" "INSERT INTO posts (slug, title, description, content_markdown, status, author_id, published_at) SELECT 'smoke-test-post', 'Smoke test post', 'Temporary smoke test content', 'Body', 'PUBLISHED', id, CURRENT_TIMESTAMP(6) FROM users WHERE username = '${admin_username}';" \
  | "${compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names --user="$MYSQL_USER" "$MYSQL_DATABASE"'
public_comments="$(curl --fail --silent --show-error "${base_url}/api/v1/comments?postSlug=smoke-test-post")"
[[ "$public_comments" == *'"totalElements":0'* ]]

mapfile -t anonymous_csrf < <(fetch_csrf "$anonymous_cookie_jar")
anonymous_comment_payload="$(printf '{"postSlug":"%s","body":"Anonymous comment"}' "smoke-test-post")"
request_json POST /api/v1/comments "$anonymous_cookie_jar" "${anonymous_csrf[0]}" "${anonymous_csrf[1]}" "$anonymous_comment_payload" 401

mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
admin_login_payload="$(printf '{"username":"%s","password":"%s"}' "$admin_username" "$admin_password")"
request_json POST /api/v1/auth/login "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$admin_login_payload" 200
grep -q '"roles":\["ADMIN"\]' "$response_file"

admin_me="$(curl --fail --silent --show-error --cookie "$admin_cookie_jar" "${base_url}/api/v1/auth/me")"
[[ "$admin_me" == *'"username":"codex-smoke-admin"'* ]]
roles_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$admin_cookie_jar" "${base_url}/api/v1/admin/roles")"
[[ "$roles_status" == 200 ]]
admin_users="$(curl --fail --silent --show-error --cookie "$admin_cookie_jar" "${base_url}/api/v1/admin/users")"
[[ "$admin_users" == *'"username":"codex-smoke-admin"'* ]]

anonymous_admin_posts_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$anonymous_cookie_jar" "${base_url}/api/v1/admin/posts")"
[[ "$anonymous_admin_posts_status" == 401 ]]
admin_articles="$(curl --fail --silent --show-error --cookie "$admin_cookie_jar" "${base_url}/api/v1/admin/posts?status=PUBLISHED")"
[[ "$admin_articles" == *'"slug":"smoke-test-post"'* ]]
article_payload='{"slug":"cloud-managed-smoke-post","title":"Cloud managed article","description":"Temporary article management smoke test","contentMarkdown":"# Cloud Markdown body","timezone":"Asia/Shanghai","featured":true,"hideEditPost":false,"tags":["Cloud Smoke","Spring Boot"]}'
mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
request_json POST /api/v1/admin/posts "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$article_payload" 201
grep -q '"status":"DRAFT"' "$response_file"
article_id="$(sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p' "$response_file")"
[[ -n "$article_id" ]]
article_list="$(curl --fail --silent --show-error --cookie "$admin_cookie_jar" "${base_url}/api/v1/admin/posts?status=DRAFT")"
[[ "$article_list" == *'"slug":"cloud-managed-smoke-post"'* ]]
mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
request_json PUT "/api/v1/admin/posts/${article_id}/status" "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" '{"status":"PUBLISHED"}' 200
grep -q '"status":"PUBLISHED"' "$response_file"
public_article="$(curl --fail --silent --show-error "${base_url}/api/v1/posts/cloud-managed-smoke-post")"
[[ "$public_article" == *'"contentMarkdown":"# Cloud Markdown body"'* ]]
[[ "$public_article" == *'"tags":["Cloud Smoke","Spring Boot"]'* ]]

mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
user_payload="$(printf '{"username":"%s","email":"%s","password":"%s","displayName":"Cloud Smoke User"}' "$user_username" "$user_email" "$user_password")"
request_json POST /api/v1/admin/users "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$user_payload" 201
grep -q '"roles":\["USER"\]' "$response_file"
user_id="$(sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p' "$response_file")"
[[ -n "$user_id" ]]

mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
editor_payload="$(printf '{"username":"%s","email":"%s","password":"%s","displayName":"Cloud Smoke Editor"}' "$editor_username" "$editor_email" "$editor_password")"
request_json POST /api/v1/admin/users "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$editor_payload" 201
editor_id="$(sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p' "$response_file")"
[[ -n "$editor_id" ]]
mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
request_json PUT "/api/v1/admin/users/${editor_id}/roles" "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" '{"roleCodes":["EDITOR"]}' 200
grep -q '"roles":\["EDITOR"\]' "$response_file"

mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
editor_permissions_payload='{"permissionCodes":["post:read","post:create","post:update","post:publish","comment:moderate"]}'
request_json PUT /api/v1/admin/roles/EDITOR/permissions "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$editor_permissions_payload" 200

mapfile -t user_csrf < <(fetch_csrf "$user_cookie_jar")
user_login_payload="$(printf '{"username":"%s","password":"%s"}' "$user_username" "$user_password")"
request_json POST /api/v1/auth/login "$user_cookie_jar" "${user_csrf[0]}" "${user_csrf[1]}" "$user_login_payload" 200
grep -q '"roles":\["USER"\]' "$response_file"

mapfile -t user_csrf < <(fetch_csrf "$user_cookie_jar")
request_json POST /api/v1/admin/users "$user_cookie_jar" "${user_csrf[0]}" "${user_csrf[1]}" "$user_payload" 403
user_roles_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$user_cookie_jar" "${base_url}/api/v1/admin/roles")"
[[ "$user_roles_status" == 403 ]]
user_admin_users_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$user_cookie_jar" "${base_url}/api/v1/admin/users")"
[[ "$user_admin_users_status" == 403 ]]
user_admin_posts_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$user_cookie_jar" "${base_url}/api/v1/admin/posts")"
[[ "$user_admin_posts_status" == 403 ]]
user_comments_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$user_cookie_jar" "${base_url}/api/v1/admin/comments")"
[[ "$user_comments_status" == 403 ]]
user_messages_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$user_cookie_jar" "${base_url}/api/v1/admin/messages")"
[[ "$user_messages_status" == 403 ]]

mapfile -t user_csrf < <(fetch_csrf "$user_cookie_jar")
comment_payload="$(printf '{"postSlug":"%s","body":"Cloud smoke comment"}' "smoke-test-post")"
request_json POST /api/v1/comments "$user_cookie_jar" "${user_csrf[0]}" "${user_csrf[1]}" "$comment_payload" 201
grep -q '"status":"PENDING"' "$response_file"
comment_id="$(sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p' "$response_file")"
[[ -n "$comment_id" ]]
public_comments="$(curl --fail --silent --show-error "${base_url}/api/v1/comments?postSlug=smoke-test-post")"
[[ "$public_comments" == *'"totalElements":0'* ]]

admin_pending_comments="$(curl --fail --silent --show-error --cookie "$admin_cookie_jar" "${base_url}/api/v1/admin/comments?status=PENDING")"
[[ "$admin_pending_comments" == *'"username":"codex-smoke-user"'* ]]
mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
publish_comment_payload='{"status":"PUBLISHED"}'
request_json PUT "/api/v1/admin/comments/${comment_id}/status" "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$publish_comment_payload" 200
grep -q '"status":"PUBLISHED"' "$response_file"
public_comments="$(curl --fail --silent --show-error "${base_url}/api/v1/comments?postSlug=smoke-test-post")"
[[ "$public_comments" == *'"authorName":"Cloud Smoke User"'* ]]

mapfile -t user_csrf < <(fetch_csrf "$user_cookie_jar")
message_payload='{"subject":"Cloud smoke message","body":"Temporary message"}'
request_json POST /api/v1/messages "$user_cookie_jar" "${user_csrf[0]}" "${user_csrf[1]}" "$message_payload" 201
grep -q '"status":"NEW"' "$response_file"
message_id="$(sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p' "$response_file")"
[[ -n "$message_id" ]]
admin_new_messages="$(curl --fail --silent --show-error --cookie "$admin_cookie_jar" "${base_url}/api/v1/admin/messages?status=NEW")"
[[ "$admin_new_messages" == *'"senderEmail":"codex-smoke-user@example.test"'* ]]
mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
resolve_message_payload='{"status":"RESOLVED"}'
request_json PUT "/api/v1/admin/messages/${message_id}/status" "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$resolve_message_payload" 200
grep -q '"status":"RESOLVED"' "$response_file"

mapfile -t editor_csrf < <(fetch_csrf "$editor_cookie_jar")
editor_login_payload="$(printf '{"username":"%s","password":"%s"}' "$editor_username" "$editor_password")"
request_json POST /api/v1/auth/login "$editor_cookie_jar" "${editor_csrf[0]}" "${editor_csrf[1]}" "$editor_login_payload" 200
grep -q '"roles":\["EDITOR"\]' "$response_file"
mapfile -t editor_csrf < <(fetch_csrf "$editor_cookie_jar")
editor_article_payload='{"slug":"cloud-editor-smoke-post","title":"Cloud editor article","description":"Temporary owner scope test","contentMarkdown":"Editor Markdown","tags":["Cloud Smoke"]}'
request_json POST /api/v1/admin/posts "$editor_cookie_jar" "${editor_csrf[0]}" "${editor_csrf[1]}" "$editor_article_payload" 201
grep -q "\"authorId\":${editor_id}" "$response_file"
editor_article_id="$(sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p' "$response_file")"
[[ -n "$editor_article_id" ]]
editor_posts="$(curl --fail --silent --show-error --cookie "$editor_cookie_jar" "${base_url}/api/v1/admin/posts")"
[[ "$editor_posts" == *'"slug":"cloud-editor-smoke-post"'* ]]
[[ "$editor_posts" != *'"slug":"cloud-managed-smoke-post"'* ]]
editor_foreign_payload='{"slug":"cloud-managed-smoke-post","title":"Blocked edit","description":"Must be forbidden","contentMarkdown":"Nope"}'
request_json PUT "/api/v1/admin/posts/${article_id}" "$editor_cookie_jar" "${editor_csrf[0]}" "${editor_csrf[1]}" "$editor_foreign_payload" 403
admin_articles="$(curl --fail --silent --show-error --cookie "$admin_cookie_jar" "${base_url}/api/v1/admin/posts?size=100")"
[[ "$admin_articles" == *'"slug":"cloud-editor-smoke-post"'* ]]

mapfile -t admin_csrf < <(fetch_csrf "$admin_cookie_jar")
disable_payload='{"status":"DISABLED"}'
request_json PUT "/api/v1/admin/users/${user_id}/status" "$admin_cookie_jar" "${admin_csrf[0]}" "${admin_csrf[1]}" "$disable_payload" 200
grep -q '"status":"DISABLED"' "$response_file"

inactive_status="$(curl --silent --output "$response_file" --write-out '%{http_code}' --cookie "$user_cookie_jar" "${base_url}/api/v1/auth/me")"
[[ "$inactive_status" == 401 ]]

audit_count="$(printf '%s\n' "SELECT COUNT(*) FROM audit_logs WHERE action IN ('USER_CREATED','USER_ROLES_CHANGED','USER_STATUS_CHANGED','ROLE_PERMISSIONS_CHANGED');" | "${compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names --user="$MYSQL_USER" "$MYSQL_DATABASE"')"
[[ "$audit_count" == 5 ]]

unset db_password mysql_root_password admin_password user_password editor_password SMOKE_ADMIN_PASSWORD
printf 'Cloud API smoke test passed: MySQL health, authentication/RBAC, Markdown article management and publication, editor ownership boundaries, protected comments and moderation, protected guestbook submission and status update, and account disable.\n'
printf 'Cloud security checks passed: 10 invalid sign-in attempts are rate limited and account/role mutations create five audit records.\n'

if [[ "${SMOKE_HOLD_FOR_BROWSER:-false}" == true ]]; then
  printf 'Temporary browser test services are ready on 127.0.0.1:%s. Press Enter to stop and clean them up.\n' "$api_host_port"
  read -r _ </dev/tty
fi
