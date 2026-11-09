#!/usr/bin/env bash
# Uploads the 12 MVP badge images to MinIO as public/badges/<code>.png (keys set by migration_badges_image_v4.sql).
# Run BEFORE that migration, from Git Bash:
#   bash migration/upload-badge-images.sh                         # local docker MinIO (minioadmin)
#   MINIO_URL=https://KEY:SECRET@s3.example.com BUCKET=… bash migration/upload-badge-images.sh   # another MinIO
# `mc cp` overwrites same-named objects; nothing else in the bucket is touched.
set -euo pipefail
cd "$(dirname "$0")"
export MSYS_NO_PATHCONV=1

MINIO_URL=${MINIO_URL:-http://minioadmin:minioadmin@host.docker.internal:9000}
BUCKET=${BUCKET:-snapvocab}

docker run --rm -e "MC_HOST_target=$MINIO_URL" -v "$(pwd -W 2>/dev/null || pwd)/badge-images:/in:ro" \
  --entrypoint sh minio/mc:latest -c \
  "mc cp --quiet --attr 'Content-Type=image/png' /in/*.png target/$BUCKET/public/badges/ && mc ls target/$BUCKET/public/badges/"
