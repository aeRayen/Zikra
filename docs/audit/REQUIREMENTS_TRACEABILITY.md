# Phase 0 Audit — Requirements Traceability

Abbreviations for source files:

| Code | File |
|---|---|
| MP | `prompts/MASTER_BUILD_PROMPT_AR.md` |
| PR | `docs/PROJECT_REQUIREMENTS_AR.md` |
| AR | `docs/ANDROID_REALITY_AND_SECURITY_AR.md` |
| RM | `docs/ROADMAP_AND_ACCEPTANCE_AR.md` |
| SA | `architecture/SYSTEM_ARCHITECTURE.md` |
| SQL | `database/initial_schema.sql` |
| FR | `database/feature_registry.json` |
| DS | `design/DESIGN_SYSTEM_AR.md` |
| SVG | `design/concept_assets/*` |

Feasibility codes:

| Code | Meaning |
|---|---|
| **STD** | Standard Android app capability |
| **PERM** | Needs a runtime or special user-granted permission |
| **POL** | Needs a Google Play policy declaration or review |
| **VPN** | Needs VpnService or local filtering setup |
| **DO** | Needs Android Enterprise device-owner or managed provisioning |
| **EXT** | Needs an external service, data licence or provider |
| **NG** | Cannot be guaranteed |

**Implementation status of every row: NOT IMPLEMENTED.** No source code exists.

## 1. Requirements matrix

