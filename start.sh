#!/bin/bash
# KijelzoPC start script for Raspberry Pi (Linux)
# Run from the directory containing KijelzoPC.jar and server.properties

cd "$(dirname "$0")"

# Install a newer signed release first. The updater runs from a copy (in RAM) so it can replace
# the JAR; if the new version ends right after starting, the previous one is restored and started.
UPDATER=/tmp/kijelzopc-updater.jar
cp out/KijelzoPC.jar "$UPDATER"
java -cp "$UPDATER" kijelzopc.Updater update

java -Dfile.encoding=utf-8 -jar out/KijelzoPC.jar

if java -cp "$UPDATER" kijelzopc.Updater rollback; then
    java -Dfile.encoding=utf-8 -jar out/KijelzoPC.jar
fi
