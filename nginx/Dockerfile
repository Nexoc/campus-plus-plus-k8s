FROM nginx:alpine

# Add a DNS tool so the container can wait for service-name resolution before
# starting NGINX on a stressed cluster node.
RUN apk add --no-cache bind-tools \
    && rm -f /etc/nginx/conf.d/default.conf

# Copy our nginx config
COPY nginx.conf /etc/nginx/nginx.conf
