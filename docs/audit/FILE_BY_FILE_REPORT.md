# Phase 0 Audit — File-by-File Report

Every file listed below was opened and read in full during the audit.

Mechanical checks ([VERIFIED]):

- Both JSON files parse with `ConvertFrom-Json`.
- All four SVGs parse as well-formed XML.
- All 16 files are valid UTF-8, with no BOM and LF line endings.
- The SQL file was **reviewed statically only**. No PostgreSQL instance was available, and nothing was installed, so it has **not** been executed.

**Files that could not be inspected: none.**

Excluded context: `C:\Users\Administrator\Desktop\AGENTS.md` sits two directories above the kit. It describes an unrelated project ("SROX" Win32 migration) and was not treated as part of this package.

| # | Path | Size (bytes) |
|---|---|---|
| 1 | `README_AR.md` | 3,347 |
| 2 | `HANDOFF_CHECKLIST_AR.md` | 991 |
| 3 | `prompts/MASTER_BUILD_PROMPT_AR.md` | 16,329 |
| 4 | `prompts/AI_AGENT_WORKFLOW_AR.md` | 1,863 |
| 5 | `docs/PROJECT_REQUIREMENTS_AR.md` | 6,118 |
| 6 | `docs/ANDROID_REALITY_AND_SECURITY_AR.md` | 3,722 |
| 7 | `docs/ROADMAP_AND_ACCEPTANCE_AR.md` | 3,919 |
| 8 | `architecture/SYSTEM_ARCHITECTURE.md` | 2,384 |
| 9 | `database/initial_schema.sql` | 6,818 |
| 10 | `database/feature_registry.json` | 3,552 |
| 11 | `design/DESIGN_SYSTEM_AR.md` | 1,971 |
| 12 | `design/concept_assets/ASSET_INDEX.json` | 625 |
| 13 | `design/concept_assets/home_dashboard_concept.svg` | 2,777 |
| 14 | `design/concept_assets/quran_reader_concept.svg` | 2,537 |
| 15 | `design/concept_assets/protection_recovery_concept.svg` | 3,187 |
| 16 | `design/concept_assets/smart_alarm_concept.svg` | 2,677 |

---

## 1. `README_AR.md`

- **Purpose:** the kit's entry point. It lists the contents, explains how to use the kit with an AI agent, and records one key architecture decision.
- **Key decisions:**
  - Native Android with Kotlin and Jetpack Compose.
  - A single private-server **modular monolith**.
  - **PostgreSQL**.
  - Design all modules up front, but implement them in phases.
  - Never claim the app is undeletable.
  - Keep secrets out of the repository.
  - Work in phases, with approval before large changes.
- **Image note:** images shown earlier in a chat were *not* recoverable. The SVGs are original replacements, and the kit does not claim to contain those images.
- **Depends on:** every other file, which it indexes.
- **Problems:**
  - It describes all the files, but does not say which one wins when they conflict. See the contradictions in `REQUIREMENTS_TRACEABILITY.md` §2.
  - The working title "Islamic Life OS" is not final. This affects the Android `applicationId`, the Google OAuth client and the Play listing, which are hard to change later. See [Q-ESS-01].

## 2. `HANDOFF_CHECKLIST_AR.md`

- **Purpose:** an 11-item checklist for the AI agent.
- **Requirements it adds:**
  - Choose one backend technology and justify it.
  - Create `.env.example`.
  - Document the build and test commands.
  - No fake buttons.
  - No unverified Quran or hadith data.
  - Test permissions, privacy and backup.
  - One phase at a time.
- **Depends on:** the Master prompt, Roadmap and Requirements.
- **Problems:**
  - "Tested backup" is not defined. It could mean Android Auto Backup, user-initiated export, server backup/restore, or all three.
  - All checklist items are unchecked, which is consistent with nothing having been implemented.

## 3. `prompts/MASTER_BUILD_PROMPT_AR.md`

This is the largest and most authoritative document.

