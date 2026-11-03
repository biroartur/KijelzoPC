#!/bin/bash
# KijelzoPC start script for Raspberry Pi (Linux)
# Run from the directory containing KijelzoPC.jar and server.properties

cd "$(dirname "$0")"

# Never blank the screen or start a screensaver (X11 desktops, e.g. XFCE on a PC; the Pi's Wayland
# desktop has blanking switched off by install-pi.sh and ignores this)
xset s off s noblank -dpms 2>/dev/null || true

# Install a newer signed release first. The updater runs from a copy (in RAM) so it can replace
# the JAR; if the new version ends right after starting, the previous one is restored and started.
UPDATER=/tmp/kijelzopc-updater.jar
cp out/KijelzoPC.jar "$UPDATER"
java -cp "$UPDATER" kijelzopc.Updater update

java -Dfile.encoding=utf-8 -jar out/KijelzoPC.jar

if java -cp "$UPDATER" kijelzopc.Updater rollback; then
    java -Dfile.encoding=utf-8 -jar out/KijelzoPC.jar
fi
