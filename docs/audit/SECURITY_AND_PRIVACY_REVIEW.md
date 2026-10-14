# Phase 0 Audit — Security and Privacy Review

Sources: `docs/ANDROID_REALITY_AND_SECURITY_AR.md` (AR), `prompts/MASTER_BUILD_PROMPT_AR.md` §5–6 (MP), `docs/PROJECT_REQUIREMENTS_AR.md` (PR), `architecture/SYSTEM_ARCHITECTURE.md` (SA), and `database/initial_schema.sql` (SQL).

Android and Play policy statements reflect platform behaviour known to the auditor. Items marked **[verify]** must be checked against the current official documentation at implementation time, as MP §1.9 requires.

## 1. Sensitive data inventory

| Data | Sensitivity | Why |
|---|---|---|
| Recovery plan, triggers, relapses, check-ins | **Critical** | Sexual-behaviour data (special category under GDPR-like laws). Exposure could cause social or family harm. |
| Protection state, event log, exceptions, blocked-domain hits | **Critical** | Reveals the user's struggle and browsing behaviour. |
| Trusted-person identity and messages | High | A third party's personal data, plus a relationship that reveals the user's situation |
| Precise location | High | Movement and home inference |
| Religious practice (prayer tracking, goals, memorization) | High in some jurisdictions | Religious belief is special-category data |
| Alarm and wake signals (sensor-derived) | Medium | Sleep and behaviour patterns |
| Notes on bookmarks | Medium | Free text |
| Google identity (`sub`, display name) | Medium | Personal identifier |
| Admin and CMS accounts | High (integrity) | Compromise lets an attacker alter religious content, which affects trust and reputation |

## 2. Threat model (realistic)

| # | Threat | Asset | Mitigation |
|---|---|---|---|
| T1 | A family member or friend picks up the unlocked phone | Recovery, protection log | Optional **app lock** for Recovery and Protection settings (BiometricPrompt or device credential). Neutral notification text. No sensitive Home cards by default (DS). Separate destinations (fixes the combined SVG). |
| T2 | Device backup or transfer exfiltrates private data | `private.db` | Exclude it in `dataExtractionRules` and `fullBackupContent`. Encrypt with a Keystore-wrapped key, which by design does not survive backup. |
| T3 | Server breach | Synced data | Recovery sync is **E2E encrypted**, so the server holds ciphertext only. Minimal user data. Rounded location. Encrypted backups. Separate DB roles. |
| T4 | Malicious or compromised admin | Content integrity | Role separation (an editor cannot publish), mandatory reviewer, MFA, audit log, signed content packs verified on the client, version rollback. |
| T5 | Account takeover (stolen refresh token) | User data | Short access TTL, rotating refresh tokens with reuse detection, device session list and revocation, Keystore-wrapped token storage. |
| T6 | IDOR or cross-user access | All user rows | Identity only from validated claims (MP §6). Every query is scoped by `user_id`. Automated cross-tenant tests. |
| T7 | Tampered content pack or blocklist (MITM, CDN) | Quran integrity, filtering | TLS plus an Ed25519 signature on the manifest plus SHA-256 per file. The client refuses an unsigned or invalid pack. |
| T8 | Logs or crash reports leak content | Recovery text, URLs | Redaction layer in `core:common` (SA "logging redaction"). No request bodies. Strip URLs and query strings. Scrubbing tests. |
| T9 | The VPN filter itself becomes a privacy risk | DNS queries | Process DNS **on device only**. Never upload query logs. State this in the privacy policy and UI. |
| T10 | The AI provider receives personal data | Questions | Send only the question plus retrieved public passages. No user IDs, location or recovery context. A data processing agreement with the provider. Opt-in. |
| T11 | Exposed infrastructure | DB, Redis, MinIO admin | Bind to an internal network only. Firewall. Reverse proxy is the only public port. SSH keys only. Automatic security updates. |
| T12 | Supply chain | Dependencies | Dependency scanning (OWASP, Renovate or Dependabot), lockfiles, Gradle dependency verification, signed releases, Play App Signing. |
| T13 | Abuse of public API | Server | Rate limiting per IP and per user, request size limits, input validation (MP §6). |