- **Purpose:** the main instructions for the AI agent. Sections §1–§9 cover rules, vision, technology, Android modules, functional modules, server security, UX rules, the 10-step plan, and the current task.
- **Important requirements and decisions:**
  - §1: audit first; no "done" without tests; no giant changes; ask before destructive changes; record assumptions; no secrets; use stable, verified APIs; write a report per phase.
  - §3, Android:
    - Kotlin, Compose with Material 3, Clean Architecture, multi-module Gradle.
    - Coroutines and Flow; Hilt *or* another single DI solution.
    - Room, DataStore, Retrofit/OkHttp, WorkManager.
    - AlarmManager for exact alarms.
    - Android Keystore.
    - Credential Manager with server-side token verification.
    - JUnit, Compose UI tests and integration tests.
  - §3, Backend:
    - **ASP.NET Core *or* NestJS**, choose one; PostgreSQL; REST with OpenAPI; Docker Compose; TLS reverse proxy.
    - MinIO if needed; Redis **only if proven necessary**; a background worker.
    - Self-hostable monitoring; encrypted off-site backups with restore tests.
  - §4: a list of 27 named modules, plus "separate platform modules" for VPN, device administration and audio. It warns: *do not create empty modules with no value*.
  - §5: detailed module rules (summarised in `PROJECT_UNDERSTANDING.md`).
  - §6: 14 server security rules, including short-lived sessions with revocation, never trusting a client-supplied `user_id`, rate limiting, least privilege, no public DB or Redis, an admin audit log without private text, and crash monitoring without sensitive content.
  - §7: UX rules. Arabic RTL first; emerald, dark, ivory and limited gold; light and dark themes; font scaling; a customizable Home; eight navigation areas; loading, empty and error states with retry; no decorative images replacing content; no colours that make uncertain data look certain.
  - §8: a 10-step plan (step 1 is the audit).
  - §9: do Phase 1 (the audit) only, then wait for approval.
- **Depends on:** all docs, `SYSTEM_ARCHITECTURE.md`, `initial_schema.sql` and `DESIGN_SYSTEM_AR.md`. It does **not** reference `feature_registry.json` or the SVG assets.
- **Problems and ambiguities:**
  1. The phase numbering (§8: 1–10) conflicts with the Roadmap (0–8). See C1.
  2. The backend framework is not decided ("choose one").
  3. The AI assistant is specified behaviourally, but the stack names **no LLM provider, hosting model, embedding or retrieval store, or cost/privacy model**.
  4. "Optional distraction reduction" for Focus, "habits", "widget where supported", and "Accessibility" all appear without a mechanism or permissions.
  5. It names `feature:sleep-insights`, but no document specifies what that module does.
  6. **Push notifications are not mentioned anywhere.** FCM vs. none vs. self-hosted is undecided.
  7. **minSdk, targetSdk and supported Android versions are not given.** This is critical for exact alarms, notifications, VPN and device-policy APIs.
  8. Admin CMS technology (web frontend?) and admin authentication are unspecified.

## 4. `prompts/AI_AGENT_WORKFLOW_AR.md`

- **Purpose:** a role-based workflow: Architect → Foundation → Feature implementation (7 ordered units) → Quality gate. It also provides a phase-report template.
- **Requirements:**
  - Foundation must include CI build and tests, and the Docker Compose and backend skeleton.
  - The feature order is: Prayer/Qibla → Quran/Dhikr → Library → Protection → Recovery → Smart alarm → AI.
  - Quality gate per unit: unit, integration and UI tests; offline, error and permission states; a privacy review; battery checks.
- **Depends on:** Master prompt and Roadmap.
- **Problems:**
  - Focus, memorization, the Admin CMS and managed protection are absent from the feature order.
  - "Protection" is not split into standard and managed.

## 5. `docs/PROJECT_REQUIREMENTS_AR.md`

- **Purpose:** the product requirements document.
  - §1 vision; §2 six personas.
  - §3 functional requirements for Account, Prayer, Qibla, Quran, Hadith/Seerah/Stories, Adhkar/Goals, Protection, Recovery, Smart alarm, AI assistant and CMS.
  - §4 non-functional requirements.
  - §5 items out of scope for v1.
