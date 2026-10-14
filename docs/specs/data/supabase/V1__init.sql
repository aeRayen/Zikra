-- =====================================================================
-- Zikra (ذِكْرَى) — Supabase V1 schema DESIGN DRAFT  (ADR-024)
-- Supersedes the identity/userdata parts of phase-0b/data/V1__init.sql.
-- STATUS: designed, NOT executed. Validated in package 1.10 with
--         `supabase db reset` on the local Supabase stack + pgTAP RLS tests.
--
-- Layout:
--   auth.*     managed by Supabase Auth (users, sessions, refresh tokens)
--   app.*      user data tables — NOT exposed through the Data API
--   api.*      versioned RPC functions — the ONLY schema exposed to clients
--   content.*  content catalogue for the pipeline/CMS (from phase-0b V1, unchanged,
--              not exposed; packs are served from a CDN, not from Supabase)
--
-- NOT stored here (by decision): recovery/protection data (ADR-011),
-- bookmark notes (ADR-012), alarm data, precise location (ADR-018).
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS api;

REVOKE ALL ON SCHEMA app FROM anon, authenticated;
GRANT USAGE ON SCHEMA app TO authenticated;          -- needed by SECURITY INVOKER rpc
GRANT USAGE ON SCHEMA api TO authenticated;
REVOKE ALL ON SCHEMA api FROM anon;                  -- no anonymous RPC access

-- ---------------------------------------------------------------------
-- Versioning helpers (sync cursor)
-- ---------------------------------------------------------------------
CREATE SEQUENCE app.server_version_seq AS BIGINT;
GRANT USAGE ON SEQUENCE app.server_version_seq TO authenticated;

CREATE FUNCTION app.bump_version() RETURNS trigger
LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
  NEW.server_version := nextval('app.server_version_seq');
  NEW.updated_at     := now();
  RETURN NEW;
END;
$$;

-- Forces user_id to the caller on every write, whatever the client sent.
CREATE FUNCTION app.force_owner() RETURNS trigger
LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'auth.required' USING ERRCODE = '42501';
  END IF;
  NEW.user_id := auth.uid();
  RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------
-- Profiles (1:1 with auth.users)
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

-- ---------------------------------------------------------------------
-- Consents
-- ---------------------------------------------------------------------
CREATE TABLE app.consents (
  user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  category        VARCHAR(30) NOT NULL CHECK (category IN
                    ('sync.settings','sync.bookmarks','sync.reading','sync.worship','crash_reports')),
  granted         BOOLEAN NOT NULL,
  policy_version  VARCHAR(20) NOT NULL,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, category)
);

-- ---------------------------------------------------------------------
-- Synced user data (same semantics as phase-0b/SYNC_PROTOCOL_AR.md)
-- ---------------------------------------------------------------------
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
  id                 UUID PRIMARY KEY,                          -- client-generated
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

-- Sync cursor indexes (ADR-025 §5)
CREATE INDEX settings_sync_idx          ON app.settings          (user_id, server_version);
CREATE INDEX bookmarks_sync_idx         ON app.bookmarks         (user_id, server_version);
CREATE INDEX reading_positions_sync_idx ON app.reading_positions (user_id, server_version);
CREATE INDEX worship_goals_sync_idx     ON app.worship_goals     (user_id, server_version);
CREATE INDEX worship_progress_sync_idx  ON app.worship_progress  (user_id, server_version);
CREATE INDEX worship_progress_goal_idx  ON app.worship_progress  (goal_id);

