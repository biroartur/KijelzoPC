#!/bin/bash
# Builds out/KijelzoPC.jar for the Windows PC: Java 8 bytecode, relies on the
# JAX-WS built into Java 8. Requires a JDK 9+ (for --release 8).
set -e
cd "$(dirname "$0")"

BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT
CP=$(ls lib/bluecove-*.jar | tr '\n' ':')

javac --release 8 -encoding UTF-8 -nowarn -cp "$CP" -d "$BUILD/fat" $(find src -name '*.java')

for j in lib/bluecove-*.jar; do
    (cd "$BUILD/fat" && unzip -qo "$OLDPWD/$j" -x 'META-INF/MANIFEST.MF')
done

printf 'Main-Class: kijelzopc.Main\n' > "$BUILD/manifest.txt"
mkdir -p out
rm -f out/KijelzoPC.jar
jar cfm out/KijelzoPC.jar "$BUILD/manifest.txt" -C "$BUILD/fat" .
echo "Built out/KijelzoPC.jar ($(du -h out/KijelzoPC.jar | cut -f1))"
