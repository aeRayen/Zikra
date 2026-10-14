# Phase 0 Audit — Architecture and Database Review

Sources: `architecture/SYSTEM_ARCHITECTURE.md` (SA), `prompts/MASTER_BUILD_PROMPT_AR.md` §3–4 and §6 (MP), and `database/initial_schema.sql` (SQL).

Nothing here replaces the documented architecture. Every change is a **[REC]** with its reason, and lists the documents that would need updating if it is accepted.

## 1. The proposed architecture in practical terms

```
┌──────────────── Android app (Kotlin, Compose, M3) ────────────────┐
│ feature:* (UI + ViewModel + use cases)                            │
│   └─ core:* (design-system, database[Room], network, security,    │
│              notifications, navigation, common)                   │
│   └─ platform:* adapters (alarm, VPN, device-policy, audio)       │
│ Local stores: Room DB(s), DataStore, Keystore-protected keys      │
└───────────────▲──────────────────────────────▲────────────────────┘
                │ HTTPS /api/v1 (OpenAPI)       │ HTTPS content packs / audio
┌───────────────┴─────── Reverse proxy (TLS) ──┴────────────────────┐
│ Backend modular monolith (ASP.NET Core OR NestJS — undecided)     │
│  identity · prefs/sync · content catalog · search · protection    │
│  rules · recovery sync (opt-in) · alarm prefs (opt-in) · CMS+audit│
│  · releases            + background worker                        │
├──────────────┬─────────────────┬──────────────────────────────────┤
│ PostgreSQL   │ MinIO (if audio)│ Redis (only if proven)            │
└──────────────┴─────────────────┴──────────────────────────────────┘
External: Google Identity (token verification keys), licensed content
providers, optional LLM provider (unspecified), off-site backup target.
```

**[FACT]** The core choices are appropriate for the product:

- A native Android app is required anyway, because of VpnService, AlarmManager, the device-policy APIs and full-screen intents. Cross-platform UI frameworks would still need native code for these.
- A modular monolith fits a small team, and splitting it into microservices would add cost with no benefit. PostgreSQL is a sound general-purpose choice. REST with OpenAPI fits an iOS client later.

## 2. Android client — assessment and corrections

| Topic | Finding | Recommendation |
|---|---|---|
| Module count | SA lists 31 modules. MP §4 itself warns against empty modules. Thirty-one modules means slow builds and boilerplate for a codebase that is still empty. | **[REC]** Start with about 14: `app`, `core:{common, design-system, database, network, security, notifications}`, `platform:alarm`, `feature:{onboarding, auth, home, prayer (incl. qibla, hijri), quran (incl. tafsir; later memorization), settings}`. Split modules out when they reach a real size. Use Gradle convention plugins (`build-logic`) from day one. *Update:* SA "Suggested modules", MP §4. |
| Module boundaries | Feature modules must not depend on each other. Home needs data from several features. | **[REC]** Define a `HomeCardProvider` interface in `core` and bind implementations with DI multibinding. Disabled or unimplemented features register nothing, which satisfies the "no fake buttons" rule. |
| Shared alarm infrastructure | Adhan (Phase 2) and the smart alarm (Phase 5) need the same exact-alarm, reboot, timezone and Doze handling. | **[REC]** Build `platform:alarm` in Phase 2 with a single scheduler, a reschedule-on-boot / time / timezone-change receiver, direct-boot awareness and a fallback policy. *Update:* RM Phase 2. |
| Local storage | The SA does not define a Room schema. Content and user data must be separated. | **[REC]** Three stores: (a) `content.db`, read-only, prepackaged or downloaded, verified by checksum and signature; (b) `user.db`, holding bookmarks, settings mirrors, goals, alarms and outbox; (c) `private.db`, holding recovery data and protection logs, encrypted with SQLCipher and a key wrapped by Android Keystore. Exclude (c) from Auto Backup and device transfer via `dataExtractionRules`. |
| Settings | DataStore is fine. | Use Proto DataStore for typed settings. |
| Secrets on device | `androidx.security:security-crypto` (EncryptedSharedPreferences) has been deprecated upstream. **[verify current status]** | **[REC]** Use a Keystore-backed AEAD (for example Tink) to wrap tokens and the SQLCipher key. |
| DI | "Hilt or one other stable solution" is still open. | **[REC]** Hilt. It is the standard choice for Android, has the best tooling, and supports the multibinding pattern above. If Kotlin Multiplatform is chosen ([Q-DEF-04]), use Koin for the shared code instead. |
| Background work | WorkManager suits content and list updates and sync. AlarmManager is for exact alarms. | Keep both. Never use WorkManager for alarm firing. |
| Min/target SDK | **Not specified anywhere.** | **[REC]** minSdk 26: it gives `java.time` and `HijrahDate` natively, notification channels, and drops very old devices that are hard to test. targetSdk is the current Play requirement at implementation time. *Decision [Q-ESS-03].* |
| iOS portability | SA asks for platform-neutral rules but names no mechanism. | Option A: share only the API contract (simplest). Option B: Kotlin Multiplatform for domain, prayer calculation and sync logic. Decision [Q-DEF-04]. |