-- Triggers: owner + version on every synced table
CREATE TRIGGER settings_owner          BEFORE INSERT OR UPDATE ON app.settings          FOR EACH ROW EXECUTE FUNCTION app.force_owner();
CREATE TRIGGER settings_bump           BEFORE INSERT OR UPDATE ON app.settings          FOR EACH ROW EXECUTE FUNCTION app.bump_version();
CREATE TRIGGER bookmarks_owner         BEFORE INSERT OR UPDATE ON app.bookmarks         FOR EACH ROW EXECUTE FUNCTION app.force_owner();
CREATE TRIGGER bookmarks_bump          BEFORE INSERT OR UPDATE ON app.bookmarks         FOR EACH ROW EXECUTE FUNCTION app.bump_version();
CREATE TRIGGER reading_positions_owner BEFORE INSERT OR UPDATE ON app.reading_positions FOR EACH ROW EXECUTE FUNCTION app.force_owner();
CREATE TRIGGER reading_positions_bump  BEFORE INSERT OR UPDATE ON app.reading_positions FOR EACH ROW EXECUTE FUNCTION app.bump_version();
CREATE TRIGGER worship_goals_owner     BEFORE INSERT OR UPDATE ON app.worship_goals     FOR EACH ROW EXECUTE FUNCTION app.force_owner();
CREATE TRIGGER worship_goals_bump      BEFORE INSERT OR UPDATE ON app.worship_goals     FOR EACH ROW EXECUTE FUNCTION app.bump_version();
CREATE TRIGGER worship_progress_owner  BEFORE INSERT OR UPDATE ON app.worship_progress  FOR EACH ROW EXECUTE FUNCTION app.force_owner();
CREATE TRIGGER worship_progress_bump   BEFORE INSERT OR UPDATE ON app.worship_progress  FOR EACH ROW EXECUTE FUNCTION app.bump_version();

-- ---------------------------------------------------------------------
-- Row Level Security — defence in depth even though tables are not exposed
-- Pattern: (select auth.uid()) is evaluated once per statement (performance).
-- ---------------------------------------------------------------------
ALTER TABLE app.profiles          ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.consents          ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.settings          ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.bookmarks         ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.reading_positions ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.worship_goals     ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.worship_progress  ENABLE ROW LEVEL SECURITY;

CREATE POLICY own_rows ON app.profiles          FOR ALL TO authenticated
  USING (user_id = (select auth.uid())) WITH CHECK (user_id = (select auth.uid()));
CREATE POLICY own_rows ON app.consents          FOR ALL TO authenticated
  USING (user_id = (select auth.uid())) WITH CHECK (user_id = (select auth.uid()));
CREATE POLICY own_rows ON app.settings          FOR ALL TO authenticated
  USING (user_id = (select auth.uid())) WITH CHECK (user_id = (select auth.uid()));
CREATE POLICY own_rows ON app.bookmarks         FOR ALL TO authenticated
  USING (user_id = (select auth.uid())) WITH CHECK (user_id = (select auth.uid()));
CREATE POLICY own_rows ON app.reading_positions FOR ALL TO authenticated
  USING (user_id = (select auth.uid())) WITH CHECK (user_id = (select auth.uid()));
CREATE POLICY own_rows ON app.worship_goals     FOR ALL TO authenticated
  USING (user_id = (select auth.uid())) WITH CHECK (user_id = (select auth.uid()));
CREATE POLICY own_rows ON app.worship_progress  FOR ALL TO authenticated
  USING (user_id = (select auth.uid()))
  WITH CHECK (user_id = (select auth.uid())
              AND EXISTS (SELECT 1 FROM app.worship_goals g
                          WHERE g.id = goal_id AND g.user_id = (select auth.uid())));

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA app TO authenticated;
REVOKE ALL ON ALL TABLES IN SCHEMA app FROM anon;

-- ---------------------------------------------------------------------
-- Account deletion queue (processed by Edge Function + pg_cron, service role)
-- ---------------------------------------------------------------------
CREATE TABLE app.deletion_requests (
  user_id             UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  requested_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  scheduled_purge_at  TIMESTAMPTZ NOT NULL DEFAULT now() + interval '14 days'
);
ALTER TABLE app.deletion_requests ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_rows ON app.deletion_requests FOR ALL TO authenticated
  USING (user_id = (select auth.uid())) WITH CHECK (user_id = (select auth.uid()));

-- ---------------------------------------------------------------------
-- api.* — client contract (signatures only; bodies written in package 1.10)
--   api.sync_push_v1(p_changes jsonb)                  RETURNS jsonb
--   api.sync_pull_v1(p_since bigint, p_limit int)      RETURNS jsonb
--   api.set_consent_v1(p_category text, p_granted bool, p_policy_version text) RETURNS jsonb
--   api.get_me_v1()                                    RETURNS jsonb
--   api.request_deletion_v1()                          RETURNS jsonb
--   api.cancel_deletion_v1()                           RETURNS void
-- All SECURITY INVOKER (RLS applies), SET search_path = '', input-validated,
-- max 200 changes / call. Breaking changes => new _v2 function, _v1 kept.
-- ---------------------------------------------------------------------
