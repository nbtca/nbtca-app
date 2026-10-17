FROM alpine:3.21

RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app
COPY nbtca-backend-linux-amd64 /app/nbtca-backend
RUN chmod +x /app/nbtca-backend

ENV PORT=8080 \
    TZ=Asia/Shanghai

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/health >/dev/null || exit 1

USER 65534:65534
ENTRYPOINT ["/app/nbtca-backend"]