## 3. Backend — assessment and corrections

| Topic | Finding | Recommendation |
|---|---|---|
| Framework | ASP.NET Core vs. NestJS is **undecided**, and the handoff checklist requires a decision. With no existing repository, there is no team-skill signal. | **[REC]** ASP.NET Core (LTS). Built-in OpenAPI, mature EF Core migrations, strong typing end to end, good performance on a small self-hosted server, and first-class background workers (`BackgroundService`). Choose NestJS if the team is TypeScript-first, or if the CMS web front end will be TS and sharing types matters more. **Owner decision [Q-ESS-02].** |
| Bounded contexts | Nine contexts (SA). Sensible. | Enforce boundaries as separate projects or modules with no cross-module DB access. Use one PG database with **separate schemas per context** and separate DB roles (fixes C8). |
| CMS front end | Unspecified. | **[REC]** Server-rendered admin UI in the same backend for v1 (Razor Pages, or a simple SPA). It must sit on a separate admin hostname, with MFA, and ideally be reachable only over a VPN or IP allow-list. |
| Content distribution | Unspecified. | **[REC]** Versioned SQLite **content packs** (Quran, tafsir, hadith, adhkar, blocklists), built by a pipeline. Each pack has a manifest (area, version, SHA-256 and an Ed25519 signature) and is served as static files. Ship the Quran text pack **inside the APK**, if the licence allows, so the app works offline on first launch. |
| Search | Unspecified. | On device: Room FTS over a *normalized* shadow column. On the server: PG full-text search or `pg_trgm` over a normalized column. **[verify]** whether the chosen PG version includes an Arabic text-search configuration. Never normalize the display text. |
| Push | Not mentioned. | **[REC]** No push in v1. Content and blocklist updates use periodic WorkManager checks, and prayer notifications are local. This avoids a Firebase dependency. Revisit if a real need appears. [Q-DEF-06] |
| Redis | "Only if proven." | Agree. Rate limiting and idempotency can live in PG at first. |
| Observability | "Self-hosted" is required; no tool is named. | **[REC]** One self-hosted Sentry-compatible tool (for example GlitchTip) for backend and Android crashes, with strict scrubbing. Add Prometheus/Grafana or the host's own metrics later. [Q-DEF-07] |
| AI assistant | No provider or retrieval design. | Defer to Phase 6. Design it now as a separate context that reads only the **published** corpus, with pgvector or a separate index. Choose the provider later and send it no user data. [Q-DEF-08] |

## 4. Communication and authentication flow

1. The app calls Credential Manager with the Google ID option and gets a Google **ID token**, using a nonce created by the server.
2. The app sends `POST /api/v1/auth/google` with the ID token and the nonce.
3. The server verifies the token with the official Google library. It checks the signature, `iss`, `aud` (the server or web client ID), `exp` and the nonce. It then upserts a user keyed by `sub` and **does not store** the Google token.
4. The server issues a short-lived access token (≈ 10–15 min) and a **rotating** refresh token. Only a hash of the refresh token is stored in the `auth_sessions` table, so it can be revoked per device or for all devices. Reuse of a refresh token revokes the whole token family.
5. Every request derives `user_id` from the validated token claims. Client-supplied `user_id` is ignored (MP §6, SA).
6. Admin and CMS access is a separate role-based authorization layer (`cms_roles`) with MFA. It must not reuse consumer sessions without step-up authentication. [Q-ESS-06]

