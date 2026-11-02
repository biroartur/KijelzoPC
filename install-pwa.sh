#!/bin/bash
# Installs/updates the iPhone web app (PWA, https://kijelzo.szeretet.ro) on the Raspberry Pi:
# Node bridge as a service + nginx with HTTPS. Called by install-pi.sh; needs passwordless sudo.
#   bash install-pwa.sh <user> <app dir>
# The real certificate is fetched once with: sudo bash setup-certificate.sh
set -e

SRC="$(cd "$(dirname "$0")" && pwd)"
TARGET_USER="$1"
APP="${2:?usage: install-pwa.sh <user> <app dir>}"
TLS=/etc/kijelzo/tls

missing=""
for pkg in nodejs npm nginx certbot python3-certbot-dns-route53; do
    dpkg -s "$pkg" >/dev/null 2>&1 || missing="$missing $pkg"
done
if [ -n "$missing" ]; then
    sudo -n apt-get update -qq
    sudo -n DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends $missing >/dev/null
fi

# Bridge code: replaced on every update; node_modules only reinstalled when the lock file changes
mkdir -p "$APP/bridge"
rm -rf "${APP:?}/bridge/src" "${APP:?}/bridge/public"
cp -R "$SRC/bridge/src" "$SRC/bridge/public" "$APP/bridge/"
if ! cmp -s "$SRC/bridge/package-lock.json" "$APP/bridge/package-lock.json" || [ ! -d "$APP/bridge/node_modules" ]; then
    cp "$SRC/bridge/package.json" "$SRC/bridge/package-lock.json" "$APP/bridge/"
    (cd "$APP/bridge" && npm ci --omit=dev --no-audit --no-fund --loglevel=error)
fi
chown -R "$TARGET_USER:$TARGET_USER" "$APP/bridge" 2>/dev/null || true

sed -e "s|@USER@|$TARGET_USER|" -e "s|@APP@|$APP|" "$SRC/pwa/kijelzo-bridge.service" \
    | sudo -n tee /etc/systemd/system/kijelzo-bridge.service >/dev/null

# Certificate: a self-signed placeholder keeps nginx running until the real one is fetched
sudo -n mkdir -p "$TLS"
if [ ! -f "$TLS/fullchain.pem" ]; then
    sudo -n openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -subj "/CN=kijelzo.szeretet.ro" \
        -keyout "$TLS/privkey.pem" -out "$TLS/fullchain.pem" 2>/dev/null
    sudo -n chmod 600 "$TLS/privkey.pem"
fi
sudo -n mkdir -p /etc/letsencrypt/renewal-hooks/deploy
sudo -n install -m 755 "$SRC/pwa/certbot-deploy-hook.sh" /etc/letsencrypt/renewal-hooks/deploy/kijelzo-nginx.sh

sudo -n install -m 644 "$SRC/pwa/nginx-kijelzo.conf" /etc/nginx/sites-available/kijelzo
sudo -n ln -sf /etc/nginx/sites-available/kijelzo /etc/nginx/sites-enabled/kijelzo
sudo -n rm -f /etc/nginx/sites-enabled/default
sudo -n nginx -t -q

sudo -n systemctl daemon-reload
sudo -n systemctl enable -q kijelzo-bridge nginx
sudo -n systemctl restart kijelzo-bridge
sudo -n systemctl reload-or-restart nginx

if sudo -n test -d /etc/letsencrypt/live/kijelzo.szeretet.ro; then
    echo "PWA installed: https://kijelzo.szeretet.ro (Let's Encrypt certificate, renews automatically)"
else
    echo "PWA installed with a temporary certificate. Fetch the real one once: sudo bash $SRC/setup-certificate.sh"
fi
