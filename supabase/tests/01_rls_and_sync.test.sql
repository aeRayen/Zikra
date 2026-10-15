-- Acceptance for WP 1.10: a user can never read or change another user's data, guests (anon)
-- cannot call the contract, and the sync rules of SYNC_PROTOCOL_AR.md hold.
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET search_path = extensions, public;
SELECT plan(18);

-- Two users (as the database owner).
INSERT INTO auth.users (id, instance_id, aud, role, email) VALUES
  ('aaaaaaaa-0000-4000-8000-000000000001', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'a@test.local'),
  ('bbbbbbbb-0000-4000-8000-000000000002', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'b@test.local');

SELECT is((SELECT count(*)::int FROM app.profiles), 2, 'a profile is created for every new user');

-- ===== User A =====
SELECT set_config('request.jwt.claims', '{"sub":"aaaaaaaa-0000-4000-8000-000000000001","role":"authenticated"}', true);
SET LOCAL ROLE authenticated;

SELECT throws_ok(
  $$SELECT api.sync_push_v1('[{"entity":"bookmark","id":"11111111-0000-4000-8000-000000000001","op":"upsert","clientUpdatedAt":"2026-10-15T10:00:00Z","data":{"itemType":"quran_ayah","itemKey":"quran:2:255"}}]')$$,
  '42501', 'sync.consent_required', 'no sync without consent');

SELECT lives_ok($$SELECT api.set_consent_v1('sync.bookmarks', true, '1')$$, 'consent: bookmarks');
SELECT lives_ok($$SELECT api.set_consent_v1('sync.worship', true, '1')$$, 'consent: worship');
SELECT lives_ok($$SELECT api.set_consent_v1('sync.settings', true, '1')$$, 'consent: settings');

SELECT is(
  api.sync_push_v1('[{"entity":"bookmark","id":"11111111-0000-4000-8000-000000000001","op":"upsert","clientUpdatedAt":"2026-10-15T10:00:00Z","data":{"itemType":"quran_ayah","itemKey":"quran:2:255"}}]')
    ->'results'->0->>'status',
  'applied', 'bookmark push is applied');

-- The owner is forced to the caller even if the client claims another user.
INSERT INTO app.bookmarks (id, user_id, item_type, item_key, client_updated_at)
  VALUES ('11111111-0000-4000-8000-000000000002', 'bbbbbbbb-0000-4000-8000-000000000002', 'quran_ayah', 'quran:1:1', now());
SELECT is((SELECT user_id::text FROM app.bookmarks WHERE id = '11111111-0000-4000-8000-000000000002'),
  'aaaaaaaa-0000-4000-8000-000000000001', 'user_id is forced to the caller');

-- Progress: the highest value of the day wins.
SELECT api.sync_push_v1('[{"entity":"worship_goal","id":"22222222-0000-4000-8000-000000000001","op":"upsert","clientUpdatedAt":"2026-10-15T10:00:00Z","data":{"goalType":"quran_pages","period":"daily","targetValue":5}}]');
SELECT api.sync_push_v1('[{"entity":"worship_progress","id":"33333333-0000-4000-8000-000000000001","op":"upsert","clientUpdatedAt":"2026-10-15T10:00:00Z","data":{"goalId":"22222222-0000-4000-8000-000000000001","localDate":"2026-10-15","value":3}}]');
SELECT api.sync_push_v1('[{"entity":"worship_progress","id":"33333333-0000-4000-8000-000000000002","op":"upsert","clientUpdatedAt":"2026-10-15T11:00:00Z","data":{"goalId":"22222222-0000-4000-8000-000000000001","localDate":"2026-10-15","value":5}}]');
SELECT api.sync_push_v1('[{"entity":"worship_progress","id":"33333333-0000-4000-8000-000000000003","op":"upsert","clientUpdatedAt":"2026-10-15T12:00:00Z","data":{"goalId":"22222222-0000-4000-8000-000000000001","localDate":"2026-10-15","value":2}}]');
SELECT is((SELECT value FROM app.worship_progress WHERE local_date = '2026-10-15'), 5, 'progress keeps the max per day');
SELECT is((SELECT count(*)::int FROM app.worship_progress), 1, 'one progress row per goal and day');

-- Settings: last write wins; an older change is merged away.
SELECT api.sync_push_v1('[{"entity":"settings","id":"aaaaaaaa-0000-4000-8000-000000000001","op":"upsert","clientUpdatedAt":"2026-10-15T12:00:00Z","data":{"theme":"dark","latRounded":30.04442}}]');
SELECT is(
  api.sync_push_v1('[{"entity":"settings","id":"aaaaaaaa-0000-4000-8000-000000000001","op":"upsert","clientUpdatedAt":"2026-10-15T09:00:00Z","data":{"theme":"light"}}]')
    ->'results'->0->>'status',
  'merged', 'an older settings change does not overwrite a newer one');
SELECT is((SELECT theme || ' ' || lat_rounded::text FROM app.settings), 'dark 30.0',
  'newest theme kept; latitude stored rounded to 0.1 degree (ADR-018)');

-- Tombstones are pulled as deleted.
SELECT api.sync_push_v1('[{"entity":"bookmark","id":"11111111-0000-4000-8000-000000000002","op":"delete","clientUpdatedAt":"2026-10-15T13:00:00Z"}]');
SELECT ok(
  (SELECT bool_or((r->>'deleted')::boolean) FROM jsonb_array_elements(api.sync_pull_v1(0, 200)->'records') r
    WHERE r->>'id' = '11111111-0000-4000-8000-000000000002'),
  'deleted bookmark is pulled as a tombstone');

-- ===== User B =====
SELECT set_config('request.jwt.claims', '{"sub":"bbbbbbbb-0000-4000-8000-000000000002","role":"authenticated"}', true);

SELECT is((SELECT count(*)::int FROM app.bookmarks), 0, 'B cannot read A''s bookmarks');
SELECT is(jsonb_array_length(api.sync_pull_v1(0, 200)->'records'), 0, 'B pulls nothing of A''s data');

UPDATE app.bookmarks SET item_key = 'hacked' WHERE id = '11111111-0000-4000-8000-000000000001';
SELECT api.set_consent_v1('sync.worship', true, '1');
SELECT is(
  api.sync_push_v1('[{"entity":"worship_progress","id":"33333333-0000-4000-8000-000000000009","op":"upsert","clientUpdatedAt":"2026-10-15T10:00:00Z","data":{"goalId":"22222222-0000-4000-8000-000000000001","localDate":"2026-10-16","value":9}}]')
    ->'results'->0->>'status',
  'rejected', 'B cannot attach progress to A''s goal');

-- ===== Anonymous (guest) =====
RESET ROLE;
SET LOCAL ROLE anon;
SELECT throws_ok($$SELECT api.sync_pull_v1(0, 200)$$, '42501', NULL, 'anon cannot call the sync contract');

-- ===== Back as owner: verify B's update did nothing =====
RESET ROLE;
SELECT is((SELECT item_key FROM app.bookmarks WHERE id = '11111111-0000-4000-8000-000000000001'), 'quran:2:255',
  'B''s UPDATE on A''s row changed nothing');

-- ===== A revokes consent: server copy is deleted =====
SELECT set_config('request.jwt.claims', '{"sub":"aaaaaaaa-0000-4000-8000-000000000001","role":"authenticated"}', true);
SET LOCAL ROLE authenticated;
SELECT api.set_consent_v1('sync.bookmarks', false, '1');
SELECT is((SELECT count(*)::int FROM app.bookmarks), 0, 'revoking consent deletes that category on the server');

SELECT * FROM finish();
ROLLBACK;
