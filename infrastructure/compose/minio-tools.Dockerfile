FROM cgr.dev/chainguard/minio-client@sha256:1a1b84b36947e93b2d0eb9373e1c886eadfdcf223c91f47bcaa3756d3437785c AS minio-client
FROM alpine:3.22.2@sha256:4b7ce07002c69e8f3d704a9c5d6fd3053be500b7f1c69fc0d80990c2ad8dd412

COPY --from=minio-client /usr/bin/mc /usr/local/bin/mc
ENTRYPOINT ["/bin/sh"]
