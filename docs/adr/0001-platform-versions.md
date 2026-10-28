# ADR 0001 Nền tảng và phiên bản

- Trạng thái: accepted
- Ngày tra cứu: 2026-10-25

## Quyết định

| Thành phần | Phiên bản pin | Ghi chú |
|---|---:|---|
| Java | 21, Temurin 21.0.12+8 | JDK LTS; build với `--release 21`; image runtime `eclipse-temurin:21.0.12_8-jre-noble` |
| Spring Boot | 4.1.2 | GA, hỗ trợ Java 17–27 |
| Spring Cloud BOM | 2025.1.4 | Release train tương thích Boot 4.0.x/4.1.x |
| Maven Wrapper | 3.9.11 | Pinned trong `.mvn/wrapper/maven-wrapper.properties` |
| PostgreSQL | 18.6 | Một instance local, database và role riêng theo service |
| RabbitMQ | 4.2.9-management | Management UI local |
| Redis | 8.2.10 | Bản extended support trong nhánh 8.2 |
| MinIO | Chainguard digest `sha256:87896cb33f93ecfcca4e883c3fb2f86a0a1a600c003a104011ed30631c1869c3` | Digest bất biến vì tag upstream từng dùng không còn kéo được; client `mc` cũng ghim digest, chạy cùng shell Alpine `3.22.2` để init/backup |
| Mailpit | v1.31.6 | SMTP dev và mailbox UI |
| Node.js | 24.22.1 | LTS, pin ở `.nvmrc` và image frontend |
| Next.js | 16.4.3 | Bản stable; không dùng canary/RC |
| pnpm | 12.15.0 | Pin trong `packageManager` và `pnpm-lock.yaml` |

## Nguồn chính thức

- Spring Boot system requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Cloud version mapping: https://spring.io/projects/spring-cloud/
- Temurin release: https://github.com/adoptium/temurin21-binaries/releases
- PostgreSQL version policy: https://www.postgresql.org/support/versioning/
- Redis version policy/releases: https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/ và https://download.redis.io/releases/
- RabbitMQ official Docker tags: https://hub.docker.com/_/rabbitmq
- MinIO container docs: https://min.io/docs/minio/container/index.html
- Mailpit releases: https://github.com/axllent/mailpit/releases
- Node release table: https://nodejs.org/en/about/previous-releases
- Next.js official releases: https://github.com/vercel/next.js/releases
- pnpm releases: https://github.com/pnpm/pnpm/releases

## Hệ quả

Boot 4 yêu cầu kiểm tra tương thích starter/ thư viện ở từng cập nhật; các phiên bản không thả nổi. Mỗi lần nâng phiên bản phải cập nhật ADR, image tag, lockfile và CI cùng lúc.