- **Important requirements:**
  - Works without login.
  - Export and account deletion.
  - DST and timezone handling.
  - Show the calculation method in use.
  - Qibla manual or map fallback.
  - **Automated verification of surah and ayah counts, ordering and import integrity.**
  - Hadith grading with disagreements shown.
  - Human review before publishing.
  - Declared, limited protection exceptions.
  - A **local** protection event log.
  - An urgent-help button.
  - Progress that is not just streaks.
  - Trusted-person support with consent.
  - Alarm escalation, snooze and emergency settings.
  - CMS roles editor, reviewer and admin, with rollback.
- **Non-functional requirements:** RTL, mid-range device performance, core functions offline, accessibility, low battery use, encryption, iOS without a server rebuild, clear retention and sync policies.
- **Depends on:** this is the source of truth for the traceability matrix.
- **Problems:**
  - No measurable non-functional targets (cold-start time, APK size, battery budget, API latency, minimum device spec).
  - No personas for the **admin, editor or reviewer** (CMS users), even though the CMS is P1.
  - "Stories" (القصص) has no sourcing standard.
  - "Emergency settings" (إعدادات الطوارئ) for the alarm is undefined.
  - "Trusted person" has no defined data scope, channel or consent-revocation flow.
  - No age or minor policy.
  - No list of supported regions or languages for v1, beyond Arabic first.

## 6. `docs/ANDROID_REALITY_AND_SECURITY_AR.md`

- **Purpose:** platform honesty and privacy rules. Its sections cover standard filtering (§1), the limits of an ordinary app (§2), managed mode (§3), Accessibility (§4), alarm and sensors (§5), privacy (§6) and server security (§7).
- **Key statements:**
  - A VPN may conflict with another VPN.
  - Encrypted traffic cannot be classified.
  - An ordinary app cannot stop being disabled, uninstalled, factory-reset or network-switched.
  - Managed mode may need a device wipe or fully-managed provisioning, plus clear recovery procedures.
  - Lock Task Mode "is not magic".
  - Accessibility only for an allowed core function, with explicit consent.
  - Check exact-alarm permissions; test Doze, reboot and timezone changes.
  - Motion alone does not prove wakefulness.
  - Do not sample sensors all night.
  - Export and delete; state how long backups are retained.
- **Depends on:** Requirements, Master prompt §5 and §6.
- **Problems:**
  - It correctly flags limits, but does not name the **concrete Android permissions or policy declarations** involved: exact alarms, full-screen intents, foreground-service types, notifications, the VPN declaration, and activity recognition. `SECURITY_AND_PRIVACY_REVIEW.md` §4 adds this mapping.
  - It does not mention DNS-over-HTTPS or Private DNS bypass of DNS-based filtering, or that only one VPN can be active at a time (it says "may conflict"; the reality is that it is exclusive).

## 7. `docs/ROADMAP_AND_ACCEPTANCE_AR.md`

- **Purpose:** Phases 0–8 with deliverables and acceptance criteria, a time estimate, and general quality criteria.
- **Key items:**
  - Phase 0 deliverables: requirements report, risk register, module and data diagrams, **initial API contracts**, initial UX model.
  - Phase 1 acceptance: successful build, basic tests, secure sign-in.
  - Phase 2: tests on **real devices**.
  - Phase 3: every published item has source metadata and review.
  - Phase 4: documented limits, no claims of absolute blocking.
  - Phase 5: reboot, permission, power and privacy tests.
  - Phase 6: sample-question evaluation of the AI.
  - Phase 7: security review, battery tests, backup restore.
  - Phase 8: iOS.
  - Estimate: **18–25 weeks with a suitable team**, explicitly not a commitment.
- **Problems:**
  - Acceptance criteria are qualitative, not measurable. For example, "test the calculations" has no reference dataset or tolerance.
  - Memorization, sleep insights and the admin CMS frontend are not placed in any phase.
  - The estimate does not state team size or composition. For this scope (Android app with about 30 modules, backend, CMS web app, content curation, managed-device DPC, AI RAG) it looks optimistic unless the team is fairly large. **[ASSUMPTION]**
  - Phase 0 requires "initial API contracts" and an "initial UX model". This audit gives only outlines. Producing them is proposed as Phase 0b.

