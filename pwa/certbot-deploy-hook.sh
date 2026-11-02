#!/bin/sh
# certbot runs this after every successful issue/renewal of kijelzo.szeretet.ro
# (installed as /etc/letsencrypt/renewal-hooks/deploy/kijelzo-nginx.sh by install-pi.sh)
set -e
LIVE=/etc/letsencrypt/live/kijelzo.szeretet.ro
[ -f "$LIVE/fullchain.pem" ] || exit 0
install -m 644 "$LIVE/fullchain.pem" /etc/kijelzo/tls/fullchain.pem.new
install -m 600 "$LIVE/privkey.pem" /etc/kijelzo/tls/privkey.pem.new
mv /etc/kijelzo/tls/fullchain.pem.new /etc/kijelzo/tls/fullchain.pem
mv /etc/kijelzo/tls/privkey.pem.new /etc/kijelzo/tls/privkey.pem
sync
systemctl reload nginx