## 5. Data placement: local vs. server

| Data | Local | Server | Notes |
|---|---|---|---|
| Quran, tafsir, hadith, adhkar, library | ✔ (packs) | ✔ (source of truth plus CMS) | Public content |
| Blocklists | ✔ | ✔ (distribution) | Public |
| Settings, theme, language, calc method | ✔ | opt-in sync | Low sensitivity |
| Location | ✔ | opt-in, **city or rounded only** | Fixes C7 |
| Bookmarks, notes, reading position, memorization | ✔ | opt-in sync | Notes are personal → encrypt |
| Worship goals and progress | ✔ | opt-in sync | Religious practice is a special-category datum under GDPR-like laws |
| Alarm profiles | ✔ | opt-in | Low |
| Alarm sessions and wake signals | ✔ | **aggregate only**, opt-in | Never raw sensor data |
| Protection state and event log | ✔ | **No** by default | Revealing; local-only in v1 |
| Recovery plan, triggers, check-ins | ✔ (encrypted DB) | **only as end-to-end encrypted opaque blobs**, opt-in | Most sensitive data in the product |
| Identity | Session tokens (Keystore-wrapped) | `users`, `auth_sessions` | Minimal: Google `sub`, display name optional |

## 6. Sync, errors, retries, offline, conflicts and versioning

- **Sync protocol [REC]:**
  - Records have client-generated UUIDs.
  - The client keeps an outbox table and pushes with `Idempotency-Key`.
  - The server assigns a monotonically increasing `server_version` per row.
  - The client pulls with `?since=<cursor>`.
  - Deletes are tombstones, kept for N days.
  - Conflicts:
    - Settings: last-write-wins per field.
    - Bookmarks: set-union, with tombstones winning only if newer.
    - Progress counters: per-day max.
    - Recovery: E2E blobs, last-write-wins per record (the server cannot merge them).
- **API versioning:**
  - `/api/v1` with additive changes only.
  - Breaking changes go to `/api/v2`, and both versions run in parallel for at least one app-release cycle.
  - The client sends `X-App-Version`; the server can return `426` with a minimum version.
- **DB migrations:** use the chosen framework's migration tool (EF Core migrations, or Flyway or a TypeORM equivalent). `initial_schema.sql` becomes **reference only**, superseded by `V1__…` migrations. Room schema export is enabled, with migration tests.
- **Errors:** an RFC 9457 (problem+json) body carrying a stable `code` and a `correlationId` (SA requirement). The client maps codes to Arabic messages.
- **Retries:** exponential backoff with jitter for idempotent operations only; writes carry idempotency keys. WorkManager constraints handle the network.
- **Offline:** every read path serves local data first. The UI shows a stale-data badge with the last-sync time.

### Components that need automated tests

| Area | Tests |
|---|---|
| Prayer calculation | Golden tables |
| Qibla | Bearing calculation |
| Hijri | Conversion and offset |
| Content import | Counts, ordering, checksums, signature rejection |
| Arabic normalization | Normalization rules |
| Sync engine | Conflicts and tombstones |
| Alarm scheduler | Reboot, timezone, DST, permission-denied fallback |
| VPN DNS filter | Block, allow, exceptions, upstream failure |
| Auth | Token verification, refresh rotation and reuse detection |
| Authorization | Cross-user access attempts must return 404/403 |
| CMS | Role checks, rollback |
| Account deletion | Completeness |
| Logging | Redaction |
| UI | RTL, font scale, TalkBack on primary flows |

## 7. Database assessment (`database/initial_schema.sql`)

**Verdict:** a reasonable *seed*. It is **not** production-complete, and covers only about half of the entities the requirements imply. It is statically reviewed only; it has not been executed.

### 7.1 Defects in existing tables

