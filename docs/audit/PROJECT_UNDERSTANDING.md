# Phase 0 Audit — Project Understanding

Audit date: 2026-10-14
Scope: the whole contents of `Islamic_Life_OS_AI_Starter_Kit/` (16 files, ~70 KB).
Auditor role: architecture and readiness review only. **No application code was written, and no original file was modified.**

## Evidence legend

The audit labels its statements as follows:

- **[FACT]** — read directly in a named file of the package.
- **[VERIFIED]** — mechanically checked during the audit. Examples: JSON and SVG parsing, UTF-8 encoding, and contrast ratios.
- **[ASSUMPTION]** — inferred. It is not stated anywhere in the package.
- **[REC]** — a recommendation from the auditor. It is not a decision.
- **[Q]** — an open question for the project owner. See `OPEN_QUESTIONS.md`.

## Where each report section lives

| Section requested in the Phase 0 brief | File |
|---|---|
| A. Project Understanding | this file |
| B. File-by-File Audit | `FILE_BY_FILE_REPORT.md` |
| C. Requirements Matrix | `REQUIREMENTS_TRACEABILITY.md` |
| G. UI/UX Assessment (screen coverage) | `REQUIREMENTS_TRACEABILITY.md` §4, plus the SVG details in `FILE_BY_FILE_REPORT.md` |
| D. Architecture Assessment | `ARCHITECTURE_REVIEW.md` §1–6 |
| E. Database Assessment | `ARCHITECTURE_REVIEW.md` §7 |
| F. Security and Privacy Assessment | `SECURITY_AND_PRIVACY_REVIEW.md` |
| H. Risks and Blockers | `IMPLEMENTATION_READINESS.md` §2 |
| I. Missing Decisions | `OPEN_QUESTIONS.md` |
| J. Implementation Readiness | `IMPLEMENTATION_READINESS.md` §1 and §3 |
| K. Recommended Development Plan | `IMPLEMENTATION_READINESS.md` §4 |

---

## 1. What the package is

- **[FACT]** The package is a **specification and prompt kit** for an AI coding agent (`README_AR.md`). It contains only documentation: Markdown, one SQL reference schema, two JSON files and four SVG concept images.
- **[VERIFIED]** There is **no application source code** anywhere in the package: no Gradle project, no backend project, no tests and no CI configuration. The directory is **not a git repository**. The parent folder `Islamic App/` contains only this kit.
- **[FACT]** The name "Islamic Life OS" is a **working title** that may change (`README_AR.md`).
- Therefore **nothing is implemented**. Every feature named below is a documented intention only.

## 2. Product purpose

**[FACT]** The vision is in `docs/PROJECT_REQUIREMENTS_AR.md` §1. The app is meant to bring worship, Islamic knowledge, digital protection from pornography, recovery from harmful habits, and smart waking into one experience. It must respect privacy and the limits of the operating system.

It is **Arabic-first and RTL**, with English planned for later (`MASTER_BUILD_PROMPT_AR.md` §7). It targets **Android first**, with **iOS later** (`feature_registry.json`). It uses a **self-hosted backend** (`feature_registry.json` → `"deployment": "self-hosted backend"`).

## 3. Target users

**[FACT]** These are the personas in `PROJECT_REQUIREMENTS_AR.md` §2:

1. Users who want prayer times, Quran and adhkar.
2. Readers who want a mushaf, tafsir, hadith, seerah and search.
3. Users who want to reduce their exposure to pornography.
4. Users who want a personal recovery plan.
5. Users who want an alarm that requires extra proof of being awake.
6. Families or organisations that manage a dedicated device under legitimate device-management policies.

**[ASSUMPTION]** Persona 6 implies that **minors** may use managed devices. The package does not state an age policy. This affects Play Families policy, consent and legal requirements. See [Q-ESS-09].

## 4. Major modules, as one product

Sources: `MASTER_BUILD_PROMPT_AR.md` §4–5, `architecture/SYSTEM_ARCHITECTURE.md` and `database/feature_registry.json`.

| # | Module | Documented intent (summary) | Works offline? | Genuinely needs backend or external service |
|---|---|---|---|---|
| 1 | Prayer times, adhan, qibla, Hijri, adhkar | Choice of calculation method and madhab, per-prayer adhan, timezone and DST handling, compass qibla with calibration guidance, Hijri calendar, widgets | **Yes, fully.** Calculation is local. | Nothing. Optional sync of preferences. An optional city/geocoding lookup would need a service or bundled DB. |
| 2 | Quran | Verified, licensed text; page or ayah view; bookmarks; recitations; tafsir and translations; memorization plans; integrity checks | **Yes**, once content is bundled or downloaded | Content distribution (audio, tafsir packs); licensed upstream sources |
| 3 | Hadith, seerah, stories, library, search | Source and reference on every item, grading with disagreement shown, human editorial review, Arabic-normalized search | Downloaded packs can be read offline | Content catalog, CMS, release/versioning, editorial workflow |
| 4 | Digital protection | DNS or local-VPN filtering, status UI, domain lists with source and update time, optional **managed mode** via Android Enterprise, no 100% guarantee | Filtering is local; list updates need the network | Blocklist distribution; managed provisioning |
| 5 | Recovery | Optional plan, minimal trigger logging, urgent help, alternative tasks, trusted-person support with consent, progress not limited to streaks, local by default | **Yes.** Local-first by design. | Only opt-in sync (should be end-to-end encrypted, see the security review) |
| 6 | Smart alarm | Local scheduling, verification tasks (QR, math, motion), probabilistic wake inference from several signals, no covert mic or camera, robust to reboot, timezone and Doze | **Yes, must be.** | Nothing. Opt-in aggregate outcomes only. |
| 7 | Account and sync | Google sign-in via Credential Manager, server-side token verification, minimal profile, works without login, optional sync, export, deletion | Guest mode works offline | Identity, sessions, sync API |
| 8 | Backend and CMS | Modular monolith (ASP.NET Core **or** NestJS, still undecided), PostgreSQL, OpenAPI `/api/v1`, Docker Compose, TLS proxy, MinIO if needed, self-hosted monitoring, encrypted off-site backups; CMS with editor, reviewer and admin roles, versioning, rollback and audit | n/a | It *is* the backend |
| 9 | Focus and habits | Focus sessions, "optional distraction reduction", daily habits | Yes | No |
| 10 | AI assistant | Retrieval-grounded answers with citations, quote kept separate from generated summary, no fatwas, no use of sensitive data | **No** | LLM provider (unspecified) plus a retrieval index |
| 11 | UI and design system | Calm emerald, ivory and gold palette; light and dark themes; RTL; scalable fonts; customizable Home; loading, empty and error states | n/a | n/a |
| 12 | Security and privacy | Sensitive data local by default, opt-in sync, export and delete, no sensitive text in crash tools, encrypted backups, least privilege | n/a | Server hardening |

