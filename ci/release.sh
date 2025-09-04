#!/usr/bin/env sh
set -euo pipefail

echo "Preparing GitLab Release..."

# 1) Upload the JAR to project uploads to obtain a permanent asset URL
upload_json=$(curl --fail --silent --show-error \
  --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
  --form "file=@${JAR_PATH}" \
  "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/uploads")

echo "Upload response: ${upload_json}"
asset_rel_url=$(echo "${upload_json}" | jq -r '.url')
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
curl --fail --silent --show-error \
  --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
  --header "Content-Type: application/json" \
  --data "${release_body}" \
  "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/releases"

echo "Release created successfully."

