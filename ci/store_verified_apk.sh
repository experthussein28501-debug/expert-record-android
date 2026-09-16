#!/usr/bin/env bash
set -euo pipefail

source_sha=$(git rev-parse HEAD)
branch=${KHABIR_PUBLISH_BRANCH:?publish branch is required}
review=release-output/integration-review-0.9.0
apk="$review/verified-optimized.apk"
test -s "$apk"
payload=$(mktemp -d)
publish_tree=$(mktemp -d)
rmdir "$publish_tree"
trap 'git worktree remove --force "$publish_tree" 2>/dev/null || true; rm -rf "$payload"' EXIT

cp "$apk" "$payload/khabir-integrated-0.9.0-optimized.apk"
cp release-output/INTEGRATED_APK_ANALYSIS_0.9.0.txt "$payload/"
cp -a "$review" "$payload/integration-review-0.9.0"
rm "$payload/integration-review-0.9.0/verified-optimized.apk"
printf 'source_sha=%s\noptimized_bytes=%s\n' "$source_sha" "$(stat -c%s "$apk")" > "$payload/INTEGRATED_SIZE_0.9.0.txt"
(
  cd "$payload"
  sha256sum khabir-integrated-0.9.0-optimized.apk > SHA256SUMS-0.9.0
  mkdir -p integrated-0.9.0-transfer
  base64 -w 0 khabir-integrated-0.9.0-optimized.apk | split -b 614400 -d -a 3 - integrated-0.9.0-transfer/part-
)

# Publish only tested sources. Output-only commits may safely advance the branch.
# A real source change is never overwritten or labelled as tested by this job.
for attempt in 1 2 3; do
  git fetch origin "$branch"
  target_sha=$(git rev-parse FETCH_HEAD)
  if ! git diff --quiet "$source_sha" "$target_sha" -- . ':!release-output' ':!build-output'; then
    echo "::notice::Source changed during verification; the newer build owns publication."
    exit 0
  fi
  git worktree add --detach "$publish_tree" "$target_sha"
  mkdir -p "$publish_tree/release-output"
  rm -rf "$publish_tree/release-output/integrated-0.9.0-transfer"
  cp -a "$payload/." "$publish_tree/release-output/"
  git -C "$publish_tree" config user.name 'github-actions[bot]'
  git -C "$publish_tree" config user.email '41898282+github-actions[bot]@users.noreply.github.com'
  git -C "$publish_tree" add release-output
  if git -C "$publish_tree" diff --cached --quiet; then
    echo 'Verified APK already published.'
    exit 0
  fi
  git -C "$publish_tree" commit -m 'Store tested integrated APK and size evidence [skip ci]'
  if git -C "$publish_tree" push origin "HEAD:$branch"; then
    exit 0
  fi
  git worktree remove --force "$publish_tree"
done
echo '::error::Unable to publish after three concurrent updates.'
exit 1
