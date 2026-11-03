#!/bin/bash
# Publishes a signed KijelzoPC release that every display installs at its next start.
#
#   tools/release.sh v1.2 ["What changed"]
#
# Takes the JARs that CI built on the raspberry (Pi) and main (Windows) branches, checks that
# they were built from the latest source, signs them with the activation private key and
# creates the GitHub release. Needs: gh (logged in), Java 11+, the private key on this Mac.
set -e
cd "$(dirname "$0")/.."

VERSION="$1"
NOTES="${2:-KijelzoPC $VERSION}"
if ! [[ "$VERSION" =~ ^v[0-9]+(\.[0-9]+){0,3}$ ]]; then
    echo "usage: tools/release.sh vX.Y [\"release notes\"]" >&2
    exit 2
fi
if gh release view "$VERSION" >/dev/null 2>&1; then
    echo "Release $VERSION already exists" >&2
    exit 1
fi
LATEST=$(gh release view --json tagName -q .tagName 2>/dev/null || true)
if [ -n "$LATEST" ] && [ "$(printf '%s\n%s\n' "${LATEST#v}" "${VERSION#v}" | sort -V | tail -1)" != "${VERSION#v}" ]; then
    echo "$VERSION is not newer than the latest release $LATEST; displays would ignore it" >&2
    exit 1
fi

DIST=$(mktemp -d)
trap 'rm -rf "$DIST"' EXIT
git fetch -q origin main raspberry

for pair in pi:raspberry windows:main; do
    platform=${pair%%:*}
    branch=${pair#*:}
    jar="$DIST/KijelzoPC-$platform.jar"
    git show "origin/$branch:out/KijelzoPC.jar" > "$jar"
    built=$(unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' | sed -n 's/^Implementation-Version: //p')
    source=$(git log -1 --format=%h "origin/$branch" -- src lib 'build-*.sh')
    # The build may come from a later commit (several commits pushed at once): it must contain $source
    if [ -z "$built" ] || [[ "$built" == *dirty* ]] || ! git merge-base --is-ancestor "$source" "$built" 2>/dev/null; then
        echo "The $branch JAR (built from ${built:-unknown}) is not the CI build of the latest source ($source)." >&2
        echo "Push your changes and wait for the 'Build JAR' workflow to finish, then try again." >&2
        exit 1
    fi
    java tools/KijelzoSign.java "$platform" "$VERSION" "$jar" > "$DIST/update-$platform.txt"
    echo "$platform: $branch @ $built, signed"
done

gh release create "$VERSION" --target main --title "KijelzoPC $VERSION" --notes "$NOTES" \
    "$DIST/KijelzoPC-pi.jar" "$DIST/KijelzoPC-windows.jar" "$DIST/update-pi.txt" "$DIST/update-windows.txt"
echo "Published $VERSION. Displays install it the next time KijelzoPC starts."
