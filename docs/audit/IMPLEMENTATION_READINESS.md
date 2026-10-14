# Phase 0 Audit — Implementation Readiness, Risks and Plan

## 1. Readiness verdict

| Area | Ready? | Reason |
|---|---|---|
| Product vision and ethics constraints | ✅ Yes | Clear, consistent and honest about platform limits |
| Requirements | ⚠️ Partial | Functional areas listed; no measurable non-functional targets; several features undefined (sleep insights, habits, trusted person, focus mechanism) |
| Architecture | ⚠️ Partial | Sound high-level choices; backend framework, minSdk, sync, content distribution, push, observability and CMS front end undecided |
| API contract | ❌ No | No endpoints exist. RM Phase 0 expects them. |
| Server data model | ⚠️ Draft | About 23 defects and about 18 missing entities (`ARCHITECTURE_REVIEW.md` §7) |
| Local data model | ❌ No | Absent |
| Design system | ❌ No | Direction only; no tokens or components. Concepts are English, LTR, landscape and dark-only. |
| Content licensing | ❌ No | No source selected or licence verified (blocks Phase 2) |
| Infrastructure | ❌ No | No host, domain, OAuth project or backup target |
| Codebase | — | **None exists.** Greenfield project; not a git repository. |

**Overall:** **not ready for feature implementation.** The project is ready to proceed to **Phase 0b (decisions and contracts)** right away. It can then start **Phase 1 (foundation)** once Q-ESS-01, 02, 03, 05, 09 and 10 are answered.

Phase 2 additionally requires Q-ESS-04 (content licences). Phase 3 requires Q-ESS-06 and Q-ESS-11.

## 2. Risks and blockers (ranked)

### Critical

| # | Finding | Impact | Resolution |
|---|---|---|---|
| R1 | **No content licensing decision** for the Quran text, audio, tafsir, hadith and adhkar (Q-ESS-04) | Cannot ship any core content legally or verifiably. Integrity requirements (PR, MP §5) cannot be tested. | Owner selects the sources. Legal verification of each licence. Record the result in `content_sources` with machine-readable licence flags. |
| R2 | **Recovery and protection data protection model undefined** (key management for `*_encrypted`, plaintext `event_type`, server `protection_profiles`, combined protection/recovery screen) | Exposure of the most sensitive data the app holds, with severe personal harm and legal liability | Local-only in v1. Encrypted `private.db`, app lock, backup exclusion. E2E-only design for future sync (Q-ESS-08, Q-DEF-16). |
| R3 | **No religious content governance** (Q-ESS-11) | Publishing wrong gradings or fabricated material is reputationally fatal for this product category. PR's review requirement cannot be met. | Name reviewers and a methodology before Phase 3. CMS enforces reviewer sign-off. |

### High

| # | Finding | Impact | Resolution |
|---|---|---|---|
| R4 | Exact-alarm, full-screen-intent and FGS policy restrictions plus OEM battery killers | Adhan and smart alarm unreliable or rejected by Play | Shared `platform:alarm` built early, permission-grant UX, fallbacks, real-device matrix including aggressive OEMs, Play declarations prepared |
| R5 | Play VpnService policy and DNS-filter bypasses (DoH, other VPN) | Rejection, or user disappointment from overpromising | Policy review before Phase 4. Honest status UI. SafeSearch and DoH-endpoint mitigations. Never claim completeness. |
| R6 | Backend framework, minSdk and hosting undecided (Q-ESS-02, 03, 05) | Phase 1 cannot start | Owner decisions (defaults proposed) |
| R7 | No API contract, sync protocol or local schema | Rework risk across every feature; sync bugs | Phase 0b deliverables |
| R8 | Server schema insufficient (sessions, roles, editorial status, gradings, editions) | Requirements unmet; later migrations painful | V1 migration from the corrected design (§7 of the architecture review) |
| R9 | Scope vs. the 18–25 week estimate with no stated team size | Schedule failure; pressure to cut quality gates | Re-plan after 0b. Treat Managed mode, AI, Focus and iOS as post-v1 candidates. |

### Medium

