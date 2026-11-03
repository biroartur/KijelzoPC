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
# Power button shuts down at once (XFCE's default asks in a dialog hidden behind the full-screen display)
xfconf-query -c xfce4-power-manager -p /xfce4-power-manager/power-button-action -n -t int -s 4 2>/dev/null \
    || xfconf-query -c xfce4-power-manager -p /xfce4-power-manager/power-button-action -s 4
# A 5:4 monitor (e.g. 1280x1024) gets its own version, so the picture fills it without dark bars
WALLPAPER="$APP/kijelzo-wallpaper.jpg"
SIZE=$(xrandr 2>/dev/null | awk '/\*/ {print $1; exit}')
if [ -f "$APP/kijelzo-wallpaper-5x4.jpg" ] && [ $((${SIZE%x*} * 4)) -eq $((${SIZE#*x} * 5)) ] 2>/dev/null; then
    WALLPAPER="$APP/kijelzo-wallpaper-5x4.jpg"
fi
for p in $(xfconf-query -c xfce4-desktop -l | grep '/last-image$'); do
    xfconf-query -c xfce4-desktop -p "$p" -s "$WALLPAPER"
done
for p in $(xfconf-query -c xfce4-desktop -l | grep '/image-style$'); do
    # scaled: the whole picture, dark bars on screens that are not 16:9 (zoomed cut off the sides on 5:4)
    xfconf-query -c xfce4-desktop -p "$p" -s 4
    b="${p%/image-style}"
    xfconf-query -c xfce4-desktop -p "$b/color-style" -n -t int -s 0
    xfconf-query -c xfce4-desktop -p "$b/rgba1" -n -t double -s 0.03 -t double -s 0.03 -t double -s 0.04 -t double -s 1
done
# XFCE may only have the generic monitor0/monitor1 settings while it shows the real output
# (e.g. monitorVGA-1), so set every connected output's workspaces explicitly as well
for m in $(xrandr 2>/dev/null | awk '/ connected/ {print $1}'); do
    for ws in 0 1 2 3; do
        b="/backdrop/screen0/monitor$m/workspace$ws"
        xfconf-query -c xfce4-desktop -p "$b/last-image" -n -t string -s "$WALLPAPER"
        xfconf-query -c xfce4-desktop -p "$b/image-style" -n -t int -s 4
        xfconf-query -c xfce4-desktop -p "$b/color-style" -n -t int -s 0
        xfconf-query -c xfce4-desktop -p "$b/rgba1" -n -t double -s 0.03 -t double -s 0.03 -t double -s 0.04 -t double -s 1
    done
done
touch "$MARKER"
