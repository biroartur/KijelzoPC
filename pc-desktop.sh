#!/bin/sh
# Kijelzo look for the XFCE desktop on a Debian PC: dark theme, Kijelzo wallpaper on every monitor,
# no desktop icons. Started at login (installed by install-pi.sh); applies once, so later manual
# changes are kept. XFCE creates the per-monitor wallpaper settings only after the desktop starts.
APP="$(cd "$(dirname "$0")" && pwd)"
MARKER="$APP/.pc-desktop-applied"
[ -f "$MARKER" ] && exit 0

for i in $(seq 1 30); do
    xfconf-query -c xfce4-desktop -l 2>/dev/null | grep -q '/last-image$' && break
    sleep 2
done

xfconf-query -c xsettings -p /Net/ThemeName -s Adwaita-dark
xfconf-query -c xsettings -p /Net/IconThemeName -s Adwaita 2>/dev/null
xfconf-query -c xfce4-desktop -p /desktop-icons/style -n -t int -s 0 2>/dev/null \
    || xfconf-query -c xfce4-desktop -p /desktop-icons/style -s 0
for p in $(xfconf-query -c xfce4-desktop -l | grep '/last-image$'); do
    xfconf-query -c xfce4-desktop -p "$p" -s "$APP/kijelzo-wallpaper.jpg"
done
for p in $(xfconf-query -c xfce4-desktop -l | grep '/image-style$'); do
    xfconf-query -c xfce4-desktop -p "$p" -s 5   # zoomed: fills the screen, keeps proportions
done
touch "$MARKER"