## 3. Protection mechanisms (recommended baseline)

- **Transport:** TLS 1.2 or later everywhere, HSTS on the admin site. Certificate pinning is optional and high-maintenance: **[REC]** skip it in v1, relying on standard trust plus signed content packs.
- **On device:**
  - Keystore-backed AEAD (for example Tink) for tokens and the database key.
  - SQLCipher for `private.db`.
  - `android:allowBackup` rules.
  - `FLAG_SECURE` on Recovery screens (blocks screenshots and recents thumbnails) as an option.
  - No sensitive data in notifications on the lock screen (`VISIBILITY_PRIVATE` or `SECRET`).
- **E2E recovery sync:**
  - A data key is generated on device and wrapped by a key derived from a user passphrase (Argon2id or scrypt) for multi-device restore.
  - Losing the passphrase means losing synced recovery data. This must be disclosed. [Q-ESS-08]
- **Server:**
  - Least-privilege DB roles per context; a migrations role separate from the runtime role.
  - Secrets in environment variables or a secret manager; `.env.example` only in Git.
  - Encrypted off-site backups, with a restore drill.
  - Audit log without private text.
- **Retention:**
  - Documented per category.
  - Account deletion purges live data immediately, or after a short grace period. Backups age out within N days (for example ≤ 35), and the policy states this (AR §6).

## 4. Required permissions and policy declarations

| Capability | Permission / API | Classification | Notes |
|---|---|---|---|
| Notifications | `POST_NOTIFICATIONS` (API 33+) | Runtime | Ask with a rationale at the point of need. |
| Prayer/adhan and alarms at exact times | `SCHEDULE_EXACT_ALARM` (API 31+; **denied by default** for new installs on Android 14+), or `USE_EXACT_ALARM` (API 33+) | Special and **Play-restricted** | Play limits `USE_EXACT_ALARM` to apps whose *core* function is an alarm clock or calendar. A multi-purpose app may not qualify. **[verify]** Plan for `SCHEDULE_EXACT_ALARM` with a user-grant flow and an inexact fallback. `setAlarmClock()` gives the best Doze behaviour. |
| Full-screen alarm UI | `USE_FULL_SCREEN_INTENT` | Restricted on Android 14+ | Auto-granted only to calling and alarm apps under Play policy **[verify]**. Otherwise the user must grant it. Fall back to a high-priority notification. |
| Ringing / adhan playback | Foreground service with an appropriate **type** (required for target 34+) | Play FGS declaration | **[verify]** the correct type and declaration for alarm ringing and audio playback (`mediaPlayback` for recitation). |
| Reschedule after reboot | `RECEIVE_BOOT_COMPLETED`, plus `LOCKED_BOOT_COMPLETED` with `directBootAware` | Normal | Without direct boot, alarms after a reboot do not fire until the first unlock, which is a real failure mode for morning alarms. |
| Location | `ACCESS_COARSE_LOCATION` only | Runtime | Fine location is unnecessary for prayer times. A manual city is always available. |
| Qibla | Sensors (no permission) | Standard | |
| QR verification | `CAMERA` | Runtime, foreground only | Never used in the background (AR §5). |
| Motion task, step-based verification | Accelerometer (no permission); `ACTIVITY_RECOGNITION` if the step counter is used (API 29+) | Runtime if steps are used | Sample only within the verification window. |
| Filtering | `VpnService` (`BIND_VPN_SERVICE`, user consent via `VpnService.prepare`) | **Play VpnService declaration** | Only **one VPN can be active** at a time. Starting another VPN revokes this one, so the UI must detect `onRevoke`. **[verify]** Play's current list of permitted VpnService uses. |
| Alternative filtering | Guide the user to set Private DNS to a filtering resolver | User action | An ordinary app cannot set Private DNS programmatically. A device owner can (`setGlobalPrivateDnsModeSpecifiedHost`, API 29+). |
| Managed mode | Device Owner DPC: `setUninstallBlocked`, `setAlwaysOnVpnPackage(lockdown)`, user restrictions (for example DISALLOW_CONFIG_VPN, DISALLOW_CONFIG_PRIVATE_DNS, DISALLOW_FACTORY_RESET, DISALLOW_SAFE_BOOT) | **DO**: needs factory-reset provisioning (QR or zero-touch; adb only for development) | Play policy for DPC apps and the Android Enterprise requirements apply **[verify]**. A consumer-facing DPC is a significant product and policy undertaking. Keep it a spike (P2). |
| Focus "distraction reduction" | `ACCESS_NOTIFICATION_POLICY` (DND) and/or `PACKAGE_USAGE_STATS` | Special | Undefined requirement [Q-DEF-09]. Avoid `QUERY_ALL_PACKAGES` (Play-restricted). |
| Accessibility service | — | **Do not use** | AR §4 permits it only for a legitimate core function. Play policy prohibits non-accessibility uses. URL monitoring through accessibility would contradict AR §4. |