| # | Feature | Specification source | Dependencies | Feasibility | Missing details | Acceptance criteria (proposed, measurable) |
|---|---|---|---|---|---|---|
| 1 | Google Sign-In | MP §3, §6; PR §3 Account; FR authentication P0; RM Ph1 | Credential Manager; Google Cloud OAuth client IDs (Android plus web/server); backend token verification | STD + EXT | OAuth project owner; package name and signing SHA-1/256; which Google option (button vs. One Tap); scopes (minimum: openid) | Sign-in succeeds on a real device. The server rejects tokens with the wrong `aud`, wrong `iss`, expired tokens and replayed nonces. No Google token is stored after the exchange. |
| 2 | Session lifecycle | MP §6; SA API conventions; FR | Identity context; sessions table (**missing in SQL**) | STD | Token format; lifetimes; refresh rotation; revocation on all devices | Access token ≤ 15 min (proposed). Refresh rotation with reuse detection revokes the whole token family. Logout invalidates the session server-side. Integration-tested. |
| 3 | Guest mode, then account upgrade | PR §3 Account ("works without login") | Local-first data layer; sync merge | STD | Merge rules when an existing account already has data | All P0 features usable with no account and no network. The upgrade merge is tested with conflicting data. |
| 4 | Optional sync | PR §3; SA data principles; FR | Sync protocol (**unspecified**); consent record | STD | Which entities sync; conflict policy; tombstones; per-category consent | Two-device conflict tests pass. With sync off, zero user-data requests (verified by network log). |
| 5 | Data export | PR §3 Account; AR §6 | Export format; both local and server export | STD | Format (JSON/ZIP?); whether recovery data is included; encryption of the export | Export includes every synced category. A schema-validated file is produced. |
| 6 | Account deletion | PR §3; SA; AR §6; MP §6 | Hard-delete or anonymize job; backup retention window | STD + POL (Play requires in-app deletion **and** a web deletion path) | Retention window in backups; grace period; deletion of encrypted blobs | After deletion, no row references the user (automated query test). Purged from backups within the documented N days. A web request path exists. |
| 7 | Prayer time calculation | MP §5 Worship; PR §3 Prayer; FR P0 | Calculation library (candidate: an open-source Adhan implementation; **verify licence**); location | STD | Supported methods list; high-latitude rule; per-prayer minute adjustments; Asr madhab | Unit tests against a reference table, e.g. ≥ 20 cities × 365 days, within ±1 min of the chosen authority. The method in use is shown in the UI. |
| 8 | Location (manual or GPS) | PR §3 Prayer; AR | ACCESS_COARSE_LOCATION (fine is not needed); manual city DB | PERM | City database source and licence; whether geocoding is offline | Works with location denied (manual). Only coarse permission is requested. |
| 9 | Adhan and prayer notifications | MP §5; PR; FR | `platform:android-alarm`; POST_NOTIFICATIONS (API 33+); exact-alarm permission; adhan audio licence | PERM + POL + NG (OEM killers) | Adhan audio source; per-prayer sound; pre-prayer reminder; behaviour when exact alarms are denied | Fires within ±60 s on a reference device set after reboot, timezone change and Doze. Falls back to an inexact alarm plus a warning UI when permission is denied. |
| 10 | Timezone / DST / travel | PR §3 Prayer; AR §5 | TIME_SET / TIMEZONE_CHANGED receivers | STD | Travel detection (auto-update the location?) | Automated tests over DST transitions in ≥ 3 zones. |
| 11 | Qibla | MP §5; PR §3 Qibla; FR P0 | Rotation-vector or magnetometer; GeomagneticField declination | STD + NG (magnetic interference) | Map fallback (needs map tiles, an EXT dependency); accuracy indicator | The bearing calculation is unit-tested against known cities to ±0.5°. A calibration prompt appears at low sensor accuracy. Graceful UI when there is no sensor. |
| 12 | Hijri calendar | MP §2, §5 | Calculation choice (Umm al-Qura vs. other); user day-offset | STD | **Not in FR**; which calendar; manual ±1/2-day adjustment for local moon sighting | The adjustment persists. Dates are verified against a reference table. |
| 13 | Home widget(s) | MP §5 ("where supported") | Glance/AppWidget | STD | **Not in FR or RM**; which widgets | — |
| 14 | Quran text | MP §5; PR §3 Quran; FR P0; SQL quran_* | **Licensed source (undecided)**; import pipeline; checksum | EXT | Source (e.g. Tanzil / KFGQPC / Quran Foundation, **licence to be verified**); riwaya (Hafs assumed); script (Uthmani vs. simple) | Import check: 114 surahs and 6,236 ayahs (Hafs/Kufan count). The per-surah ayah counts match the reference. The SHA-256 of the canonical file matches. Displayed text is byte-identical to the source. |
| 15 | Mushaf page view | MP §5 ("pages or ayah view") | Page-layout data plus page-specific fonts (licence) | EXT | Whether the page view is in v1 (it is expensive) | — |
| 16 | Quran navigation and search | PR §3 Quran | Surah, juz, hizb and page metadata (**missing in SQL**); a normalized search column separate from display text | STD | Normalization rules (hamza, ta marbuta, diacritics) | Searching "الرحمن" finds ayahs regardless of diacritics. The display text is never altered. |
| 17 | Bookmarks and notes | PR; FR; SQL user_bookmarks | Local DB; optional sync | STD | Note encryption model | CRUD works offline and syncs without duplicates. |
| 18 | Recitation audio | MP §5; PR; FR quran P0 "audio" | **Audio licence**; MinIO or third-party CDN; Media3 foreground playback | EXT + POL (foreground-service type) | Reciters; hosting; download size; gapless ayah-by-ayah playback | **Not scheduled in RM Ph2 (conflict C2)** |
| 19 | Tafsir and translations | MP §5; PR; SQL tafsir_entries | Licensed tafsir sources; **translations table missing** | EXT | Which tafsir; ayah-range tafsir | Every entry shows its source and licence. |
| 20 | Memorization and review | MP §5; FR P1 | Plan model; spaced-review algorithm | STD | **No RM phase; no SQL table**; algorithm | — |
| 21 | Adhkar and counter | MP §5; PR §3; FR P1; RM Ph2 | Verified adhkar dataset with references (**no SQL table**) | EXT (content) | Dataset source; reminder schedule | Every dhikr shows its source reference. The counter persists across process death. |
| 22 | Worship goals and habits | PR §3 Goals; MP §2 "daily habits"; SQL worship_* | — | STD | "Habits" is undefined beyond worship goals | — |
| 23 | Hadith | MP §5; PR §3; FR P1; SQL hadith_entries | Licensed dataset; grading source(s) | EXT | Collections in v1; **multiple gradings (schema supports only one)**; isnad/matn split | Each hadith has a collection, number, source and ≥ 0 gradings, each with its grader. Disagreement is shown when gradings differ. |
| 24 | Seerah, stories, library | MP §5; PR §3 | Editorial content; **no SQL tables** | EXT (authoring) | Content source; authenticity policy for stories | Every item has a source and a reviewer before publishing. |
| 25 | Unified Arabic search | MP §5; SA | PG full-text search or trigram; local FTS4/5 in Room | STD | Server vs. on-device search; scope | p95 search < 300 ms on a mid-range device for the offline corpus (proposed). |
| 26 | Admin CMS | PR §3 CMS; FR P1; RM Ph3; SA | Roles, editorial status and version tables (**missing**); web UI (**tech unspecified**); admin auth | STD | Admin login method; MFA; frontend framework | Role tests: an editor cannot publish and a reviewer cannot manage users. Rollback restores the prior version. All actions are audited. |
| 27 | Content releases and distribution | SA; SQL content_releases | Signed packs; client verification | STD | Bundled vs. downloaded; signature scheme | The client rejects a pack with a bad checksum or signature (test). |
| 28 | Standard protection (filtering) | MP §5; PR §3; AR §1; FR P1; RM Ph4 | VpnService (local DNS) **or** guided Private DNS; blocklist source | VPN + POL + NG | Blocklist source and licence; VPN vs. Private DNS; DoH-bypass handling; SafeSearch enforcement | A blocked domain fails to resolve on a reference device. The status UI changes within 10 s when the VPN is revoked or another VPN starts. No claim of 100% blocking appears anywhere. |
| 29 | Protection status, event log, exceptions | PR §3; DS | Local log table (**not specified**) | STD | Exception UX; who may add exceptions; cooldown | — |
| 30 | Managed protection | MP §5; AR §3; FR P2; RM Ph4 (feasibility only) | DPC app; device-owner provisioning (factory reset or QR) | DO + POL | Who is the admin (parent, self, organisation)? Recovery/unenrol procedure | Feasibility spike report only in v1. |
| 31 | Recovery plan, triggers, interventions | MP §5; PR §3; FR P1; RM Ph5; SQL recovery_* | Encrypted local DB; app lock (proposed) | STD | Data model for plan, triggers and interventions (**SQL is minimal**) | Works fully offline. No recovery data leaves the device unless sync consent is on (network test). |
| 32 | Urgent help button | PR §3; SVG "Open quick support" | Content of the intervention flow | STD | Flow contents; crisis resources per country? | One tap from Home or Recovery reaches the intervention screen. |
| 33 | Trusted-person support | PR §3 ("with explicit consent") | Channel (share sheet? server relay?) | STD/EXT | **Entirely unspecified**: what is shared, how, revocation | Nothing is sent without per-event confirmation (proposed). |
| 34 | Progress reports (not streak-only) | PR §3 | — | STD | Metrics definition | — |
| 35 | Alarm scheduling | MP §5; PR §3; FR P1; SQL alarm_* | `platform:android-alarm`; exact-alarm permission; full-screen intent; boot receiver; direct-boot awareness | PERM + POL + NG | Repeat days; snooze; escalation; "emergency settings" | Rings within ±30 s after a reboot *before first unlock*, a timezone change, 8 h of Doze, and app force-stop recovery (documented). |
| 36 | Verification tasks | MP §5 (QR, math, motion) | CAMERA (QR, foreground only); accelerometer | PERM | QR registration; accessibility alternative for users who cannot do motion or math | Each task can be completed with TalkBack on (or an accessible alternative is offered). |
| 37 | Probabilistic wake inference | MP §5; PR §3; AR §5 | Signals: dismissal time, task completion, screen-on or unlock events, short-window motion, follow-up check-in | STD + NG | Model or formula; follow-up check-in timing; on-device only? | Shown as an estimate with its uncertainty. Sensor sampling only within a configured window (verified in the battery profile). |
| 38 | Sleep insights | MP §4 and SA module list only | — | — | **No requirement at all** | — |
| 39 | Focus sessions | MP §2; FR P2; RM Ph6 | DND access (ACCESS_NOTIFICATION_POLICY) and/or usage access, if "distraction reduction" is meant | PERM + POL | Mechanism | — |
| 40 | AI assistant (RAG) | MP §5; PR §3; FR P2; RM Ph6 | **LLM provider (unspecified)**; embeddings; reviewed corpus | EXT | Provider; self-host vs. API; cost; data processing agreement; Arabic quality evaluation | On a fixed evaluation set (e.g. 200 questions), 100% of answers cite retrievable sources and 0 fabricated hadith references. Questions outside the corpus are refused. |
| 41 | Arabic RTL, accessibility, theming | MP §7; DS; PR §4 | Design tokens (**missing**) | STD | Numeral policy; fonts; contrast targets | All screens pass Accessibility Scanner. RTL screenshot tests. 200% font scale without clipping. |
| 42 | Offline core | PR §4 | Bundled content | STD | Definition of "core" | Airplane-mode test of all P0 flows. |
| 43 | Backups (server) | MP §3, §6; RM Ph7 | Off-site storage; encryption keys | EXT | Target storage; RPO and RTO | A monthly restore drill is documented, and a restore succeeds. |
| 44 | Monitoring and crash reporting | MP §3, §6 | Self-hosted crash or APM tool | EXT | Tool choice; scrubbing rules | Scrubbing tests: no request bodies, notes or URLs in events. |
| 45 | Push notifications | **Not specified** | FCM (Google) or none | EXT | **Decision missing** | — |
| 46 | Analytics | **Not specified** (user brief: "if required") | — | — | Whether any analytics exist | Default: none, or opt-in aggregate only. |
| 47 | iOS | MP §2, §8; RM Ph8; FR P3 | Stable API contract | EXT | Shared-code strategy | — |