| # | Table / column | Problem | Correction |
|---|---|---|---|
| D1 | `users.deleted_at` plus `google_subject UNIQUE` | Soft delete keeps the Google `sub` indefinitely, which conflicts with deletion and retention rules. The same user cannot re-register after deletion. | Hard-delete, or anonymize (null `google_subject`) after the grace period. Use a partial unique index `WHERE deleted_at IS NULL`, or purge. |
| D2 | All `updated_at` | No trigger maintains them. | Add a trigger, or set them in the ORM. Add `server_version BIGINT` for sync. |
| D3 | `user_settings.calculation_method`, `madhab`, `theme`, `language` | Free-text VARCHAR. | CHECK constraints or lookup enums. Validate `timezone` against the IANA list in the application. |
| D4 | `prayer_preferences.latitude/longitude` | Precise location on the server (C7). | Store `city_id` or rounded coordinates; opt-in only. |
| D5 | `prayer_preferences.notification_settings JSONB` | Unvalidated blob. | A JSON schema validated in the application, or normalized per-prayer rows. |
| D6 | `worship_progress UNIQUE(user_id, goal_id, local_date)` with nullable `goal_id` and `ON DELETE SET NULL` | NULLs are distinct in PG, so uniqueness is not enforced for NULL goals; deleting a goal leaves orphan rows. | Make `goal_id NOT NULL ... ON DELETE CASCADE`, or use `UNIQUE NULLS NOT DISTINCT` (PG 15+). |
| D7 | `worship_goals` | No `updated_at`, period/frequency, `archived_at` or title. | Add them. |
| D8 | `quran_surahs` | No source or revision; no metadata (revelation place, order, names). Ayah totals are not enforced. | Add metadata columns. Enforce 114/6,236 in an import-verification test (not a DB constraint). |
| D9 | `quran_ayahs` PK `(surah, ayah)` | Only **one edition** is possible; `source_id` is **nullable** (conflicts with "every item has a source"); no juz, hizb, page, sajda; no normalized search text. | Add `quran_editions(id, riwaya, script, source_id NOT NULL, checksum)`. Change the PK to `(edition_id, surah, ayah)`. Add a structural `quran_ayah_meta(surah, ayah, juz, hizb_quarter, page, sajda)`. Add a generated or separate `search_text` column. |
| D10 | `hadith_entries` | `collection` is free text; a single `grading` value; no chapter or bab; no isnad/matn split; no translation; no uniqueness; no editorial status. | Add `hadith_collections` and `hadith_chapters` tables, and a `hadith_gradings(hadith_id, grade, grader, source_id)` 1:N table. Add `UNIQUE(collection_id, hadith_number, source_id)` and a `status` column. |
| D11 | `tafsir_entries` | Per-ayah only, but tafsir often covers ayah ranges; no uniqueness. | Add `ayah_from`, `ayah_to`. Add `UNIQUE(source_id, surah, ayah_from, language)`. |
| D12 | `content_sources.license_info TEXT` | Licence terms are not machine-checkable (for example, whether offline redistribution or modification is allowed). | Add `license_code`, `allows_offline`, `allows_modification`, `attribution_text`, `license_url` and `verified_at`/`by`. |
| D13 | `user_bookmarks.note_encrypted`, `recovery_checkins.notes_encrypted` | **Key management is undefined.** If the server holds the key, the protection is weak; if the client holds it, the server cannot search these columns (acceptable). | Decide on client-side E2E for recovery (strongly recommended) and say so in the schema comments. See the security review. |
| D14 | `recovery_checkins.event_type`, `recovery_goals.goal_type` | Even with encrypted notes, plaintext `event_type` (for example "relapse") and its date leak the most sensitive facts. | Store recovery data as a **single opaque encrypted payload** per record: `(id, user_id, record_kind_opaque?, ciphertext, nonce, key_version, server_version)`. |
| D15 | `alarm_profiles` | No repeat days, label, snooze or escalation configuration, verification parameters or sound; `timezone` nullable. | Add these columns, or a validated JSON configuration. |
| D16 | `alarm_sessions` | **No FK to `alarm_profiles`**; no snooze count, signals summary or confidence score. | Add `profile_id` FK, `snooze_count`, `confidence NUMERIC(3,2)` and `signals_summary JSONB` (aggregate only). |
| D17 | `protection_profiles` | Purpose unclear. Server-side "protection enabled" for a user is sensitive. | Remove from the v1 server, or keep it only for managed-mode policy distribution. |
| D18 | `blocked_domains.domain` | No lowercase or IDN normalization constraint. Per-row storage of large lists makes distribution heavy. | `CHECK (domain = lower(domain))`, punycode normalization, and distribution through `content_releases` packs. |
| D19 | `content_releases` | Not linked to items; checksum algorithm unspecified; no status (draft, published, revoked); no signature. | Add `algorithm`, `signature`, `status`, `created_by`, and a manifest of pack files. |
| D20 | `audit_events` | No details JSON, IP or user agent; actor is a consumer `users` row (are admins also `users`?). | Add `details JSONB` (redacted) and an `actor_role`. Admin identity per [Q-ESS-06]. |
| D21 | Missing FK indexes | `worship_goals(user_id)`, `recovery_goals(user_id)`, `alarm_profiles(user_id)`, `protection_profiles(user_id)`, `tafsir_entries(surah_number, ayah_number)`, `hadith_entries` search. | Add these indexes. |
| D22 | `CREATE EXTENSION pgcrypto` | Only `gen_random_uuid()` is used, and it is built in since PG 13. | Pin a PG major version (≥ 15 recommended) and drop the extension if it is unused. Consider UUIDv7 for index locality. |
| D23 | One flat script | No migrations or versioning. | Convert to the migration tool's format (§6). |