## 5. Platform limitations (what cannot be guaranteed)

- **Filtering bypass on ordinary devices:**
  - Uninstalling the app, disabling the VPN, or switching to another VPN.
  - Browsers using **DNS-over-HTTPS** directly (some apps hard-code DoH), Tor, or proxies.
  - Encrypted SNI/ECH, which makes inspection by SNI impossible. A DNS-level filter cannot see URLs or paths, so content on a mixed domain cannot be classified (AR §1).
  - Safe mode and factory reset.
  - **[REC]** Add SafeSearch enforcement through DNS rewriting of known search engines, and block well-known DoH endpoints in the list, as a mitigation only, never presented as a guarantee.
- **Background reliability:** some OEM battery managers kill apps or delay alarms regardless of API correctness. Provide device-specific guidance and a "reliability check" screen. Results are never guaranteed (AR §5).
- **Wake inference:** probabilistic only. Motion, unlock or task completion indicate interaction, not wakefulness (PR, AR §5).
- **Managed mode:** requires provisioning. It cannot be applied to an already set-up personal device without a reset, in the fully-managed configuration. Recovery and unenrol procedures must be documented (AR §3).
- **iOS:** none of the Android protection mechanisms carry over. iOS uses NetworkExtension content filters (supervised or Screen Time contexts) and Screen Time APIs, and alarm capabilities differ (newer iOS versions add third-party alarm APIs **[verify]**). Re-evaluation is required (RM Phase 8).

## 6. Consent and ethics checks against the documents

| Requirement | Status in package | Gap |
|---|---|---|
| No covert monitoring (PR §5, AR §5) | Clearly stated | Needs enforcement: a CI lint check that flags any background camera or microphone usage; manifest review. |
| Trusted-person support "with explicit consent" (PR) | Stated but **unspecified** | Define what is shared, per-event confirmation, revocation, and the third party's consent. |
| Disabling protection must not be deceptive (DS) | Stated | Clarify whether an optional **self-imposed cooldown** (for example "protection turns off in 15 minutes") is acceptable. It must be user-configured, transparent, and have an emergency path. [Q-ESS-07] |
| Minors on managed devices (PR persona 6) | Not addressed | Age policy, parental consent, Play Families policy if children are targeted. [Q-ESS-09] |
| Privacy policy, Play Data Safety form | Not mentioned | Required before release. |
| Applicable law (GDPR, local laws) | MP §6 says "review local laws" | Target jurisdictions are unknown. [Q-ESS-10] |
| Crisis resources in Recovery | Not mentioned | Consider region-appropriate support resources; avoid medical claims. |

## 7. Security acceptance gates (proposed)

1. Automated test: no endpoint returns another user's data. Covered for 100% of user-scoped endpoints.
2. Redaction tests on logger and crash reporter.
3. Network-traffic test: with sync off, Recovery and Protection produce zero outbound requests.
4. `private.db` cannot be read from a backup or adb backup extraction.
5. A modified content pack is rejected.
6. Dependency scan has no unresolved high or critical findings at release.
7. A backup restore drill succeeds and is documented.
8. An external or independent security review before public release (RM Phase 7).
