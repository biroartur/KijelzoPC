#!/bin/bash
# KijelzoPC — Raspberry Pi first-time setup
# Run as the default user (pi / your chosen username) after first boot:
#   bash setup-raspberry.sh
#
# What this does:
#   1. Sets static IP 192.168.1.12 on eth0
#   2. Installs Java
#   3. Places the app in ~/kijelzopc
#   4. Installs and enables the systemd service (auto-start on boot)
#   5. Reboots

set -e

INSTALL_DIR="$HOME/kijelzopc"
STATIC_IP="192.168.1.12"
GATEWAY="192.168.1.1"
DNS="8.8.8.8"

echo "=== KijelzoPC Raspberry Pi Setup ==="
echo ""

# ── 1. Static IP ─────────────────────────────────────────────────────────────
echo "[1/4] Setting static IP $STATIC_IP on eth0..."

if command -v nmcli &>/dev/null; then
    # Raspberry Pi OS Bookworm (NetworkManager)
    CON=$(nmcli -t -f NAME con show --active | grep -v lo | head -1)
    nmcli con mod "$CON" \
        ipv4.addresses "$STATIC_IP/24" \
        ipv4.gateway "$GATEWAY" \
        ipv4.dns "$DNS" \
        ipv4.method manual
    nmcli con up "$CON"
else
    # Raspberry Pi OS Bullseye (dhcpcd)
    DHCPCD=/etc/dhcpcd.conf
    if ! grep -q "interface eth0" "$DHCPCD" 2>/dev/null; then
        sudo tee -a "$DHCPCD" > /dev/null <<EOF

interface eth0
static ip_address=$STATIC_IP/24
static routers=$GATEWAY
static domain_name_servers=$DNS
EOF
    else
        echo "  (static IP already configured in dhcpcd.conf, skipping)"
    fi
fi

# ── 2. Java ───────────────────────────────────────────────────────────────────
echo "[2/4] Installing Java..."
sudo apt-get update -qq
sudo apt-get install -y default-jdk

# ── 3. App files ──────────────────────────────────────────────────────────────
echo "[3/4] Installing KijelzoPC to $INSTALL_DIR..."
mkdir -p "$INSTALL_DIR/out"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cp "$SCRIPT_DIR/out/KijelzoPC.jar"  "$INSTALL_DIR/out/"
cp "$SCRIPT_DIR/server.properties"  "$INSTALL_DIR/"
cp "$SCRIPT_DIR/konyvek.txt"        "$INSTALL_DIR/"

# Update IP in server.properties to match this machine
sed -i "s|publish.wsdl.address=.*|publish.wsdl.address=http://$STATIC_IP:7777/wsdl/|" "$INSTALL_DIR/server.properties"
sed -i "s|publish.html.address=.*|publish.html.address=http://$STATIC_IP:7777/|" "$INSTALL_DIR/server.properties"

# ── 4. Systemd service ────────────────────────────────────────────────────────
echo "[4/4] Installing systemd service..."
USERNAME=$(whoami)
sudo tee /etc/systemd/system/kijelzopc.service > /dev/null <<EOF
[Unit]
Description=KijelzoPC Church Display Server
After=network.target graphical.target

[Service]
Type=simple
User=$USERNAME
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/java -Dfile.encoding=utf-8 -jar $INSTALL_DIR/out/KijelzoPC.jar
Restart=on-failure
RestartSec=5
Environment=DISPLAY=:0

[Install]
WantedBy=graphical.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable kijelzopc

echo ""
echo "=== Setup complete! ==="
echo ""
echo "  Static IP : $STATIC_IP"
echo "  App dir   : $INSTALL_DIR"
echo "  Service   : sudo systemctl start|stop|status kijelzopc"
echo "  Logs      : journalctl -u kijelzopc -f"
echo ""
echo "The server will start automatically on next boot."
echo "Rebooting in 5 seconds... (Ctrl+C to cancel)"
sleep 5
sudo reboot