### 7.2 Missing entities (required by the documented requirements)

| Entity | Required by |
|---|---|
| `auth_sessions` (refresh-token hash, device, created, last used, revoked) | MP §6 |
| `consents` (category, granted_at, revoked_at, policy_version) | AR §6, PR |
| `deletion_requests` / `data_exports` (status, timestamps) | PR Account |
| `idempotency_keys` | SA |
| `sync_tombstones`, or `deleted_at` plus `server_version` on every synced table | SA, sync |
| `cms_users` / `cms_roles` / `cms_user_roles` (editor, reviewer, admin) | PR CMS |
| `content_items` / `content_versions` (status draft→in_review→published→unpublished, reviewer, published_at, rollback pointer) | PR CMS, MP §5 |
| `quran_editions`, `quran_ayah_meta`, `quran_translations` | MP §5, PR Quran |
| `reciters`, `recitation_audio` (reciter, surah, ayah or file, URL, checksum, licence) | PR Quran, FR |
| `hadith_collections`, `hadith_chapters`, `hadith_gradings` | PR Hadith |
| `seerah_sections`, `stories`, `library_articles` (or a generic `articles` with a type) | MP §5, PR |
| `adhkar`, `adhkar_categories` (text, reference, repetitions, time-of-day) | PR Adhkar |
| `memorization_plans`, `memorization_reviews` | MP §5, FR |
| `reading_positions` | Home "continue reading" |
| `recovery_records` (E2E blob), `trusted_contacts` (if server-mediated) | PR Recovery |
| `devices` (for session listing and optional managed-mode policy) | MP §6 |
| `blocklist_releases` (or reuse `content_releases`) | PR Protection |
| `focus_sessions` (if synced) | FR focus |

### 7.3 Local (Room) schema

The local schema is **absent from the package**, and is the larger gap. Phase 0b must define:

- `content.db`: the Quran tables mirroring D8–D9 plus an FTS table; tafsir; hadith; adhkar.
- `user.db`: settings mirror, bookmarks, reading position, goals and progress, memorization, alarm profiles and sessions, outbox, sync cursor.
- `private.db`: recovery plan, triggers, check-ins and interventions; protection event log; exceptions.

### 7.4 Migration plan

1. Freeze `initial_schema.sql` as *reference*.
2. Write `V1` from the corrected design: schemas `identity`, `userdata`, `content`, `cms`, `ops`.
3. Seed content only through the verified import pipeline.
4. Add a CI job that applies migrations to an empty PG container and runs schema tests.

### Documents to update if these recommendations are accepted

- `architecture/SYSTEM_ARCHITECTURE.md`: modules, storage split, sync, content packs, push and observability.
- `database/initial_schema.sql`: mark as superseded, or replace it through migrations.
- `prompts/MASTER_BUILD_PROMPT_AR.md`: §3 decisions and §4 modules.
- `docs/ROADMAP_AND_ACCEPTANCE_AR.md`: content pipeline and alarm platform in Phase 2.
- `database/feature_registry.json`: missing features and fixed priorities.
