#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p .github/workflows
rm -f .github/workflows/*.yml .github/workflows/*.yaml
cp ci/android.yml .github/workflows/android.yml
chmod +x gradlew

grep -F 'name: Evolune Android CI v6' .github/workflows/android.yml >/dev/null
grep -F 'compose-bom:2025.08.00' app/build.gradle.kts >/dev/null
grep -F 'compileSdk = 36' app/build.gradle.kts >/dev/null

printf '\nInstalled Evolune CI v6.\n'
printf 'Workflow: .github/workflows/android.yml\n'
printf 'Compose BOM: 2025.08.00 (API-36-compatible baseline)\n'
