# Các lệnh đã dùng và mục đích

Kết nối server bằng `ssh oracle`. Không có token hoặc mật khẩu trong tài liệu này.

| Lệnh / thao tác | Mục đích |
| --- | --- |
| `hostname`, `whoami`, `uname -sr` | Xác định host, tài khoản SSH và hệ điều hành |
| `docker version`, `docker compose version` | Kiểm tra Docker Engine và Compose |
| `docker ps -a` | Liệt kê container có sẵn, trạng thái và cổng |
| `docker inspect` với các trường mounts, networks, labels | Tìm volume, network và file Compose quản lý Jenkins |
| `docker exec jenkins-agent id` | Kiểm tra UID/GID và quyền truy cập socket |
| `docker logs --tail=... jenkins-agent` | Xác nhận agent kết nối controller |
| `free -h`, `df -h /` | Kiểm tra RAM và dung lượng đĩa |
| `systemctl is-active ...`, `ss -lnt` | Xác định MariaDB, Redis, Nginx và cổng lắng nghe |
| `sudo mariadb ... SHOW DATABASES` | Xác nhận database `snapvocab` và tài khoản DB hiện có |
| `redis-cli ping` | Xác nhận Redis host trả PONG và không cần password |
| Đọc XML Jenkins, chỉ lấy ID credential/cấu hình node | Tìm credential, label agent, job và executor; không in secrets |
| Sao lưu Compose và Jenkins config vào `/home/ubuntu/jenkins/.backups/` | Có cấu hình để phục hồi |
| `docker compose -f docker-compose.yml -f compose.cicd.yml config --quiet` | Kiểm tra cấu hình agent trước khi áp dụng |
| `docker compose ... build agent` | Tạo image agent có Docker CLI và Compose |
| `docker compose ... up -d --no-deps agent` | Chỉ cập nhật agent, giữ volume đang dùng |
| `docker exec jenkins-agent docker version` | Xác nhận agent gọi được Docker Engine host |
| `git ls-remote` từ trong agent | Xác nhận agent truy cập được GitHub |
| `sudo python3 /home/ubuntu/snapvocab-jenkins/update-prod-env.py` | Đọc YAML qua stdin, sao lưu rồi cập nhật `.env.prod`; không ghi nội dung YAML ra file tạm |
| Truy vấn `SELECT 1` bằng credentials vừa cập nhật | Xác nhận kết nối MariaDB thật sự thành công |
| `chmod 600` cho `.env.prod` | Giới hạn quyền đọc secrets |
| `mariadb-dump --single-transaction --quick --routines --events snapvocab` kết hợp gzip | Sao lưu database trước lần deploy đầu |
| `gzip -t` | Kiểm tra bản sao lưu nén không lỗi |
| `docker pull mysql:8.0`, `maven:3.9-eclipse-temurin-17`, `curlimages/curl:latest` | Chuẩn bị image chạy test và kiểm tra API |
| `docker cp` vào `init.groovy.d` | Cài script khởi tạo Jenkins một lần để tạo job và đặt executor controller về 0 |
| `docker restart jenkins` | Áp dụng cấu hình Jenkins; không restart MariaDB/Redis/MinIO |

Các file sao lưu đầu tiên:

- Env: `/opt/snapvocab/.backups/.env.prod.20261025T044449Z`.
- Database: `/opt/snapvocab/.backups/snapvocab-before-jenkins-20261025T044752Z.sql.gz`
  (khoảng 35 MB, đã kiểm tra gzip).

Lúc kiểm tra ban đầu có bốn container: `jenkins`, `jenkins-agent`, `minio`,
`redisinsight`. MariaDB, Redis và Nginx chạy trực tiếp trên host.

## Kết quả build đầu tiên

- Job `snapvocab-backend`, build `1`, nhánh `prod`, commit `116014a85331`.
- Maven chạy 341 test: 1 failure, 0 error, 3 skipped.
- Test thất bại:
  `MissionMigrationScriptTest.scriptIsIdempotentAndMatchesTheEntitySchema`.
  Migration đặt tên CHECK constraint rõ ràng, trong khi schema Hibernate tạo
  tên tự động; phép so sánh bao gồm tên constraint nên báo khác nhau.
- Pipeline đã chặn build/push image và deploy. Chưa có container backend được
  khởi chạy bởi pipeline này. Container MySQL/Maven test đã được dọn.
- Chưa sửa source/test trên nhánh `prod`; chưa commit hoặc push các file local.

## Xác minh database test và production

- `.env.prod` sử dụng MariaDB database `snapvocab`.
- `SHOW DATABASES` trên MariaDB host không có `snapvocab_it`.
- `snapvocab_it` chỉ tồn tại trong container MySQL test tạm, đã xóa sau build.
- Profile test dùng `ddl-auto: create`; Hibernate có thể cảnh báo khi dọn khóa
  ngoại của bảng chưa tồn tại rồi tiếp tục tạo schema. Log đã ghi nhận
  `create table word_relations` sau cảnh báo. Lỗi chặn build là phép so sánh
  constraint ở trên, không phải cảnh báo dọn bảng này.