## 8. `architecture/SYSTEM_ARCHITECTURE.md`

- **Purpose:**
  - Architecture decision: a modular Android app plus a modular-monolith backend, with no microservices.
  - Android layers: Presentation, Domain, Data, Platform adapters, Shared core.
  - A suggested module list of 27 modules plus 4 `platform:*` modules, 31 in total.
  - Nine backend bounded contexts.
  - API conventions: `/api/v1`, OpenAPI, server-side validation, identity from session claims, idempotency keys, pagination, stable error codes and correlation IDs, no sensitive bodies in logs.
  - Data principles: content separate from user data; local-first; minimal sync; content source, licence, revision and checksum; deletion and retention designed in from day one.
  - iOS portability.
- **Depends on:** Master prompt §3–4; schema.
- **Problems:**
  1. The sync protocol (conflict resolution, tombstones, versions) is not specified, although sync is P0 scope.
  2. There is no content-distribution design (bundled DB vs. downloadable signed packs vs. API pagination).
  3. "Platform-neutral business rules" sits awkwardly with a Kotlin-only stack. Kotlin Multiplatform is not mentioned. See [Q-DEF-04].
  4. Thirty-one modules from day one conflicts with Master prompt §4's warning against empty modules. **[REC]** Consolidate at the start (see `ARCHITECTURE_REVIEW.md` §2).
  5. Admin CMS hosting and frontend are not described.
  6. Observability, push and AI provider are not described.

## 9. `database/initial_schema.sql`

- **Purpose:** a reference PostgreSQL schema, explicitly marked *"Initial reference schema only. Review, normalize, and migrate before production."*
- **Contents:**
  - Extension `pgcrypto`.
  - 19 tables: `users`, `user_settings`, `prayer_preferences`, `worship_goals`, `worship_progress`, `content_sources`, `quran_surahs`, `quran_ayahs`, `hadith_entries`, `tafsir_entries`, `user_bookmarks`, `recovery_goals`, `recovery_checkins`, `alarm_profiles`, `alarm_sessions`, `protection_profiles`, `blocked_domains`, `content_releases`, `audit_events`.
  - 5 indexes.
- **Good decisions:**
  - UUID keys.
  - `TIMESTAMPTZ` columns.
  - Latitude and longitude CHECK constraints.
  - Surah number range CHECK.
  - Composite primary key for ayahs.
  - Tafsir foreign key to the ayah.
  - Encrypted note columns.
  - A comment that recovery sync is opt-in only.
  - Content source and revision columns.
  - Release checksum.
  - `ON DELETE CASCADE` from `users`.
- **Depends on:** `SYSTEM_ARCHITECTURE.md` data principles; Requirements.
- **Problems:** there are about 40 findings, listed in full in `ARCHITECTURE_REVIEW.md` §7. The most important:
  - No sessions or refresh tokens, roles, editorial status, translations, seerah or stories, recitations, adhkar, memorization, trusted contacts, consent records, sync metadata or idempotency.
  - A single `grading` column contradicts the requirement to show grading disagreement.
  - Precise server-side coordinates contradict the local-first rule.
  - The meaning of `*_encrypted` columns depends on key management, which is undefined.
  - `worship_progress` uniqueness is broken when `goal_id` is NULL.
  - `alarm_sessions` has no link to `alarm_profiles`.
  - Only one Quran edition is possible.
  - `users` uses soft delete while also keeping a `google_subject` UNIQUE constraint.
  - No migration tooling.

## 10. `database/feature_registry.json`

- **Purpose:** a machine-readable list of features with priorities.
  - P0: authentication, home, prayer_times, qibla, quran.
  - P1: dhikr, hadith_tafsir_seerah, memorization, protection_standard, recovery, smart_alarm, admin_cms.
  - P2: protection_managed, focus, ai_assistant.
  - P3: ios.
  - It also lists non-goals.
