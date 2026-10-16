#!/bin/sh
# Renderiza nginx.conf a partir de la plantilla sustituyendo SOLO las variables
# listadas (las variables propias de Nginx como $host se dejan intactas).
set -eu

: "${PUBLIC_DOMAIN:?PUBLIC_DOMAIN is required}"
: "${ALT_DOMAIN:=}"
export PUBLIC_DOMAIN ALT_DOMAIN

envsubst '${PUBLIC_DOMAIN} ${ALT_DOMAIN}' \
    < /etc/nginx/nginx.conf.template \
    > /etc/nginx/nginx.conf

# El volumen dynamic-routes puede estar vacío en el primer arranque.
mkdir -p /etc/nginx/conf.d/apps/ask
