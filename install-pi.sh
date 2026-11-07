#!/bin/bash
# Installs/updates KijelzoPC into ~/kijelzopc and sets up desktop autostart, on a Raspberry Pi or a Debian PC.
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
# Raspberry Pi (SD card, Pi desktop) or a Debian PC (disk, XFCE; prepared by the linux-pc installer stick)
if grep -qs 'Raspberry Pi' /proc/device-tree/model; then IS_PI=1; else IS_PI=0; fi

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

cp "$SRC/wallpaper/kijelzo-wallpaper.jpg" "$SRC/wallpaper/kijelzo-wallpaper-5x4.jpg" "$APP/"

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

# PC with XFCE: same look (dark, Kijelzo wallpaper), applied once at the first login
if [ "$IS_PI" = 0 ] && command -v xfconf-query >/dev/null; then
    install -m 755 "$SRC/pc-desktop.sh" "$APP/pc-desktop.sh"
    cat > "$TARGET_HOME/.config/autostart/kijelzo-desktop.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Kijelzo desktop look
Exec=$APP/pc-desktop.sh
Hidden=false
NoDisplay=true
X-GNOME-Autostart-enabled=true
EOF
fi

# Console output goes to RAM (/tmp); the app's own log is in logs/ (one file per day, kept 90 days)
rm -f "$APP/kijelzopc.out"

chown -R "$TARGET_USER:$TARGET_USER" "$APP" "$TARGET_HOME/.config/autostart" "$TARGET_HOME/.config/pcmanfm" \
    "$TARGET_HOME/.config/xsettingsd" "$TARGET_HOME/.config/labwc" 2>/dev/null || true

# The display must never blank or sleep (needs passwordless sudo, as the pi user has)
if sudo -n true 2>/dev/null; then
    [ "$IS_PI" = 1 ] && { sudo -n raspi-config nonint do_blanking 1 || true; }
    # Daily 22:00 shutdown follows local time
    sudo -n timedatectl set-timezone Europe/Bucharest || true
    # Never sleep: no suspend/hibernate at all, and no idle, lid or suspend-key action
    sudo -n systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target \
        suspend-then-hibernate.target >/dev/null 2>&1 || true
    sudo -n mkdir -p /etc/systemd/logind.conf.d
    printf '[Login]\nIdleAction=ignore\nHandleSuspendKey=ignore\nHandleHibernateKey=ignore\nHandleLidSwitch=ignore\nHandleLidSwitchExternalPower=ignore\n' \
        | sudo -n tee /etc/systemd/logind.conf.d/kijelzo.conf >/dev/null
    if [ "$IS_PI" = 0 ]; then
        # PC desktop: no screen locker or screensaver (the installer stick removes them too)
        for pkg in light-locker xfce4-screensaver; do
            dpkg -s "$pkg" >/dev/null 2>&1 && sudo -n apt-get purge -y -qq "$pkg" >/dev/null 2>&1 || true
        done
        # Wake-on-LAN: keep the wired network card listening for the wake-up packet after shutdown
        # (the BIOS must allow it too). NetworkManager's setting plus this backup for drivers ignoring it.
        for c in $(nmcli -t -f NAME,TYPE con show 2>/dev/null | sed -n 's/:802-3-ethernet$//p'); do
            sudo -n nmcli con modify "$c" 802-3-ethernet.wake-on-lan magic >/dev/null 2>&1 || true
        done
        printf '%s\n' '[Unit]' 'Description=Keep Wake-on-LAN armed on wired network cards (Kijelzo)' \
            'After=network-online.target' 'Wants=network-online.target' '' '[Service]' 'Type=oneshot' \
            "ExecStart=/bin/sh -c 'for d in /sys/class/net/*; do [ -e \"\$d/device\" ] && [ ! -d \"\$d/wireless\" ] && /usr/sbin/ethtool -s \"\$(basename \"\$d\")\" wol g 2>/dev/null; done; true'" \
            '' '[Install]' 'WantedBy=multi-user.target' | sudo -n tee /etc/systemd/system/kijelzo-wol.service >/dev/null
        command -v ethtool >/dev/null || sudo -n apt-get install -y -qq ethtool >/dev/null 2>&1 || true
        sudo -n systemctl daemon-reload
        sudo -n systemctl enable -q kijelzo-wol.service 2>/dev/null || true
        # Some PCs with an Intel USB 3 (xHCI) controller switch themselves back on ~20 s after power-off
        # (seen on a Lenovo ThinkCentre M73 at the daily 22:00 shutdown). Releasing the controller at the
        # very end of power-off prevents it; Wake-on-LAN keeps working, restarts are not affected.
        sudo -n mkdir -p /usr/lib/systemd/system-shutdown
        printf '%s\n' '#!/bin/sh' '# Kijelzo: stop Intel USB 3 controllers from switching the PC back on after power-off' \
            '[ "$1" = "poweroff" ] || exit 0' \
            'for d in /sys/bus/pci/drivers/xhci_hcd/0000:*; do' \
            '    [ -e "$d" ] && echo "${d##*/}" > /sys/bus/pci/drivers/xhci_hcd/unbind 2>/dev/null' \
            'done' 'exit 0' | sudo -n tee /usr/lib/systemd/system-shutdown/kijelzo-usb-off.sh >/dev/null
        sudo -n chmod 755 /usr/lib/systemd/system-shutdown/kijelzo-usb-off.sh
    fi

    if [ "$IS_PI" = 1 ]; then
        # Protect the SD card: system log in RAM only, swap only in compressed RAM (no swap file,
        # no daily writeback to the card), no background package/manual database jobs.
        # Not on a PC: it has a disk, and its automatic security updates need the apt timers.
        sudo -n mkdir -p /etc/systemd/journald.conf.d /etc/rpi/swap.conf.d
        printf '[Journal]\nStorage=volatile\nRuntimeMaxUse=30M\n' | sudo -n tee /etc/systemd/journald.conf.d/kijelzo.conf >/dev/null
        printf '[Main]\nMechanism=zram\n' | sudo -n tee /etc/rpi/swap.conf.d/kijelzo.conf >/dev/null
        # packagekit (desktop "updates available" checker) rewrites ~90 MB of package data on every boot
        sudo -n systemctl mask rpi-zram-writeback.timer apt-daily.timer apt-daily-upgrade.timer \
            man-db.timer dpkg-db-backup.timer packagekit.service >/dev/null 2>&1 || true
        sudo -n systemctl restart systemd-journald || true
    fi

    # iPhone web app (PWA) at https://kijelzo.szeretet.ro, as on the Windows PC
    bash "$SRC/install-pwa.sh" "$TARGET_USER" "$APP" || echo "PWA setup failed (the display works without it)"
fi

echo "KijelzoPC installed to $APP (autostarts at desktop login; output in /tmp/kijelzopc.out)"
