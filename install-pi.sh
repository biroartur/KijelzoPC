#!/bin/bash
# Installs/updates KijelzoPC into ~/kijelzopc and sets up desktop autostart.
# Run from a clone of the raspberry branch:  bash install-pi.sh
# Keeps an existing activation.key, konyvek.txt (book list) and server.properties.
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
# Keep local settings (e.g. shutdown.time) on update; add the setting to older files
[ -f "$APP/server.properties" ] || cp "$SRC/server.properties" "$APP/"
grep -q '^shutdown.time' "$APP/server.properties" || printf '\nshutdown.time = 22:00\n' >> "$APP/server.properties"
[ -f "$APP/konyvek.txt" ] || cp "$SRC/konyvek.txt" "$APP/"

sed -i "s|publish.wsdl.address=.*|publish.wsdl.address=http://$STATIC_IP:7777/wsdl/|" "$APP/server.properties"
sed -i "s|publish.html.address=.*|publish.html.address=http://$STATIC_IP:7777/|" "$APP/server.properties"

cat > "$TARGET_HOME/.config/autostart/kijelzopc.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=KijelzoPC
Exec=bash -c 'sleep 5 && $APP/start.sh >> /tmp/kijelzopc.out 2>&1'
Hidden=false
NoDisplay=false
X-GNOME-Autostart-enabled=true
EOF

cp "$SRC/wallpaper/kijelzo-wallpaper.jpg" "$APP/"

# Kijelzo wallpaper on first install only: a desktop config of the user's own is never overwritten
PCMANFM="$TARGET_HOME/.config/pcmanfm/default"
if [ ! -f "$PCMANFM/desktop-items-0.conf" ] && [ -f /etc/xdg/pcmanfm/default/desktop-items-0.conf ]; then
    mkdir -p "$PCMANFM"
    for n in 0 1; do
        [ -f "/etc/xdg/pcmanfm/default/desktop-items-$n.conf" ] || continue
        sed -e "s|^wallpaper=.*|wallpaper=$APP/kijelzo-wallpaper.jpg|" \
            -e "s|^wallpaper_mode=.*|wallpaper_mode=crop|" \
            -e "s|^desktop_bg=.*|desktop_bg=#0b0d12|" \
            "/etc/xdg/pcmanfm/default/desktop-items-$n.conf" > "$PCMANFM/desktop-items-$n.conf"
    done
fi

# Dark desktop (theme, black taskbar, window frames) once on first install; later manual changes are kept
if [ ! -f "$APP/.dark-theme-applied" ] && [ -d /usr/share/themes/PiXonyx ]; then
    XS="$TARGET_HOME/.config/xsettingsd/xsettingsd.conf"
    if [ ! -f "$XS" ] && [ -f /etc/xsettingsd/xsettingsd.conf ]; then
        mkdir -p "$(dirname "$XS")"
        cp /etc/xsettingsd/xsettingsd.conf "$XS"
    fi
    if [ -f "$XS" ]; then
        sed -i -e 's|^Net/ThemeName .*|Net/ThemeName "PiXonyx"|' \
            -e 's|bar_bg_color:#[0-9a-f]*|bar_bg_color:#000000000000|' \
            -e 's|bar_fg_color:#[0-9a-f]*|bar_fg_color:#ffffffffffff|' "$XS"
    fi
    if [ ! -f "$TARGET_HOME/.config/labwc/rc.xml" ] && [ -f /etc/xdg/labwc/rc.xml ]; then
        mkdir -p "$TARGET_HOME/.config/labwc"
        sed 's|<name>PiXtrix</name>|<name>PiXonyx</name>|' /etc/xdg/labwc/rc.xml > "$TARGET_HOME/.config/labwc/rc.xml"
    fi
    sudo -n -u "$TARGET_USER" dbus-run-session sh -c \
        "gsettings set org.gnome.desktop.interface gtk-theme PiXonyx; gsettings set org.gnome.desktop.interface color-scheme prefer-dark" \
        >/dev/null 2>&1 || true
    touch "$APP/.dark-theme-applied"
fi

# Console output goes to RAM (/tmp); the app's own log is in logs/ (one file per day, kept 90 days)
rm -f "$APP/kijelzopc.out"

chown -R "$TARGET_USER:$TARGET_USER" "$APP" "$TARGET_HOME/.config/autostart" "$TARGET_HOME/.config/pcmanfm" \
    "$TARGET_HOME/.config/xsettingsd" "$TARGET_HOME/.config/labwc" 2>/dev/null || true

# The display must never blank or sleep (needs passwordless sudo, as the pi user has)
if sudo -n true 2>/dev/null; then
    sudo -n raspi-config nonint do_blanking 1 || true
    # Daily 22:00 shutdown follows local time
    sudo -n timedatectl set-timezone Europe/Bucharest || true
    sudo -n systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target >/dev/null 2>&1 || true

    # Protect the SD card: system log in RAM only, swap only in compressed RAM (no swap file,
    # no daily writeback to the card), no background package/manual database jobs.
    sudo -n mkdir -p /etc/systemd/journald.conf.d /etc/rpi/swap.conf.d
    printf '[Journal]\nStorage=volatile\nRuntimeMaxUse=30M\n' | sudo -n tee /etc/systemd/journald.conf.d/kijelzo.conf >/dev/null
    printf '[Main]\nMechanism=zram\n' | sudo -n tee /etc/rpi/swap.conf.d/kijelzo.conf >/dev/null
    # packagekit (desktop "updates available" checker) rewrites ~90 MB of package data on every boot
    sudo -n systemctl mask rpi-zram-writeback.timer apt-daily.timer apt-daily-upgrade.timer \
        man-db.timer dpkg-db-backup.timer packagekit.service >/dev/null 2>&1 || true
    sudo -n systemctl restart systemd-journald || true
fi

echo "KijelzoPC installed to $APP (autostarts at desktop login; output in /tmp/kijelzopc.out)"