- **Depends on:** Requirements, Roadmap.
- **Problems:**
  - Quran P0 includes **"audio"**, but Roadmap Phase 2 does not schedule audio (C2).
  - Dhikr is P1 but scheduled alongside the P0 work (C3).
  - Missing entries: `hijri_calendar`, `adhan audio`, `widgets`, `settings`, `onboarding`, `sleep_insights`, `islamic_library/stories`, `habits`, `data_export`, `notifications`, `trusted_support`, `content_pipeline`.
  - Despite the folder name, this is a product file, not database content.
  - Nothing references it (`MASTER_BUILD_PROMPT_AR.md` never mentions it).

## 11. `design/DESIGN_SYSTEM_AR.md`

- **Purpose:** the visual direction, an 8-item navigation proposal, the Home dashboard content, and UX rules.
- **Key items:**
  - Calm and premium; emerald, dark, ivory and limited gold; light and dark themes; simple cards.
  - RTL by default; test numerals, dates and punctuation; scalable Arabic type.
  - Do not put 8 items in the bottom bar.
  - Home: next prayer and countdown, Quran and wird, time-based adhkar, next alarm, one focus or habit card. No sensitive data on shared screens or in notifications by default.
  - UX rules: just-in-time permissions; no fear or shame; no deceptive account recovery or protection-disable flows; visible offline and sync state; loading, empty and error states; screen reader support; avoid heavy ornament.
- **Problems:** it is a direction, not a design system. It has:
  - **No tokens**: no hex palette, no type scale, no font families (Arabic UI font or licensed Quran font), no spacing scale, no corner radii, no elevation.
  - No component inventory.
  - No light-theme palette, although the SVGs imply a dark palette.
  - No numeral policy (Arabic-Indic ٠١٢ vs. Western 012).
  - No minimum touch-target or contrast targets.
  - No iconography or motion guidance.

## 12. `design/concept_assets/ASSET_INDEX.json`

- **Purpose:** indexes the four SVGs and states that they are "conceptual editable SVG assets… not screenshots from a running app". It also says the Quran placeholder must be replaced with verified, licensed content.
- **Problems:** none in the file itself. All four indexed files exist and parse **[VERIFIED]**.

## Notes common to all four SVGs [VERIFIED]

- **Shared template:**
  - The canvas is **1200×800 landscape**, not a phone portrait frame.
  - Background gradient `#071f1a → #123d31`.
  - Two decorative gold circles at 5–6% opacity.
  - A gold diamond motif at 18% opacity.
  - Header "ISLAMIC LIFE OS • CONCEPT DESIGN".
  - Footer "Concept asset — not a production screenshot".
- A `<linearGradient id="gold">` is defined in every file but **never referenced** (0 occurrences of `url(#gold)`).
- **Language:** across all four files there are 42 text strings. Only **6 are Arabic**: "السلام عليكم", "سورة الفاتحة" and 4 ayah lines. Everything else is **English**.
- **Layout direction:**
  - All text uses `x`-left anchoring, which is an **LTR layout**.
  - There are no `direction="rtl"` or `xml:lang` attributes.
  - The arrows "→" ("Continue reading →", "Filtering settings →", "Recovery plan →") point in the LTR forward direction. In RTL they should mirror.
- **Fonts:** `Arial` and generic `serif`. Neither is a suitable or licensed Quranic font, and Arial's coverage of Quranic marks such as U+0670 (superscript alef) varies between renderers.
- **Theme:** only the dark theme is shown.
- **Contrast** (WCAG 2.x, computed) **[VERIFIED]**:
  - Body text pairs range from **4.71:1 to 13.09:1**, all passing AA for normal text. Examples: `#a7c1b4` on `#123d31` = 6.29; `#6b6b53` on `#f4eedf` = 4.71, which is the closest to the limit; `#10271f` on `#d0b36b` = 7.76.
  - Card borders: `#557969` on `#071f1a` = 3.55, which passes the 3:1 non-text minimum.
  - **The chart baseline in the smart-alarm SVG (`#547667` on `#15382d`) = 2.55 and fails WCAG 1.4.11 (non-text contrast, 3:1).**

## 13. `design/concept_assets/home_dashboard_concept.svg`