## 2. Contradictions and inconsistencies (reported, not resolved)

| ID | Conflict | Files | Impact | Suggested resolution |
|---|---|---|---|---|
| C1 | Phase numbering: MP §8 has 10 steps with the audit as step 1; RM has Phases 0–8 with the audit as Phase 0; the workflow uses roles and a 7-item feature order. | MP §8, RM, workflow | The agent and the owner may refer to "Phase 2" and mean different things. | Adopt RM numbering as canonical (the user's brief already calls this "Phase 0"). Update MP §8. |
| C2 | Quran **audio** is P0 in FR but is not in RM Phase 2 deliverables. | FR, RM Ph2 | Scope ambiguity in the first content phase. | Move audio to Phase 2b or mark it P1. Decide once the licence is resolved. |
| C3 | Dhikr is P1 in FR but sits in the P0 phase (RM Ph2, MP step 4, workflow step 2). | FR, RM, MP | Minor. | Align the FR priority to P0 or move dhikr later. |
| C4 | Memorization: P1 in FR and described in MP §5, but not in any RM phase. | FR, MP, RM | Feature risks being forgotten. | Place it in Phase 2c or 3. |
| C5 | `feature:sleep-insights` exists in the module lists, but there are no requirements and no FR entry; PR §5 excludes medical sleep diagnosis. | MP §4, SA, PR §5 | Undefined module. | Drop it from v1 or define it as "alarm response history" inside smart-alarm. |
| C6 | The Home concept shows Focus (P2/Ph6) and Smart alarm (Ph5) tiles, while "no fake buttons" applies to production. | SVG home, DS, MP §7 | Phase-2 Home would contain dead tiles. | Home cards are registered by feature modules only once implemented. |
| C7 | The architecture says prayer preferences are local-first; SQL `prayer_preferences` stores precise lat/long on the server. | SA, SQL | Unnecessary sensitive location on the server. | Sync the city or a rounded location only, opt-in. |
| C8 | The architecture separates content from user data; SQL mixes both in one schema. | SA, SQL | Blurs permissions and backup policy. | Use separate PG schemas (`content`, `identity`, `userdata`, `cms`) and separate DB roles. |
| C9 | PR requires showing grading disagreement; SQL has a single `grading` column. | PR, SQL | Cannot meet the requirement. | Add a `hadith_gradings` 1:N table. |
| C10 | PR and MP require CMS roles, review and rollback, and MP §6 requires session revocation; SQL has no roles, statuses, versions or sessions. | PR, MP §6, SQL | Schema is insufficient for P1 scope. | See `ARCHITECTURE_REVIEW.md` §7. |
| C11 | CMS is Phase 3, but the Phase 2 Quran and adhkar need a content import pipeline with source, licence and checksum. | RM, SA | Phase 2 has no sanctioned ingestion path. | Add a "content pipeline (CLI import and verification)" to Phase 1/2, separate from the CMS UI. |
| C12 | SVGs are English-first and LTR, while the product is Arabic-first and RTL. | SVG, MP §7, DS | Concepts cannot be followed literally. | Produce RTL Arabic phone-frame wireframes in Phase 0b. |
| C13 | The protection concept places Recovery on the protection screen; privacy rules say not to expose sensitive data by default. | SVG protection, DS, AR §6 | A privacy leak in shared-screen contexts. | Separate destinations plus an optional app lock. |
| C14 | MP §4 lists "platform modules for VPN, device admin, audio"; SA also adds `platform:android-alarm`. | MP §4, SA | Trivial. | Keep SA's list. |
| C15 | SA says "keep business rules platform-neutral", but the stack is Kotlin/Android only, with no shared-code strategy. | SA, MP §3 | iOS may duplicate the domain logic. | Decide on KMP vs. contract-only portability ([Q-DEF-04]). |
| C16 | RM estimates 18–25 weeks, but the team size is unstated and the scope is very large. | RM | Schedule risk. | Re-estimate after the decisions; consider cutting v1 scope. |
| C17 | The user brief item 5 mentions "habit tracking"; the docs only define worship goals. "Daily habits" (MP §2) is unspecified. | Brief, MP, PR | Ambiguous scope. | Define habits as generalized goals, or drop them. |

## 3. Missing specifications (cross-cutting)

1. OpenAPI contract: no endpoints exist anywhere (RM Phase 0 expects "initial API contracts").
2. Local Room schema: none, although local-first is the core strategy.
3. Sync protocol.
4. Content-pack format, signing and update flow.
5. minSdk and targetSdk; supported device matrix.
6. Design tokens and component library.
7. Notification channel inventory: prayer, adhan, alarm, reminders, protection status, sync.
8. Error-code catalogue (SA requires "stable error codes").
9. Retention schedule per data category.
10. Localization policy: numerals, Hijri/Gregorian display, string externalisation.
11. Measurable non-functional requirement targets: startup time, APK size, battery, crash-free rate.
12. Content governance: who reviews religious content, and to what methodology.

## 4. UI/UX coverage: existing concepts vs. required screens

The existing concepts (4) are Home, Quran page, Protection + Recovery (combined) and Smart alarm overview. All are landscape, dark-only and English-first (see `FILE_BY_FILE_REPORT.md`).

### Screens still needed (none have designs)

| Area | Screens |
|---|---|
| Onboarding | Welcome and language; privacy promise; location choice (manual vs. coarse GPS); calculation method; notification permission rationale; optional sign-in |
| Account | Sign-in; account and sync settings with per-category consent; export; delete account (with confirmation and retention explanation); sessions/devices |
| Prayer | Today's times; monthly table; method and madhab settings; per-prayer adhan settings; exact-alarm-denied banner; battery-optimization guidance |
| Qibla | Compass; calibration; no-sensor fallback; interference warning |
| Hijri | Calendar; day-offset adjustment |
| Quran | Surah, juz and page index; reader (ayah mode and page mode); search; bookmark list; tafsir/translation panel; audio player and downloads manager; reading settings (font size, theme); source/licence "about this text" |
| Memorization | Plan setup; daily review; progress |
| Adhkar | Category list; dhikr detail with reference; counter; reminders |
| Library | Hadith collection, chapter and detail (gradings, source); seerah and stories reader; unified search; favourites and notes |
| Protection | Setup with VPN consent explainer; status (active, paused, failed, VPN conflict, revoked); list source and update time; exceptions; local event log; limitations explainer; managed-mode explainer |
| Recovery | App lock; plan editor; trigger log; urgent-help flow (delay, breathe, change environment, alternative task, contact person); progress report; trusted-person setup and consent; relapse flow with non-shaming copy |
| Alarm | Alarm list; editor (time, repeat, sound, task, escalation, snooze); full-screen ringing; task screens (QR register and scan, math, motion); post-wake check-in; history and confidence explanation; permission and reliability diagnostics |
| Focus and AI | Focus session; AI chat with citations, source cards and an "outside corpus" refusal |
| Settings | Theme, language, numerals, notifications hub, privacy, about and licences |
| Admin CMS (web) | Login; content list by status; editor; review queue; diff and rollback; sources and licences; releases; audit log; users and roles |

### Required states for every data screen

Loading, Empty, Error with retry, Offline (stale-data badge), Permission denied (with a deep link to settings), Feature unavailable on this device, and Sync conflict (where relevant).

### Reusable components implied or needed

- From the SVGs: card, hero card, shortcut tile, list row with chevron, status card, primary CTA, progress bar, large time display, trend chart, disclaimer caption, mushaf page surface, action pill.
- Additionally needed: top app bar (RTL), bottom navigation (4–5 items) with a "More" hub, permission rationale sheet, banner (warning or info), empty-state illustration slot, skeleton loader, source/citation chip, grading badge, ayah number marker, audio mini-player, counter button, time picker (RTL), confirm dialog for destructive actions, and an app-lock screen.