| # | Finding | Impact | Resolution |
|---|---|---|---|
| R10 | Design concepts are English, LTR, landscape and dark-only, with no tokens | Inconsistent UI; RTL bugs found late | Phase 0b: tokens plus Arabic RTL phone wireframes for P0 screens, light and dark |
| R11 | 31-module plan from day one | Build complexity, empty modules (contradicts MP §4) | Start with about 14 modules |
| R12 | Phase numbering and priority inconsistencies (C1–C5) | Miscommunication | Adopt RM numbering; fix FR priorities |
| R13 | Admin CMS front end and auth unspecified | Phase 3 delay; admin compromise risk | Q-ESS-06; server-rendered admin with MFA |
| R14 | No content pipeline before the CMS (C11) | Phase 2 imports content ad hoc and unverified | Command-line import plus verification tool in Phase 1/2 |
| R15 | Minors and jurisdictions undefined (Q-ESS-09, 10) | Legal and policy non-compliance | Owner input; privacy policy draft |
| R16 | AI provider unspecified | Privacy and cost unknown | Deferred (P2); design isolation now |

### Low

| # | Finding | Impact | Resolution |
|---|---|---|---|
| R17 | Unused `gold` gradient in the SVGs; chart baseline contrast 2.55:1 | Cosmetic and accessibility issues in the concepts | Fix in the new wireframes |
| R18 | `pgcrypto` possibly unnecessary; no PG version pinned | Minor | Pin PG ≥ 15 |
| R19 | `feature_registry.json` not referenced by MP and missing features | Drift between documents | Update it after the decisions |
| R20 | Push and analytics unspecified | Minor (defaults exist) | Q-DEF-06, Q-DEF-11 |

## 3. Exact prerequisites remaining before implementation

1. Owner answers to Q-ESS-01, 02, 03, 05, 09 and 10 (Phase 1). Q-ESS-04 is needed for Phase 2, and Q-ESS-06, 07, 08 and 11 for Phases 3–5.
2. A Google Cloud project with Android and web OAuth clients, created by the owner. **No credentials go in the repository.**
3. A git repository created, with a branch policy.
4. The Phase 0b deliverables (below), approved.

## 4. Recommended development plan

Phase numbering follows `ROADMAP_AND_ACCEPTANCE_AR.md`. Each phase ends with real build and test runs and a report in the template from `AI_AGENT_WORKFLOW_AR.md`.

### Phase 0b — Decisions and contracts (documentation only, no app code)

- **Depends on:** this audit, plus owner answers.
- **Deliverables:**
  - ADRs (architecture decision records) for every Q-ESS and Q-DEF item.
  - OpenAPI v1 draft: auth, me, settings, sync, content manifests, deletion and export.
  - Corrected server ERD plus the V1 migration design.
  - Room schemas for the three databases.
  - Sync protocol specification.
  - Content-pack specification (manifest, signature).
  - Notification channel inventory.
  - Error-code catalogue.
  - Retention schedule.
  - Design tokens (light and dark), type scale with chosen fonts, component inventory.
  - Arabic RTL phone wireframes for the P0 screens, with every state.
  - Updated `feature_registry.json` and roadmap as proposed edits for approval.
- **Tests:** the OpenAPI document lints cleanly (spectral); the ERD is reviewed.
- **Acceptance:** owner approval of the ADRs, the API and the wireframes.

### Phase 1 — Foundation

- **Depends on:** Phase 0b plus the Phase 1 decisions.
- **Deliverables:**
  - Android multi-module skeleton (about 14 modules) with convention plugins.
  - Compose theme from the tokens; RTL default.
  - Navigation shell: 4–5 bottom items plus a "More" hub.
  - Room databases (empty schemas, with migration tests), DataStore, network client with the error model, redaction logger.
  - Backend skeleton with the chosen framework: health endpoint, OpenAPI generation, PG migrations V1, `identity` module, Google token verification, sessions with refresh rotation.
  - Docker Compose (api, postgres, proxy) and `.env.example`.
  - CI: lint, unit tests, Android build, backend build, migration apply on an empty PG.
  - Content import and verification CLI (C11).
- **Tests:**
  - Unit tests for token verification (with mocked keys).
  - Refresh rotation and reuse detection.
  - Cross-user access test harness.
  - Room migration test.
  - RTL screenshot test of the shell.
- **Acceptance:**
  - CI green on a clean checkout.
  - Sign-in works end to end on a real device against local or staging.
  - Revoked sessions are rejected.
  - No secrets in git (secret scan passes).

### Phase 2a — Prayer, qibla, Hijri and the alarm platform

