#!/bin/bash
# Fetches the Let's Encrypt certificate for kijelzo.szeretet.ro once; certbot renews it automatically.
# The name points to a private address (192.168.1.12), so validation uses DNS (Route 53) instead of HTTP.
#
#   sudo bash setup-certificate.sh
#
# Asks for the access key of the AWS IAM user that may edit the szeretet.ro hosted zone (the one
# created for win-acme on the Windows PC; make a second access key for it in the AWS console).
# The key is stored only on this Pi, in /root/.aws/credentials (readable by root only).
set -e
DOMAIN=kijelzo.szeretet.ro

if [ "$(id -u)" -ne 0 ]; then
    echo "Run as root: sudo bash $0" >&2
    exit 1
fi

if [ ! -f /root/.aws/credentials ]; then
    read -r -p "AWS access key ID: " KEY_ID
    read -r -s -p "AWS secret access key (not shown): " SECRET
    echo
    if [ -z "$KEY_ID" ] || [ -z "$SECRET" ]; then
        echo "Both values are needed." >&2
        exit 1
    fi
    umask 077
    mkdir -p /root/.aws
    printf '[default]\naws_access_key_id = %s\naws_secret_access_key = %s\n' "$KEY_ID" "$SECRET" > /root/.aws/credentials
fi

certbot certonly --non-interactive --agree-tos --register-unsafely-without-email \
    --dns-route53 -d "$DOMAIN" --keep-until-expiring
# Copies the certificate to nginx and reloads it (certbot also runs this after each renewal)
RENEWED_LINEAGE="/etc/letsencrypt/live/$DOMAIN" /etc/letsencrypt/renewal-hooks/deploy/kijelzo-nginx.sh
systemctl enable -q --now certbot.timer
echo "Certificate installed: https://$DOMAIN — renewals run automatically (certbot.timer)"
certbot certificates -d "$DOMAIN" 2>/dev/null | grep -E 'Expiry Date' || true
