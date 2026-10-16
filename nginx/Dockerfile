FROM nginx:1.27-alpine

# Actualiza los paquetes del SO base para parchear CVEs conocidas
RUN apk upgrade --no-cache

# Elimina la configuración por defecto de Nginx para evitar conflictos con la configuración personalizada
RUN rm -f /etc/nginx/conf.d/default.conf

# Copia la plantilla de configuración. Se renderiza al arrancar (PUBLIC_DOMAIN / ALT_DOMAIN)
COPY nginx.conf.template /etc/nginx/nginx.conf.template
COPY render-conf.sh /docker-entrypoint.d/05-render-conf.sh
RUN chmod +x /docker-entrypoint.d/05-render-conf.sh

# Crea el directorio donde se alojarán las configuraciones de las aplicaciones importadas en el servicio principal
# (y el subdirectorio "ask" con los marcadores que valida el endpoint de certificados bajo demanda)
RUN mkdir -p /etc/nginx/conf.d/apps/ask

# Crea el directorio para el log de auditoría (IPs reales de acceso vía Cloudflare Tunnel o vía edge directo)
RUN mkdir -p /var/log/nginx-audit

# Agrega un healthcheck para verificar que Nginx esté funcionando correctamente
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1/health || exit 1

# Expone el puerto 80 para que Nginx pueda recibir tráfico HTTP
EXPOSE 80
