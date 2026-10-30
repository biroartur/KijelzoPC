#!/bin/bash
# Installs/updates KijelzoPC into ~/kijelzopc and sets up desktop autostart.
# Run from a clone of the raspberry branch:  bash install-pi.sh
# Keeps an existing activation.key and konyvek.txt (book list).
set -e

SRC="$(cd "$(dirname "$0")" && pwd)"
# Only trust SUDO_USER when running as root ("sudo bash install-pi.sh"); under "sudo -u pi" it names the caller
if [ "$(id -u)" -eq 0 ] && [ -n "$SUDO_USER" ]; then
    TARGET_USER="$SUDO_USER"
else
    TARGET_USER="$(whoami)"
fi
TARGET_HOME=$(getent passwd "$TARGET_USER" | cut -d: -f6)
APP="$TARGET_HOME/kijelzopc"
STATIC_IP="192.168.1.12"

mkdir -p "$APP/out" "$TARGET_HOME/.config/autostart"
cp "$SRC/out/KijelzoPC.jar" "$APP/out/"
cp "$SRC/start.sh" "$APP/"
chmod +x "$APP/start.sh"
cp "$SRC/server.properties" "$APP/"
[ -f "$APP/konyvek.txt" ] || cp "$SRC/konyvek.txt" "$APP/"

sed -i "s|publish.wsdl.address=.*|publish.wsdl.address=http://$STATIC_IP:7777/wsdl/|" "$APP/server.properties"
sed -i "s|publish.html.address=.*|publish.html.address=http://$STATIC_IP:7777/|" "$APP/server.properties"

cat > "$TARGET_HOME/.config/autostart/kijelzopc.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=KijelzoPC
Exec=bash -c 'sleep 5 && $APP/start.sh >> $APP/kijelzopc.out 2>&1'
Hidden=false
NoDisplay=false
X-GNOME-Autostart-enabled=true
EOF

chown -R "$TARGET_USER:$TARGET_USER" "$APP" "$TARGET_HOME/.config/autostart"
echo "KijelzoPC installed to $APP (autostarts at desktop login; output in $APP/kijelzopc.out)"
