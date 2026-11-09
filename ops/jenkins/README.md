# SnapVocab Jenkins tren oracle

## Cau hinh hien tai

- Jenkins URL: https://jenkins.thanhthbm.online
- Controller container: `jenkins`; built-in executors: `0`.
- Agent container: `jenkins-agent`; node: `spring-agent`; label: `spring`; executors: `1`.
- Job: `snapvocab-backend`; checkout nhanh `prod`.
- `master` la nhanh dev, khong tu dong deploy production.
- Credential ID: `github-snapvocab` (Git checkout va GHCR).
- Workspace volume: `jenkins_agent_home`, mount tai `/home/jenkins/agent`.
- Agent dung Docker socket cua host; khong chay Docker daemon rieng.
- GitHub push webhook triggers an SCM check for `prod`; scheduled polling is disabled.
- Repository: `https://github.com/SnapVocab/snap-vocab-backend`.
- Webhook: `https://jenkins.thanhthbm.online/github-webhook/`, push events only,
  HTTPS verification enabled. Its secret is the existing Jenkins credential
  `snapvocab-admin-webhook`; the admin job and its webhook are unchanged.

## File tren server

| File | Muc dich |
| --- | --- |
| `/home/ubuntu/jenkins/docker-compose.yml` | Compose Jenkins goc |
| `/home/ubuntu/jenkins/compose.cicd.yml` | Bo sung image agent, group Docker socket va volume production |
| `/home/ubuntu/snapvocab-jenkins/Dockerfile.agent` | Agent Java 21, Docker CLI va Compose |
| `Jenkinsfile` on `prod` | Production pipeline source, loaded by Jenkins from SCM |
| `/opt/snapvocab/docker-compose.yml` | Compose backend production |
| `/opt/snapvocab/.env.prod` | Secrets runtime, quyen 600; khong commit |
| `/opt/snapvocab/.backups/` | Ban sao env va database truoc deploy dau |
| `/opt/snapvocab/.deployed-image` | Image cua lan deploy thanh cong gan nhat |
| `/opt/snapvocab/.previous-image` | Image truoc lan deploy gan nhat |

File `compose.production.yml` trong thu muc nay la ban nguon cua Compose
production tren server. File `docker-compose.yml` o goc repo giu nguyen cho
cach chay bridge network hien co.

Production dung host networking de ket noi MariaDB/Redis tren host.
`SERVER_ADDRESS=127.0.0.1` va `SERVER_PORT=8080` giu API tren loopback;
reverse proxy HTTPS can tro toi `http://127.0.0.1:8080`.

Domain API da cau hinh: `https://api.snapvocab.thanhthbm.online`.
Ban cau hinh tuong duong nam tai `../nginx/snapvocab-api.conf`; tren server la
`/etc/nginx/sites-available/snapvocab-api`. Chung chi Let’s Encrypt va private
key chi nam tren server. Certbot timer da bat va `renew --dry-run` da thanh cong.
File Nginx nay dung sau khi chung chi da duoc cap; khi cai server moi, can tao
HTTP site truoc roi chay `certbot --nginx -d api.snapvocab.thanhthbm.online --redirect`.

`Dockerfile.agent` va `compose.cicd.yml` trong thu muc nay la ban nguon cua
image agent va Compose override. Khi cai lai, copy Dockerfile vao
`/home/ubuntu/snapvocab-jenkins/`, override vao `/home/ubuntu/jenkins/`, roi
export `DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)` truoc khi chay Compose.

`.env.prod` lay DB, mail, JWT, MinIO, AI tu `application-dev.yaml` theo yeu cau.
Redis dung `127.0.0.1:6379`, database 0, khong password.
AI dung Runpod Serverless: dat `AI_SERVICE_URL`, `RUNPOD_API_KEY`,
`AI_SERVICE_TOKEN`, `AI_SERVICE_READ_TIMEOUT_MS` va
`AI_SERVICE_COLD_START_RETRIES` trong `/opt/snapvocab/.env.prod` giong `.env`
backend local. Khong dung URL localhost cu. File `.env` local khong duoc tu dong
copy len server; can cap nhat env server va tao lai container khi doi gia tri.
`AI_SERVICE_TOKEN` phai khop `SERVICE_TOKEN` cua worker neu worker bat bao ve.
Preflight tu choi URL localhost/loopback va Runpod Serverless thieu API key;
kiem tra nay khong in credentials va khong tao AI job.

## Luong pipeline

1. Checkout `prod` bang credential Jenkins.
2. Kiem tra Compose, workspace volume va network agent.
3. Tao MySQL 8 tam tren `jenkins_default`, khong publish port.
4. Chay Maven Java 17 trong container dung chung network namespace voi MySQL.
   Test dung profile `test`, datasource duoc ep sang database tam. Source code
   duoc mount qua dung named volume cua agent, khong dung bind path gia dinh.
5. Build image tu Dockerfile va JAR; context build khong chua source/secrets.
6. Push GHCR voi tag `build-<build-number>-<commit>`.
7. Neu `DEPLOY_PRODUCTION=true`, cap nhat backend va goi
   `GET /api/collections/system` qua loopback.
8. Neu probe loi va da co image cu, khoi phuc image cu va danh dau build that bai.
9. Thu thap bao cao JUnit va xoa container test/probe.

Rollback image khong rollback schema/data. Database hien dung Hibernate
`ddl-auto: update`. Ban backup dau tien khong thay the backup dinh ky.
Smoke test chi kiem tra API nay, khong xac minh tat ca luong mail/MinIO/AI.

## Lenh van hanh

Chay tren server qua `ssh oracle`.

```bash
# Kiem tra container va trang thai agent.
docker ps
docker logs --tail=50 jenkins-agent

# Khi cap nhat agent, can dung ca Compose goc va override.
cd /home/ubuntu/jenkins
docker compose -f docker-compose.yml -f compose.cicd.yml config --quiet
docker compose -f docker-compose.yml -f compose.cicd.yml build agent
docker compose -f docker-compose.yml -f compose.cicd.yml up -d --no-deps agent

# Kiem tra backend tu server.
curl --fail http://127.0.0.1:8080/api/collections/system
docker logs --tail=100 snapvocab-backend
```

Khong in `docker inspect .Config.Env`, `docker compose config` day du, hoac
`cat .env.prod` vao log chia se. `config --quiet` van kiem tra duoc cu phap.

De rollback thu cong sau khi da co mot image cu:

```bash
sudo sh -c '
  set -eu
  cd /opt/snapvocab
  export BACKEND_IMAGE="$(cat .previous-image)"
  export IMAGE_TAG=rollback
  docker compose -p snapvocab up -d --no-deps backend
  curl --fail http://127.0.0.1:8080/api/collections/system
  printf "%s\n" "$BACKEND_IMAGE" > .deployed-image
  chown 1000:1000 .deployed-image
'
```

## Cap nhat Jenkinsfile

The job uses `Pipeline script from SCM`, Git URL
`https://github.com/SnapVocab/snap-vocab-backend.git`, branch `*/prod`, credential
`github-snapvocab`, script path `Jenkinsfile`. Pipeline changes take effect on
the next `prod` push. Pushes to other branches do not deploy production.
Do not commit `.env.prod`, webhook secrets or tokens.
