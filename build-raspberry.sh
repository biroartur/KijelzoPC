#!/bin/bash
# Builds out/KijelzoPC.jar as a single self-contained JAR (app + JAX-WS runtime)
# that runs on Java 8 through 21+. Requires a JDK 9+ (for --release 8).
set -e
cd "$(dirname "$0")"

BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT
CP=$(ls lib/jaxws/*.jar lib/bluecove-*.jar | tr '\n' ':')

javac --release 8 -encoding UTF-8 -nowarn -cp "$CP" -d "$BUILD/classes" $(find src -name '*.java')

mkdir -p "$BUILD/fat"
for j in lib/jaxws/*.jar lib/bluecove-*.jar; do
    (cd "$BUILD/fat" && unzip -qo "$OLDPWD/$j")
done
rm -rf "$BUILD/fat/META-INF/MANIFEST.MF" "$BUILD/fat/META-INF/versions" "$BUILD/fat/module-info.class"
find "$BUILD/fat/META-INF" -maxdepth 1 \( -name '*.SF' -o -name '*.RSA' -o -name '*.DSA' \) -delete
cp -R "$BUILD/classes/." "$BUILD/fat/"

# The iPhone web app (Node bridge) rides along in the signed JAR: kijelzopc.BridgeUpdate installs it
# on Linux/Pi at start when it changed (the self-update itself only replaces this JAR)
BUNDLE="$BUILD/fat/pwa-bundle"
mkdir -p "$BUNDLE"
(cd bridge && find src public package.json package-lock.json -type f ! -name '.DS_Store' | LC_ALL=C sort) > "$BUNDLE/FILES"
while read -r f; do mkdir -p "$BUNDLE/$(dirname "$f")"; cp "bridge/$f" "$BUNDLE/$f"; done < "$BUNDLE/FILES"
SHA=$(command -v sha256sum >/dev/null && echo sha256sum || echo "shasum -a 256")
(cd "$BUNDLE" && cat FILES | while read -r f; do printf '%s\n' "$f"; cat "$f"; done | $SHA | cut -d' ' -f1) > "$BUNDLE/HASH"

# Add-Opens: JAX-WS 2.3 needs reflective access to java.lang on Java 16+ (honoured with java -jar)
VERSION=$(git describe --always --dirty 2>/dev/null || echo dev)
printf 'Main-Class: kijelzopc.Main\nAdd-Opens: java.base/java.lang\nImplementation-Version: %s\n' "$VERSION" > "$BUILD/manifest.txt"
mkdir -p out
rm -f out/KijelzoPC.jar
jar cfm out/KijelzoPC.jar "$BUILD/manifest.txt" -C "$BUILD/fat" .
echo "Built out/KijelzoPC.jar ($(du -h out/KijelzoPC.jar | cut -f1))"