- **What it shows:**
  - Hero text "Your day, with purpose" / "Prayer • Quran • Focus • Wellbeing".
  - A large panel with "السلام عليكم" and "YOUR DAILY OVERVIEW".
  - A **Next Prayer** card ("Maghrib · 18:12", hard-coded sample).
  - A **Daily Quran** card ("Continue reading →").
  - Three small cards with labels only: Adhkar, Focus session, Smart alarm.
- **Implied components and tokens:**
  - Dashboard card.
  - Hero card with label, value and CTA.
  - Small shortcut tile.
  - Surface colours `#0a2a23`, `#174538`, `#263f32` and `#17372e`.
  - Accent label colour `#e7d39b`; primary text `#fff8e8`.
  - Radii of 18, 20 and 28.
- **Gaps relative to the requirements:**
  - No countdown to the next prayer, although `DESIGN_SYSTEM_AR.md` requires one.
  - No Hijri date or location label.
  - No sync or offline indicator.
  - No "customize cards" affordance, although the Home must be customizable.
  - No bottom navigation.
  - No next-alarm time on the alarm tile.
  - Focus is shown although it is P2 / Phase 6 (see C6).
  - Prayer names are in English.

## 14. `design/concept_assets/quran_reader_concept.svg`

- **What it shows:**
  - An ivory page (`#f4eedf`) with dark-green Arabic text (`#193e2f`, contrast about 10.5:1).
  - The surah title "سورة الفاتحة".
  - The first four lines of al-Fatiha with diacritics, centred.
  - A disclaimer: "Illustrative layout only — use a verified licensed Quran source".
  - An action pill: "Bookmark • Tafsir • Listen".
- **Implied components:**
  - Mushaf page surface; surah header.
  - Ayah line.
  - Action bar: bookmark, tafsir, audio.
- **Gaps:**
  - No ayah-number markers (﴿١﴾).
  - No page, juz or hizb indicator.
  - No navigation to the surah index or search.
  - No audio player state.
  - No night-mode version of the page.
  - Font-size control not shown.
  - No translation or tafsir panel.
- **Note:** the ayah text inside the SVG was hand-typed. It **must not be reused as a data source** (ASSET_INDEX already says so).

## 15. `design/concept_assets/protection_recovery_concept.svg`

- **What it shows:**
  - Left panel:
    - A shield icon.
    - "Protection status — Active • Last checked just now".
    - Rows "Filtering settings →" and "Recovery plan →".
    - "Private by default • No shame-based messaging".
  - Right panel:
    - "TODAY'S PLAN — Pause. Breathe. Choose."
    - A progress bar filled to about **61%** (245/400 px), with no label or meaning given.
    - "One small step at a time".
    - A gold CTA, "Open quick support" (contrast 7.76:1).
- **Implied components:**
  - Status card with an icon and a state line.
  - List row with a chevron.
  - Progress bar.
  - Primary gold CTA.
- **Gaps and concerns:**
  1. It puts protection and recovery on **one screen**. Recovery is the most sensitive data in the product. **[REC]** Keep these as separate destinations, optionally behind an app lock, so that opening Protection in front of others does not reveal Recovery.
  2. The progress bar's semantics are undefined. This conflicts with "progress not reduced to streaks" and with Master prompt §7 ("don't make uncertain data look certain").
  3. There are no paused, failed, VPN-conflict or permission-revoked status variants. Only "Active" is shown.
  4. Exceptions, event log, list source and update time (required by the Requirements) are not shown.

## 16. `design/concept_assets/smart_alarm_concept.svg`

- **What it shows:**
  - "NEXT ALARM 06:30 — Tomorrow · Adaptive wake-up".
  - A "VERIFICATION TASK — Scan your QR code" card.
  - "RECENT RESPONSE", a 7-point sparkline with **no axes, units or labels**.
  - The caption "Wake confidence is an estimate, not certainty."
- **Implied components:**
  - Large time display.
  - Task card.
  - Mini trend chart.
  - Disclaimer caption.
- **Gaps:**
  - No alarm list or editor.
  - No full-screen ringing UI.
  - No QR registration flow.
  - No permission or exact-alarm-denied state.
  - The chart has no meaning, and its baseline fails contrast (2.55:1).
  - "Adaptive wake-up" is not defined anywhere in the requirements.