- **Depends on:** Phase 1.
- **Deliverables:**
  - Prayer calculation (library chosen after its licence is verified).
  - Settings screens, location (manual or coarse).
  - `platform:alarm` (exact, fallback, boot and direct boot, timezone and DST).
  - Adhan notifications.
  - Qibla with calibration.
  - Hijri calendar with offset.
  - Home card provider, with the Prayer card only.
- **Tests:** golden prayer tables; DST tests; qibla bearing tests; instrumented alarm reschedule tests; permission-denied UI tests.
- **Acceptance:** ±1 min against the reference tables; notification fires after a reboot before unlock on ≥ 3 reference devices including one aggressive OEM; all states implemented.

### Phase 2b — Quran (text first, then audio) and adhkar

- **Depends on:** Q-ESS-04.
- **Deliverables:**
  - Signed Quran content pack, bundled.
  - Surah, juz and page index.
  - Ayah reader with font scaling and night mode.
  - Normalized search.
  - Bookmarks and reading position (Home "continue reading" card).
  - Adhkar with references and a counter.
  - Then audio: Media3 playback, downloads manager.
- **Tests:** import verification (114 surahs, 6,236 ayahs, SHA-256); display text byte-identical to the source; search normalization tests; bookmark offline and sync tests.
- **Acceptance:** all checks pass in CI. Licence attribution is visible on the "about this text" screen.

### Phase 3 — Library and CMS

- **Depends on:** Q-ESS-06, Q-ESS-11.
- **Deliverables:**
  - CMS (roles, review workflow, versions, rollback, audit).
  - Hadith with multiple gradings, tafsir, seerah and stories.
  - Unified search.
  - Content releases distributed as packs.
- **Tests:** role-permission matrix; rollback; tampered-pack rejection; search performance.
- **Acceptance:** 100% of published items have a source, a licence and a reviewer. An editor cannot self-publish.

### Phase 4 — Standard protection, then a managed-mode spike

- **Depends on:** Q-ESS-07; Play policy review.
- **Deliverables:**
  - Local VPN DNS filter.
  - Signed blocklist packs.
  - Status UI with all states, plus revoke and conflict detection.
  - Exceptions, local event log, limitations explainer.
  - Managed-mode feasibility report only.
- **Tests:** block and allow tests; VPN revoke within 10 s; battery profile over 24 h; no DNS data leaves the device.
- **Acceptance:** documented limits; no "100%" claims in the UI or the store listing.

### Phase 5a — Recovery

- **Depends on:** Q-ESS-07, Q-ESS-08.
- **Deliverables:** encrypted `private.db`, app lock, plan, triggers, urgent help, alternative tasks, progress (not streak-only), trusted-person flow (user-initiated).
- **Tests:** backup-exclusion test; zero-network test; `FLAG_SECURE`; copy reviewed for non-shaming language.
- **Acceptance:** the security gates in `SECURITY_AND_PRIVACY_REVIEW.md` §7 (items 3 and 4) pass.

### Phase 5b — Smart alarm

- **Depends on:** Phase 2a's alarm platform.
- **Deliverables:** alarm list and editor; full-screen ringing; QR, math and motion tasks with accessible alternatives; post-wake check-in; probabilistic confidence presented as an estimate.
- **Tests:** reboot, timezone, Doze and permission matrix; sensor sampling only in the window (battery profile).
- **Acceptance:** rings on the full device matrix; the confidence UI always shows the uncertainty.

### Phase 6 — Focus and AI assistant

- **Depends on:** Q-DEF-08, Q-DEF-09; the Phase 3 corpus.
- **Tests:** an evaluation set (e.g. 200 questions) shows no fabricated references, citations always present, and refusal outside the corpus.

### Phase 7 — Hardening and release

- Security review, performance and battery work, a backup restore drill, the privacy policy and Data Safety form, licence audit, and a closed beta.

### Phase 8 — iOS

- Starts after API v1 is stable, with a re-evaluation of the protection and alarm capabilities.

## 5. Proposed immediate next step

Once the owner approves this audit, the owner answers the Phase 1 essential questions (Q-ESS-01, 02, 03, 05, 09, 10). Each has a proposed default in `OPEN_QUESTIONS.md`.

The agent then runs **Phase 0b**, which produces ADRs, the OpenAPI v1 draft, the corrected ERD and Room schemas, design tokens, and RTL wireframes. Phase 0b is documentation only, and the owner approves it before any application code is written.
