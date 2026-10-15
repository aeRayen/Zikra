-- =====================================================================
-- Zikra (ذِكْرَى) — V1: per-account user data + versioned RPC contract
-- Design: docs/specs/SUPABASE_ARCHITECTURE_AR.md, ADR-024/025, SYNC_PROTOCOL_AR.md
--
--   auth.*  Supabase Auth (managed)
--   app.*   user data tables — NOT exposed through the Data API (config.toml: schemas = ["api"])
--   api.*   versioned RPC functions — the only client contract
--
-- Never stored here: recovery/protection data (ADR-011), bookmark notes (ADR-012),
-- alarm data, precise location (ADR-018).
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS api;

REVOKE ALL ON SCHEMA app FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA app TO authenticated;          -- RPC functions run as the caller (RLS applies)
REVOKE ALL ON SCHEMA api FROM PUBLIC, anon;
GRANT USAGE ON SCHEMA api TO authenticated;

-- Functions are executable by PUBLIC by default; the contract is for signed-in users only.
ALTER DEFAULT PRIVILEGES IN SCHEMA api REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA app REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

-- ---------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------
CREATE SEQUENCE app.server_version_seq AS BIGINT;
GRANT USAGE ON SEQUENCE app.server_version_seq TO authenticated;

CREATE FUNCTION app.bump_version() RETURNS trigger
LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
  NEW.server_version := nextval('app.server_version_seq');
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

-- The owner is always the caller, whatever the client sent.
CREATE FUNCTION app.force_owner() RETURNS trigger
LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'auth.not_signed_in' USING ERRCODE = '42501';
  END IF;
  NEW.user_id := auth.uid();
  RETURN NEW;
END;
$$;
GRANT EXECUTE ON FUNCTION app.bump_version(), app.force_owner() TO authenticated;

-- ---------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------
CREATE TABLE app.profiles (
  user_id       UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  display_name  VARCHAR(100),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE FUNCTION app.handle_new_user() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  INSERT INTO app.profiles (user_id) VALUES (NEW.id);
  RETURN NEW;
END;
$$;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users FOR EACH ROW EXECUTE FUNCTION app.handle_new_user();

CREATE TABLE app.consents (
  user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  category        VARCHAR(30) NOT NULL CHECK (category IN
                    ('sync.settings','sync.bookmarks','sync.reading','sync.worship','crash_reports')),
  granted         BOOLEAN NOT NULL,
  policy_version  VARCHAR(20) NOT NULL,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, category)
);

