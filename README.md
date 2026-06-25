# SnapVocab

English vocabulary learning app with image recognition — final year project at PTIT (Posts and
Telecommunications Institute of Technology).

- **Mobile app**: [snap-vocab-mobile](https://github.com/thanhthbm/SnapVocab) _(coming soon)_
- **Backend**: this repo

## Overview

SnapVocab helps learners build English vocabulary by taking photos of real-world objects. The system
uses YOLO object detection to identify objects in images, then returns English words with Vietnamese
definitions, pronunciations, and audio.

Beyond image recognition, the app supports flashcard study with Spaced Repetition System (SRS),
quizzes, progress tracking, and gamification features (leaderboards, quests, badges, item shop) to
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

- **Image recognition** — Capture/upload photos, detect objects via YOLO, return English vocabulary
- **Dictionary lookup** — English word → Vietnamese definitions, IPA pronunciations, audio
- **Flashcard learning** — Study with quizzes, SRS-based review scheduling
- **Personal word list** — Save and manage vocabulary

### Community & Gamification

- **Learning groups** — Join or create study groups, group challenges
- **Leaderboards** — Individual, group, and tournament rankings
- **Quests & badges** — Earn XP, coins, badges by completing learning tasks
- **Item shop** — Buy and use items with coins

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
- Gamification (quests, badges, leaderboards)
- Community interaction (posts, comments)
- Study groups and group challenges
- Item shop purchases

### Group Owner

Creates and manages study groups:

- Create and update groups
- Manage members
- Create group challenges
- View member leaderboards
- Manage group posts

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
