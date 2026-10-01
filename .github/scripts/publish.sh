#!/usr/bin/env bash
# Upload build outputs to a GitHub release.
#   tag push (v*)  -> release named after the tag
#   anything else  -> the rolling 'nightly' pre-release
# Usage: publish.sh file [file...]     (needs GH_TOKEN; run inside the checkout)
set -euo pipefail
[ "$#" -gt 0 ] || { echo "no files given" >&2; exit 2; }

if [[ "${GITHUB_REF_TYPE:-}" == "tag" ]]; then
  TAG="$GITHUB_REF_NAME"
  gh release view "$TAG" >/dev/null 2>&1 || gh release create "$TAG" --title "ObsiLauncher $TAG" --generate-notes
else
  TAG="nightly"
  gh release view "$TAG" >/dev/null 2>&1 || gh release create "$TAG" --prerelease --title "Nightly builds" \
    --notes "Rolling development builds (Android / Windows / Linux). Replaced on every successful workflow run."
fi
gh release upload "$TAG" "$@" --clobber
echo "Uploaded to release '$TAG':"; printf '  %s\n' "$@"