CREATE TABLE app.settings (
  user_id              UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  language             VARCHAR(35),
  theme                VARCHAR(10) NOT NULL DEFAULT 'system' CHECK (theme IN ('system','light','dark')),
  numeral_system       VARCHAR(10) NOT NULL DEFAULT 'auto' CHECK (numeral_system IN ('auto','latn','arab','arabext')),
  calculation_method   VARCHAR(40),
  asr_madhab           VARCHAR(10) CHECK (asr_madhab IN ('standard','hanafi')),
  high_latitude_rule   VARCHAR(20) CHECK (high_latitude_rule IN ('none','middle_of_night','seventh_of_night','twilight_angle')),
  hijri_offset_days    SMALLINT NOT NULL DEFAULT 0 CHECK (hijri_offset_days BETWEEN -2 AND 2),
  timezone             VARCHAR(64),
  city_id              VARCHAR(40),
  lat_rounded          NUMERIC(4,1) CHECK (lat_rounded BETWEEN -90 AND 90),
  lon_rounded          NUMERIC(4,1) CHECK (lon_rounded BETWEEN -180 AND 180),
  client_updated_at    TIMESTAMPTZ NOT NULL,
  server_version       BIGINT NOT NULL,
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app.bookmarks (
  id                 UUID PRIMARY KEY,
  user_id            UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  item_type          VARCHAR(20) NOT NULL CHECK (item_type IN ('quran_ayah','hadith','article','dhikr')),
  item_key           VARCHAR(200) NOT NULL,
  client_updated_at  TIMESTAMPTZ NOT NULL,
  deleted_at         TIMESTAMPTZ,
  server_version     BIGINT NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX bookmarks_live_uq ON app.bookmarks (user_id, item_type, item_key) WHERE deleted_at IS NULL;

CREATE TABLE app.reading_positions (
  id                 UUID PRIMARY KEY,
  user_id            UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  surah              SMALLINT NOT NULL CHECK (surah BETWEEN 1 AND 114),
  ayah               SMALLINT NOT NULL CHECK (ayah BETWEEN 1 AND 286),
  client_updated_at  TIMESTAMPTZ NOT NULL,
  deleted_at         TIMESTAMPTZ,
  server_version     BIGINT NOT NULL,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX reading_positions_user_uq ON app.reading_positions (user_id) WHERE deleted_at IS NULL;

CREATE TABLE app.worship_goals (
  id                 UUID PRIMARY KEY,
  user_id            UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  goal_type          VARCHAR(40) NOT NULL,
  title              VARCHAR(120),
  target_value       INTEGER CHECK (target_value > 0),
  period             VARCHAR(10) NOT NULL CHECK (period IN ('daily','weekly')),
  archived           BOOLEAN NOT NULL DEFAULT false,
  client_updated_at  TIMESTAMPTZ NOT NULL,
  deleted_at         TIMESTAMPTZ,
  server_version     BIGINT NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app.worship_progress (
  id                 UUID PRIMARY KEY,
  user_id            UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  goal_id            UUID NOT NULL REFERENCES app.worship_goals(id) ON DELETE CASCADE,
  local_date         DATE NOT NULL,
  value              INTEGER NOT NULL DEFAULT 0 CHECK (value >= 0),
  client_updated_at  TIMESTAMPTZ NOT NULL,
  deleted_at         TIMESTAMPTZ,
  server_version     BIGINT NOT NULL,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX worship_progress_day_uq ON app.worship_progress (goal_id, local_date) WHERE deleted_at IS NULL;
CREATE INDEX worship_progress_goal_idx ON app.worship_progress (goal_id);

CREATE TABLE app.deletion_requests (
  user_id             UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  requested_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  scheduled_purge_at  TIMESTAMPTZ NOT NULL DEFAULT now() + interval '14 days'
);

-- Sync cursor indexes (ADR-025 §5)
CREATE INDEX settings_sync_idx          ON app.settings          (user_id, server_version);
CREATE INDEX bookmarks_sync_idx         ON app.bookmarks         (user_id, server_version);
CREATE INDEX reading_positions_sync_idx ON app.reading_positions (user_id, server_version);
CREATE INDEX worship_goals_sync_idx     ON app.worship_goals     (user_id, server_version);
CREATE INDEX worship_progress_sync_idx  ON app.worship_progress  (user_id, server_version);

-- Owner + version triggers on every synced table
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['settings','bookmarks','reading_positions','worship_goals','worship_progress'] LOOP
    EXECUTE format('CREATE TRIGGER %I_owner BEFORE INSERT OR UPDATE ON app.%I FOR EACH ROW EXECUTE FUNCTION app.force_owner()', t, t);
    EXECUTE format('CREATE TRIGGER %I_bump BEFORE INSERT OR UPDATE ON app.%I FOR EACH ROW EXECUTE FUNCTION app.bump_version()', t, t);
  END LOOP;
END;
$$;
CREATE TRIGGER consents_owner BEFORE INSERT OR UPDATE ON app.consents FOR EACH ROW EXECUTE FUNCTION app.force_owner();
CREATE TRIGGER deletion_requests_owner BEFORE INSERT OR UPDATE ON app.deletion_requests FOR EACH ROW EXECUTE FUNCTION app.force_owner();

-- ---------------------------------------------------------------------
-- Row Level Security: a user can only ever see and change their own rows.
-- (select auth.uid()) is evaluated once per statement (performance at scale).
-- ---------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['profiles','consents','settings','bookmarks','reading_positions',
                           'worship_goals','worship_progress','deletion_requests'] LOOP
    EXECUTE format('ALTER TABLE app.%I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY own_rows ON app.%I FOR ALL TO authenticated '
      'USING (user_id = (select auth.uid())) WITH CHECK (user_id = (select auth.uid()))', t);
  END LOOP;
END;
$$;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA app TO authenticated;
REVOKE ALL ON ALL TABLES IN SCHEMA app FROM anon, PUBLIC;

-- ---------------------------------------------------------------------
-- api.* — client contract v1 (SECURITY INVOKER: RLS always applies)
-- ---------------------------------------------------------------------
CREATE FUNCTION api.require_user() RETURNS uuid
LANGUAGE plpgsql STABLE SET search_path = '' AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'auth.not_signed_in' USING ERRCODE = '42501';
  END IF;
  RETURN auth.uid();
END;
$$;

CREATE FUNCTION api.get_me_v1() RETURNS jsonb
LANGUAGE plpgsql STABLE SET search_path = '' AS $$
DECLARE uid uuid := api.require_user();
BEGIN
  RETURN (
    SELECT jsonb_build_object(
      'id', p.user_id,
      'displayName', p.display_name,
      'createdAt', p.created_at,
      'pendingDeletion', (SELECT d.scheduled_purge_at FROM app.deletion_requests d WHERE d.user_id = uid))
    FROM app.profiles p WHERE p.user_id = uid);
END;
$$;

-- Revoking a sync.* consent deletes that category's server data (SYNC_PROTOCOL §1).
CREATE FUNCTION api.set_consent_v1(p_category text, p_granted boolean, p_policy_version text) RETURNS jsonb
LANGUAGE plpgsql SET search_path = '' AS $$
DECLARE uid uuid := api.require_user();
BEGIN
  INSERT INTO app.consents (user_id, category, granted, policy_version, updated_at)
  VALUES (uid, p_category, p_granted, p_policy_version, now())
  ON CONFLICT (user_id, category) DO UPDATE
    SET granted = EXCLUDED.granted, policy_version = EXCLUDED.policy_version, updated_at = now();

  IF NOT p_granted THEN
    CASE p_category
      WHEN 'sync.settings'  THEN DELETE FROM app.settings WHERE user_id = uid;
      WHEN 'sync.bookmarks' THEN DELETE FROM app.bookmarks WHERE user_id = uid;
      WHEN 'sync.reading'   THEN DELETE FROM app.reading_positions WHERE user_id = uid;
      WHEN 'sync.worship'   THEN DELETE FROM app.worship_goals WHERE user_id = uid; -- cascades progress
      ELSE NULL;
    END CASE;
  END IF;
  RETURN jsonb_build_object('category', p_category, 'granted', p_granted, 'policyVersion', p_policy_version);
END;
$$;

CREATE FUNCTION app.consent_for(p_entity text) RETURNS text
LANGUAGE sql IMMUTABLE SET search_path = '' AS $$
  SELECT CASE p_entity
    WHEN 'settings' THEN 'sync.settings'
    WHEN 'bookmark' THEN 'sync.bookmarks'
    WHEN 'reading_position' THEN 'sync.reading'
    WHEN 'worship_goal' THEN 'sync.worship'
    WHEN 'worship_progress' THEN 'sync.worship'
  END;
$$;
GRANT EXECUTE ON FUNCTION app.consent_for(text) TO authenticated;

/*
 * Push local changes (SYNC_PROTOCOL §3–4). Conflict rules (§2):
 *   settings, reading_position, worship_goal: last write wins (client_updated_at)
 *   bookmark: union; a delete wins only if newer than the stored change
 *   worship_progress: highest value per (goal, day) wins
 * Upserts are keyed by client-generated ids, so retries are idempotent.
 */
CREATE FUNCTION api.sync_push_v1(p_changes jsonb) RETURNS jsonb
LANGUAGE plpgsql SET search_path = '' AS $$
DECLARE
  uid uuid := api.require_user();
  c jsonb;
  results jsonb := '[]'::jsonb;
  ent text; rid uuid; op text; ts timestamptz; d jsonb;
  st text; ver bigint;
BEGIN
  IF jsonb_typeof(p_changes) <> 'array' THEN
    RAISE EXCEPTION 'request.validation_failed' USING ERRCODE = '22023';
  END IF;
  IF jsonb_array_length(p_changes) > 200 THEN
    RAISE EXCEPTION 'request.too_large' USING ERRCODE = '54000';
  END IF;

  -- Every category in the batch must be consented, otherwise nothing is applied.
  IF EXISTS (
    SELECT 1 FROM jsonb_array_elements(p_changes) ch
    WHERE app.consent_for(ch->>'entity') IS NULL
       OR NOT EXISTS (SELECT 1 FROM app.consents k
                      WHERE k.user_id = uid AND k.category = app.consent_for(ch->>'entity') AND k.granted)
  ) THEN
    RAISE EXCEPTION 'sync.consent_required' USING ERRCODE = '42501';
  END IF;

  FOR c IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
    ent := c->>'entity'; rid := (c->>'id')::uuid; op := c->>'op';
    ts := (c->>'clientUpdatedAt')::timestamptz; d := COALESCE(c->'data', '{}'::jsonb);
    st := 'applied';

    IF ent = 'bookmark' THEN
      IF op = 'delete' THEN
        UPDATE app.bookmarks SET deleted_at = now(), client_updated_at = ts
          WHERE id = rid AND client_updated_at <= ts;
        IF NOT FOUND THEN st := 'merged'; END IF;
      ELSE
        INSERT INTO app.bookmarks (id, user_id, item_type, item_key, client_updated_at)
        VALUES (rid, uid, d->>'itemType', d->>'itemKey', ts)
        ON CONFLICT (id) DO UPDATE SET deleted_at = NULL, client_updated_at = EXCLUDED.client_updated_at
          WHERE app.bookmarks.client_updated_at <= EXCLUDED.client_updated_at;
        IF NOT FOUND THEN st := 'merged'; END IF;
      END IF;
      SELECT server_version INTO ver FROM app.bookmarks WHERE id = rid;

    ELSIF ent = 'reading_position' THEN
      INSERT INTO app.reading_positions (id, user_id, surah, ayah, client_updated_at)
      VALUES (rid, uid, (d->>'surah')::smallint, (d->>'ayah')::smallint, ts)
      ON CONFLICT (id) DO UPDATE SET surah = EXCLUDED.surah, ayah = EXCLUDED.ayah,
        client_updated_at = EXCLUDED.client_updated_at
        WHERE app.reading_positions.client_updated_at <= EXCLUDED.client_updated_at;
      IF NOT FOUND THEN st := 'merged'; END IF;
      SELECT server_version INTO ver FROM app.reading_positions WHERE id = rid;

    ELSIF ent = 'worship_goal' THEN
      IF op = 'delete' THEN
        UPDATE app.worship_goals SET deleted_at = now(), client_updated_at = ts
          WHERE id = rid AND client_updated_at <= ts;
        IF NOT FOUND THEN st := 'merged'; END IF;
      ELSE
        INSERT INTO app.worship_goals (id, user_id, goal_type, title, target_value, period, archived, client_updated_at)
        VALUES (rid, uid, d->>'goalType', d->>'title', (d->>'targetValue')::int, d->>'period',
                COALESCE((d->>'archived')::boolean, false), ts)
        ON CONFLICT (id) DO UPDATE SET goal_type = EXCLUDED.goal_type, title = EXCLUDED.title,
          target_value = EXCLUDED.target_value, period = EXCLUDED.period, archived = EXCLUDED.archived,
          deleted_at = NULL, client_updated_at = EXCLUDED.client_updated_at
          WHERE app.worship_goals.client_updated_at <= EXCLUDED.client_updated_at;
        IF NOT FOUND THEN st := 'merged'; END IF;
      END IF;
      SELECT server_version INTO ver FROM app.worship_goals WHERE id = rid;

    ELSIF ent = 'worship_progress' THEN
      -- The goal must belong to the caller: FK checks bypass RLS, so check explicitly.
      IF NOT EXISTS (SELECT 1 FROM app.worship_goals g WHERE g.id = (d->>'goalId')::uuid AND g.user_id = uid) THEN
        results := results || jsonb_build_object('id', rid, 'status', 'rejected', 'errorCode', 'sync.invalid_data');
        CONTINUE;
      END IF;
      INSERT INTO app.worship_progress (id, user_id, goal_id, local_date, value, client_updated_at)
      VALUES (rid, uid, (d->>'goalId')::uuid, (d->>'localDate')::date, (d->>'value')::int, ts)
      ON CONFLICT (goal_id, local_date) WHERE deleted_at IS NULL DO UPDATE
        SET value = GREATEST(app.worship_progress.value, EXCLUDED.value),
            client_updated_at = GREATEST(app.worship_progress.client_updated_at, EXCLUDED.client_updated_at);
      SELECT id, server_version INTO rid, ver FROM app.worship_progress
        WHERE goal_id = (d->>'goalId')::uuid AND local_date = (d->>'localDate')::date AND deleted_at IS NULL;

    ELSIF ent = 'settings' THEN
      INSERT INTO app.settings (user_id, language, theme, numeral_system, calculation_method, asr_madhab,
        high_latitude_rule, hijri_offset_days, timezone, city_id, lat_rounded, lon_rounded, client_updated_at)
      VALUES (uid, d->>'language', COALESCE(d->>'theme', 'system'), COALESCE(d->>'numeralSystem', 'auto'),
        d->>'calculationMethod', d->>'asrMadhab', d->>'highLatitudeRule',
        COALESCE((d->>'hijriOffsetDays')::smallint, 0), d->>'timezone', d->>'cityId',
        round((d->>'latRounded')::numeric, 1), round((d->>'lonRounded')::numeric, 1), ts)
      ON CONFLICT (user_id) DO UPDATE SET language = EXCLUDED.language, theme = EXCLUDED.theme,
        numeral_system = EXCLUDED.numeral_system, calculation_method = EXCLUDED.calculation_method,
        asr_madhab = EXCLUDED.asr_madhab, high_latitude_rule = EXCLUDED.high_latitude_rule,
        hijri_offset_days = EXCLUDED.hijri_offset_days, timezone = EXCLUDED.timezone,
        city_id = EXCLUDED.city_id, lat_rounded = EXCLUDED.lat_rounded, lon_rounded = EXCLUDED.lon_rounded,
        client_updated_at = EXCLUDED.client_updated_at
        WHERE app.settings.client_updated_at <= EXCLUDED.client_updated_at;
      IF NOT FOUND THEN st := 'merged'; END IF;
      SELECT server_version INTO ver FROM app.settings WHERE user_id = uid;

    ELSE
      st := 'rejected';
      ver := NULL;
    END IF;

    results := results || jsonb_build_object('id', rid, 'status', st, 'serverVersion', ver);
  END LOOP;

  RETURN jsonb_build_object('results', results);
END;
$$;

/*
 * Pull everything changed after the cursor (server_version), oldest first.
 * The cursor is the last server_version returned; hasMore tells the client to call again.
 */
CREATE FUNCTION api.sync_pull_v1(p_since bigint DEFAULT 0, p_limit int DEFAULT 200) RETURNS jsonb
LANGUAGE plpgsql STABLE SET search_path = '' AS $$
DECLARE
  uid uuid := api.require_user();
  lim int := LEAST(GREATEST(COALESCE(p_limit, 200), 1), 500);
  recs jsonb;
  last_version bigint;
  total int;
BEGIN
  WITH changes AS (
    SELECT 'settings' AS entity, s.user_id AS id, s.server_version, false AS deleted, s.updated_at,
      jsonb_build_object('language', s.language, 'theme', s.theme, 'numeralSystem', s.numeral_system,
        'calculationMethod', s.calculation_method, 'asrMadhab', s.asr_madhab,
        'highLatitudeRule', s.high_latitude_rule, 'hijriOffsetDays', s.hijri_offset_days,
        'timezone', s.timezone, 'cityId', s.city_id, 'latRounded', s.lat_rounded, 'lonRounded', s.lon_rounded) AS data
      FROM app.settings s WHERE s.user_id = uid AND s.server_version > p_since
    UNION ALL
    SELECT 'bookmark', b.id, b.server_version, b.deleted_at IS NOT NULL, b.updated_at,
      jsonb_build_object('itemType', b.item_type, 'itemKey', b.item_key)
      FROM app.bookmarks b WHERE b.user_id = uid AND b.server_version > p_since
    UNION ALL
    SELECT 'reading_position', r.id, r.server_version, r.deleted_at IS NOT NULL, r.updated_at,
      jsonb_build_object('surah', r.surah, 'ayah', r.ayah)
      FROM app.reading_positions r WHERE r.user_id = uid AND r.server_version > p_since
    UNION ALL
    SELECT 'worship_goal', g.id, g.server_version, g.deleted_at IS NOT NULL, g.updated_at,
      jsonb_build_object('goalType', g.goal_type, 'title', g.title, 'targetValue', g.target_value,
        'period', g.period, 'archived', g.archived)
      FROM app.worship_goals g WHERE g.user_id = uid AND g.server_version > p_since
    UNION ALL
    SELECT 'worship_progress', w.id, w.server_version, w.deleted_at IS NOT NULL, w.updated_at,
      jsonb_build_object('goalId', w.goal_id, 'localDate', w.local_date, 'value', w.value)
      FROM app.worship_progress w WHERE w.user_id = uid AND w.server_version > p_since
  ), page AS (
    SELECT * FROM changes ORDER BY server_version LIMIT lim
  )
  SELECT COALESCE(jsonb_agg(jsonb_build_object('entity', entity, 'id', id, 'serverVersion', server_version,
           'deleted', deleted, 'updatedAt', updated_at, 'data', data) ORDER BY server_version), '[]'::jsonb),
         max(server_version),
         count(*)
    INTO recs, last_version, total
    FROM page;

  RETURN jsonb_build_object(
    'records', recs,
    'cursor', COALESCE(last_version, p_since)::text,
    'hasMore', total = lim);
END;
$$;

CREATE FUNCTION api.request_deletion_v1() RETURNS jsonb
LANGUAGE plpgsql SET search_path = '' AS $$
DECLARE uid uuid := api.require_user(); purge timestamptz;
BEGIN
  INSERT INTO app.deletion_requests (user_id) VALUES (uid) ON CONFLICT (user_id) DO NOTHING;
  SELECT scheduled_purge_at INTO purge FROM app.deletion_requests WHERE user_id = uid;
  RETURN jsonb_build_object('status', 'pending', 'scheduledPurgeAt', purge);
END;
$$;

CREATE FUNCTION api.cancel_deletion_v1() RETURNS void
LANGUAGE plpgsql SET search_path = '' AS $$
DECLARE uid uuid := api.require_user();
BEGIN
  DELETE FROM app.deletion_requests WHERE user_id = uid;
END;
$$;

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA api FROM PUBLIC, anon;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA api TO authenticated;
