FROM jaegertracing/all-in-one:1.76.0 AS jaeger

FROM nginx:1.28-alpine
COPY --from=jaeger /go/bin/all-in-one-linux /usr/local/bin/jaeger-all-in-one
COPY default.conf.template /etc/nginx/templates/default.conf.template
COPY start.sh /start.sh
RUN chmod +x /start.sh
EXPOSE 10000
ENTRYPOINT ["/start.sh"]
