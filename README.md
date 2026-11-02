# SnapVocab

English vocabulary learning app with image recognition — final year project at PTIT (Posts and
Telecommunications Institute of Technology).

- **Mobile app**: [snap-vocab-mobile](https://github.com/thanhthbm/SnapVocab) _(coming soon)_
- **Backend**: this repo
- **AI service**: [snap-vocab-ai](https://github.com/SnapVocab/snap-vocab-ai)

The AI service is developed and deployed from its own repository. For local development,
clone it separately and follow its README to start FastAPI on port 8000. The backend
continues to call `POST /api/v1/detect` over HTTP; configure `ai-service.base-url`
for the active Spring profile and use matching service tokens on both services.

## Overview

Theme and interface language are device-local app preferences stored in MMKV;
`/api/users/me/settings` only stores account preferences such as study goals and reminders.
For existing databases, stop the old backend and run
[`migration_remove_device_preferences.sql`](migration/migration_remove_device_preferences.sql)
before starting this version. Hibernate schema updates do not remove the old columns.

SnapVocab helps learners build English vocabulary by taking photos of real-world objects. The system
uses Florence-2 zero-shot object detection (CLIP optional) to identify objects in images, then returns English words with Vietnamese
definitions, pronunciations, and audio.

Beyond image recognition, the app supports flashcard study with Spaced Repetition System (SRS),
quizzes, progress tracking, and gamification features (5 daily missions with per-mission rewards, badges, personal leaderboard, item shop) to
keep learners motivated.

## System Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Mobile App  │────▶│  Backend      │────▶│  AI Service   │
│  (React      │     │  (Spring Boot)│     │  (FastAPI)    │
│   Native)    │◀────│               │◀────│               │
└──────────────┘     └──────┬───────┘     └──────────────┘
                            │
                    ┌───────┴───────┐
                    │   Database    │
                    │  (MySQL +     │
                    │   Redis)      │
                    └───────────────┘
```

## Features

### Core

- **Image recognition** — Capture/upload photos, detect objects via Florence-2 (CLIP optional), return English vocabulary
- **Dictionary lookup** — English word → Vietnamese definitions, IPA pronunciations, audio
- **Flashcard learning** — Study with quizzes, SRS-based review scheduling
- **Personal word list** — Save and manage vocabulary

### Gamification

- **Daily missions** — exactly 5 per day, each claimed separately for its own reward ([daily_mission.md](../docs/decisions/daily_mission.md)); no chest/weekly/monthly rewards
- **Leaderboard** — personal ranking by Weekly XP (no rewards)
- **Badges** — 12 fixed achievement badges (streak, quizzes, perfect quizzes, distinct words added), granted once on sync, no XP/Coin reward ([badges.md](../docs/decisions/badges.md))
  - Admin P1: `GET /api/admin/badges` and `GET /api/admin/badges/{id}` require `ROLE_ADMIN`; include inactive rows and earner counts, never grant or mark seen. `version` is `null` until P2. No Admin write endpoints yet; staging acceptance remains required (PD-BDG-13a).
- **Item shop** — Buy and use items with coins ([shop.md](../docs/decisions/shop.md))

Community features (posts, comments, study groups) are out of scope.

### Account

- Registration, login, email verification, password recovery
- Profile management
- Learning progress tracking

## Actors

### Guest

Unregistered user. Can register and log in.

### Learner

Main user of the app. Full access to learning features:

- Image recognition for vocabulary creation
- Dictionary lookup
- Personal word list management
- Flashcard study and SRS review
- Progress tracking
- Gamification (daily missions, badges, personal leaderboard)
- Item shop purchases


## Tech Stack

| Layer          | Technology                   |
|----------------|------------------------------|
| Mobile         | React Native (Android + iOS) |
| Backend        | Java 17, Spring Boot 3.5.15  |
| AI Service     | Python FastAPI               |
| Database       | MySQL, JPA/Hibernate         |
| Cache          | Redis                        |
| Auth           | Spring Security + JWT        |
| Object Storage | Cloudflare R2                |
| API Docs       | Swagger / OpenAPI            |
| Mapping        | MapStruct, Lombok            |
| Build          | Maven                        |

## Quick Start

### Prerequisites

- JDK 17+
- MySQL
- Redis
- SMTP mail server
- Cloudflare R2 bucket (or S3-compatible storage)

### Configuration

Edit `src/main/resources/application-dev.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/snapvocab
    username: root
    password: your_password

  data:
    redis:
      host: localhost
      port: 6379

  mail:
    host: smtp.gmail.com
    port: 587
    username: your_email
    password: your_app_password

jwt:
  secret: your_jwt_secret_key
  access-expiration: 900000
  refresh-expiration: 604800000
```

### Run

```bash
./mvnw spring-boot:run
```

### Tests

```bash
./mvnw test
```

Unit tests run anywhere. The integration tests (`QuizFlowIntegrationTest`,
`SemanticBindingMigrationIntegrationTest`) need a MySQL server on the machine
running them: `LocalDatabaseGuard` aborts the context before connecting to a
remote host, so they can never touch a shared database.

They default to `root` / `123456` on `127.0.0.1:3306` and create the
`snapvocab_it` database themselves. If your local MySQL differs, override:

```bash
SNAPVOCAB_TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/snapvocab_it?createDatabaseIfNotExist=true&serverTimezone=UTC' \
SNAPVOCAB_TEST_DB_USERNAME=root \
SNAPVOCAB_TEST_DB_PASSWORD=your_password \
./mvnw test
```

Without a reachable local MySQL those two classes report context-load errors
while every other test still passes. A throwaway server works just as well:

```bash
docker run -d --name snapvocab-test-db -e MYSQL_ROOT_PASSWORD=123456 \
  -e MYSQL_DATABASE=snapvocab_it -p 3307:3306 mysql:8.0
```

then point `SNAPVOCAB_TEST_DB_URL` at port 3307.

### Dictionary Database

Import the English-Vietnamese dictionary data:

```sql
mysql
-u root -p snapvocab < dictionary.sql
CREATE INDEX idx_word_en ON word (word(191), lang_code(20));
```

## API Endpoints

### Dictionary

| Method | Path                           | Description                                    |
|--------|--------------------------------|------------------------------------------------|
| GET    | `/api/words?englishWord=hello` | Lookup English word with Vietnamese definition |

### Auth

| Method | Path                         | Description            |
|--------|------------------------------|------------------------|
| POST   | `/api/auth/register`         | Register new user      |
| POST   | `/api/auth/activate`         | Activate account       |
| POST   | `/api/auth/authenticate`     | Login                  |
| POST   | `/api/auth/refresh`          | Refresh access token   |
| POST   | `/api/auth/recover-password` | Request password reset |

## Project Structure

```
src/main/java/vn/ptit/snapvocab/
├── config/              # Security, JWT, Redis, Mail config
├── controller/          # REST controllers
├── domain/              # JPA entities, DTOs, mappers, enums
│   ├── dto/             # Response/request DTOs
│   ├── mapper/          # MapStruct mappers
│   └── enumeration/     # Label enums (POS, SubPOS, Relation)
├── repository/          # Spring Data JPA repositories
├── service/             # Business logic
├── security/            # JWT filter, security utils
└── util/                # Exception handlers, helpers
```

## Dictionary Data Source

Dictionary data sourced from [dict.minhqnd.com](https://dict.minhqnd.com) — a free multilingual
dictionary project by [@minhqnd](https://github.com/minhqnd).

- **GitHub**: [github.com/minhqnd/dictionary](https://github.com/minhqnd/dictionary)
- **Statistics**: 357K+ words, 443K+ definitions, 1500+ languages
- **Formats**: SQLite, MySQL
- **License**: CC BY-SA 4.0 (data), MIT (code)

## License

MIT
