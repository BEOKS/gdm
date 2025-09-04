#!/usr/bin/env sh
set -euo pipefail

echo "Preparing GitLab Release..."

# Build auth header: prefer project/personal access token if provided
AUTH_HEADER=""
if [ -n "${GITLAB_TOKEN:-}" ]; then
  AUTH_HEADER="PRIVATE-TOKEN: ${GITLAB_TOKEN}"
  echo "Auth: using PRIVATE-TOKEN"
elif [ -n "${CI_JOB_TOKEN:-}" ]; then
  AUTH_HEADER="JOB-TOKEN: ${CI_JOB_TOKEN}"
  echo "Auth: using JOB-TOKEN"
else
  echo "No authentication token found. Set CI_JOB_TOKEN (provided by GitLab) or GITLAB_TOKEN with api scope." >&2
  exit 1
fi

# 1) Upload the JAR to project uploads to obtain a permanent asset URL
uploads_url="${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/uploads"
echo "Uploading asset to: ${uploads_url}"
upload_body_file="/tmp/upload_body.json"
upload_code=$(curl --silent --show-error \
  --header "${AUTH_HEADER}" \
  --form "file=@${JAR_PATH}" \
  --write-out "%{http_code}" \
  --output "${upload_body_file}" \
  "${uploads_url}" || true)

if [ "${upload_code}" != "201" ] && [ "${upload_code}" != "200" ]; then
  echo "Upload failed: HTTP ${upload_code}" >&2
  echo "Response body:" >&2
  head -c 500 "${upload_body_file}" >&2 || true
  echo >&2
  exit 1
fi

asset_rel_url=$(jq -r '.url' < "${upload_body_file}" 2>/dev/null || echo "")
if [ -z "${asset_rel_url}" ] || [ "${asset_rel_url}" = "null" ]; then
  echo "Failed to get asset url from upload response" >&2
  exit 1
fi

asset_url="${CI_PROJECT_URL}${asset_rel_url}"
echo "Asset URL: ${asset_url}"

# 2) Create a Release with the uploaded asset link
TAG_NAME=${TAG_NAME:-release-${CI_COMMIT_SHORT_SHA}}
release_body=$(cat <<JSON
{
  "name": "Release ${TAG_NAME}",
  "tag_name": "${TAG_NAME}",
  "ref": "${CI_COMMIT_SHA}",
  "description": "Automated release for ${CI_COMMIT_SHORT_SHA}",
  "assets": {
    "links": [
      {
        "name": "gabia-dev-mcp-server-1.0.0.jar",
        "url": "${asset_url}"
      }
    ]
  }
}
JSON
)

echo "Creating release with tag ${TAG_NAME}"
releases_url="${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/releases"
release_body_file="/tmp/release_body.json"
echo "${release_body}" > "${release_body_file}"
release_resp_file="/tmp/release_resp.json"
release_code=$(curl --silent --show-error \
  --header "${AUTH_HEADER}" \
  --header "Content-Type: application/json" \
  --data @"${release_body_file}" \
  --write-out "%{http_code}" \
  --output "${release_resp_file}" \
  "${releases_url}" || true)

if [ "${release_code}" != "201" ] && [ "${release_code}" != "200" ]; then
  echo "Release creation failed: HTTP ${release_code}" >&2
  echo "Response body:" >&2
  head -c 500 "${release_resp_file}" >&2 || true
  echo >&2
  exit 1
fi

echo "Release created successfully."
