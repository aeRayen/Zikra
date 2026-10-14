# Phase 0 Audit — Open Questions and Missing Decisions

Questions fall into two groups:

- **Essential blockers (Q-ESS):** answers are needed before the phase noted.
- **Decisions with safe documented defaults (Q-DEF):** work can proceed using the proposed default unless the owner objects.

## A. Essential blockers

| ID | Question | Why it matters | Blocks | Auditor's proposed answer (for the owner to accept or change) |
|---|---|---|---|---|
| Q-ESS-01 | What are the **final app name, Android `applicationId`, and the Google Cloud / Play Console account** that will own the OAuth clients? | The package name and signing certificate are bound to the OAuth client and Play listing, and are hard to change later. The name is currently provisional (`README_AR.md`). | Phase 1 (sign-in) | Choose a permanent `applicationId` now, even if the display name changes later. |
| Q-ESS-02 | **Backend framework: ASP.NET Core or NestJS?** | Required by MP §3 and the handoff checklist. It determines migrations, the CMS approach and hiring. | Phase 1 | ASP.NET Core (LTS), unless the team is TypeScript-first. Rationale in `ARCHITECTURE_REVIEW.md` §3. |
| Q-ESS-03 | **minSdk and supported Android versions?** | Drives exact-alarm, notification, VPN and device-policy behaviour, and the test matrix. | Phase 1 | minSdk 26; targetSdk is the current Play requirement. |
| Q-ESS-04 | **Which licensed sources for the Quran text (riwaya and script), tafsir, translations, recitation audio, hadith collections and gradings, adhkar, and adhan audio?** Who verifies the licences? | MP §5 and the checklist forbid unverified or unlicensed content. Licences may forbid modification or offline redistribution. This determines the content-pack design. | Phase 2 (Quran text is needed by the Phase 1 skeleton only as sample fixtures) | Hafs 'an 'Asim, Uthmani script, from a source whose licence allows unmodified offline redistribution with attribution. Candidates such as Tanzil, KFGQPC and Quran Foundation **must have their licences verified**. Audio and hadith are decided separately. |
| Q-ESS-05 | **Hosting:** which server (provider, region, size), which domain, and where do off-site backups go? Is a staging environment required? | Needed for TLS, OAuth redirect and authorized domains, backup policy and data-residency law. | Phase 1 deployment (local Docker Compose development can proceed) | One VPS for production plus one for staging, and an S3-compatible off-site bucket in a different provider or region. |
| Q-ESS-06 | **How do CMS administrators, editors and reviewers authenticate?** Is it Google sign-in plus allow-list plus MFA, or a separate admin identity? | Content integrity (threat T4); schema for CMS users. | Phase 3 (the schema decision is needed in Phase 0b) | Google Workspace or Google sign-in restricted to an allow-list, with TOTP MFA, on a separate admin hostname. |
| Q-ESS-07 | **Protection governance:** who controls protection on an ordinary device (always the user)? Is a **self-imposed cooldown** before disabling acceptable? What, if anything, does a **trusted person** receive: protection-disabled events, nothing automatic, or user-sent messages only? | Ethics constraints in DS and AR §2; privacy; the Recovery–Protection interaction is unspecified. | Phase 4 and Phase 5 | The user always retains control. An optional user-configured cooldown with an emergency override. Trusted-person sharing only by explicit user action per event. Nothing is automatic. |
| Q-ESS-08 | **Recovery sync:** is end-to-end encryption with a user passphrase acceptable, given that losing the passphrase means losing synced recovery data? Or is recovery local-only in v1? | Determines whether `recovery_*` server tables exist at all. | Phase 5 (schema in 0b) | **Local-only in v1.** E2E-encrypted sync in a later release. |
| Q-ESS-09 | **Age policy:** is the app for adults only, or also for minors on family-managed devices? | Play Families policy, consent, the wording of recovery content, managed-mode design. | Phase 1 (store listing, onboarding) | Users aged 18+ for Recovery; general content for all ages. Managed mode deferred. |
| Q-ESS-10 | **Target launch countries and jurisdictions?** | Privacy law (GDPR or local equivalents for religious and sexual-behaviour data), default calculation method, content licensing territory, crisis resources. | Phase 1 (privacy design) and Phase 7 | — (owner must provide) |
| Q-ESS-11 | **Who is the religious content authority?** Is there a named reviewer or board, and what is the methodology for grading, disputed matters and stories? | PR requires human review before publishing, and MP §5 requires disagreements to be shown. Without reviewers, the Phase 3 acceptance criterion cannot be met. | Phase 3 (Phase 2 for adhkar) | — (owner must provide) |

## B. Decisions that can use documented defaults

| ID | Decision | Proposed default |
|---|---|---|
| Q-DEF-01 | DI framework | Hilt |
| Q-DEF-02 | Numerals in the Arabic UI | Arabic-Indic (٠١٢) by default, with a setting for Western digits. Times use the locale's format. |
| Q-DEF-03 | Hijri calendar method | Umm al-Qura (`HijrahDate`) with a user offset of ±2 days |
| Q-DEF-04 | iOS code sharing | Contract-only (OpenAPI) for now. Revisit KMP before Phase 8. |
| Q-DEF-05 | Initial Android module set | About 14 modules (see `ARCHITECTURE_REVIEW.md` §2), split as features grow |
| Q-DEF-06 | Push notifications | None in v1. Local notifications plus periodic WorkManager checks. |
| Q-DEF-07 | Crash and monitoring | A self-hosted, Sentry-compatible tool with strict scrubbing. Crash reporting is opt-in on first run. |
| Q-DEF-08 | AI provider | Deferred to Phase 6. Retrieval runs over the published corpus only. No user data is sent. |
| Q-DEF-09 | Focus "distraction reduction" | v1: a timer plus an optional DND toggle (user-granted). No app blocking. |
| Q-DEF-10 | `feature:sleep-insights` | Dropped from v1. Alarm response history lives inside smart-alarm. |
| Q-DEF-11 | Analytics | None. If needed later, opt-in, aggregate and self-hosted. |
| Q-DEF-12 | Mushaf page view in v1 | Ayah (reflowable) view first. Page view after its font and layout licence is confirmed. |
| Q-DEF-13 | Canonical phase numbering | Use the ROADMAP's Phases 0–8 (resolves C1). |
| Q-DEF-14 | Quran audio priority | Phase 2b, after text and bookmarks (resolves C2). |
| Q-DEF-15 | Filtering mechanism | Local VpnService DNS filter as the primary path, with a guided Private DNS alternative for users who already use another VPN. |
| Q-DEF-16 | Recovery and protection logs on the server | No (local-only) in v1 |
| Q-DEF-17 | Location precision stored on the server | City or rounded to about 0.1°, opt-in |
| Q-DEF-18 | Team size and timeline | Re-estimate after Phase 0b. The 18–25 week figure (RM) is not used as a commitment. |
