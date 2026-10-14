# System Architecture

## Architectural decision
Use a modular Android application and a modular-monolith backend. Do not begin with microservices unless measurable scaling or team boundaries require them.

## Android layers
- Presentation: Compose screens, UI state, navigation.
- Domain: use cases, entities, policies, validation.
- Data: repositories, Room, API clients, sync.
- Platform adapters: alarms, notifications, sensors, VPN, device policy, audio.
- Shared core: design system, error types, logging redaction, security utilities.

## Suggested modules
app
core:common
core:design-system
core:database
core:network
core:security
core:notifications
core:navigation
feature:onboarding
feature:authentication
feature:home
feature:prayer-times
feature:qibla
feature:quran
feature:tafsir
feature:hadith
feature:seerah
feature:islamic-library
feature:dhikr
feature:memorization
feature:protection
feature:recovery
feature:smart-alarm
feature:sleep-insights
feature:focus
feature:ai-assistant
feature:settings
platform:android-alarm
platform:android-vpn
platform:android-device-policy
platform:android-audio

## Backend bounded contexts
- Identity and sessions
- User preferences and sync
- Islamic content catalog and sources
- Search
- Protection rule distribution
- Recovery sync (opt-in)
- Alarm preferences and aggregate outcomes (opt-in)
- Admin CMS and audit
- Content release/versioning

## API conventions
- Version API under `/api/v1`.
- Use OpenAPI as the contract.
- Validate all inputs server-side.
- Derive user identity from validated session claims, not from arbitrary client-supplied user IDs.
- Use idempotency keys for retry-prone write operations.
- Paginate list endpoints.
- Return stable error codes and correlation IDs.
- Avoid logging sensitive request bodies.

## Data principles
- Content data is separate from user data.
- Local-first for prayer preferences, bookmarks, alarm events, and recovery details where feasible.
- Sync only fields that require cross-device persistence.
- Include content source, license, revision, and checksum.
- Design account deletion and retention from day one.

## iOS portability
Keep business rules and user-owned state contracts platform-neutral where practical. Implement Android and iOS alarms, notifications, device restrictions, and sensor access with native adapters. Never assume policy parity across platforms.