### How the modules interact (inferred from the documents)

- **Home** (`DESIGN_SYSTEM_AR.md`, "لوحة اليوم") collects data from Prayer (next prayer and countdown), Quran (continue reading and daily *wird*), Dhikr (time-appropriate adhkar), Smart Alarm (next alarm) and Focus/Habits (one card). **[REC]** Home therefore needs a *card-provider contract*. Each feature module registers a card only when it is implemented and enabled. Otherwise Home would show buttons that do nothing, which `MASTER_BUILD_PROMPT_AR.md` §7 forbids.
- **The alarm infrastructure is shared** between adhan notifications (Phase 2) and the smart alarm (Phase 5). Both need exact alarms, rescheduling after reboot and timezone changes, Doze handling and fallback behaviour. **[REC]** Build `platform:android-alarm` once, in Phase 2.
- **Protection ↔ Recovery.** The protection concept SVG links "Recovery plan →" from the protection status card. Trusted-person support (Recovery) could receive protection-disabled events. This interaction is **unspecified** and privacy-sensitive. See [Q-ESS-07].
- **Content pipeline ↔ every reader module.** Quran, tafsir, hadith, seerah, adhkar and blocklists all depend on `content_sources` and `content_releases` (sources, licence, revision, checksum). **[REC]** Ingesting content is a cross-cutting foundation. It must exist before Phase 2, even though the CMS is Phase 3.
- **The AI assistant depends on the library.** It retrieves only from the reviewed, published corpus that Phase 3 produces.
- **Identity ↔ everything that syncs.** Guest-to-account upgrade has to merge local data into the account. This is not specified.

## 5. Intended user experience (from the documents)

- Calm, premium and uncluttered; neither childish nor busy (`DESIGN_SYSTEM_AR.md`).
- Eight top-level areas: Today, Worship, Quran, Library, Protection, Recovery, Alarm, Settings. No more than about 4–5 go in the bottom bar; the rest go in a hub or "More" page.
- Permissions are requested at the moment they are needed, with an explanation. Recovery copy never uses shame or fear. Turning protection off must not be made deceptive.
- Offline and sync status are always visible. Sensitive data never appears on shared screens or in notifications by default.

## 6. Constraints that must be preserved (explicit in the package)

1. No promise that the app cannot be uninstalled, disabled or bypassed on ordinary devices (`ANDROID_REALITY_AND_SECURITY_AR.md` §2, `feature_registry.json` non_goals, `README_AR.md`).
2. No Quran or hadith text generated by an LLM (`MASTER_BUILD_PROMPT_AR.md` §5, Quran section).
3. No hidden camera or microphone use, and no covert monitoring (`PROJECT_REQUIREMENTS_AR.md` §5).
4. Sensitive data is local by default and synced only with explicit consent (`ANDROID_REALITY_AND_SECURITY_AR.md` §6).
5. No fake buttons, mock data or decorative screens in production builds (`MASTER_BUILD_PROMPT_AR.md` §7, `HANDOFF_CHECKLIST_AR.md`).
6. No secrets in Git; provide `.env.example` (`MASTER_BUILD_PROMPT_AR.md` §1.8).
7. One phase at a time; no claims that tests passed unless they were actually run (`AI_AGENT_WORKFLOW_AR.md`).
8. No medical diagnosis of sleep disorders (`PROJECT_REQUIREMENTS_AR.md` §5).

## 7. Overall verdict (summary)

The package is a coherent and ethically careful product vision, with unusually honest platform limits. As an implementation specification it is **thin**: about 2–6 KB per area. There is no API contract, no local (Room) data model, no screen inventory, no content-licensing decision, no backend-framework decision, no minimum SDK and no hosting plan. The SQL schema is explicitly a draft and covers about half of the required entities. The design concepts are English-language, LTR, landscape illustrations, not Arabic RTL phone screens.

**The package is suitable for starting Phase 0b** (decisions, contracts and data models) **and then Phase 1** (foundation), once the essential decisions in `OPEN_QUESTIONS.md` are answered. It is **not yet sufficient to implement Phase 2 content features**, because Quran, tafsir, hadith and audio licensing is undecided.
