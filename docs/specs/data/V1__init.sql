-- NOTE (ADR-024): identity/userdata/ops sections are SUPERSEDED by data/supabase/V1__init.sql.
-- The content.* section remains the design for the content pipeline / CMS.
-- =====================================================================
-- Islamic Life OS — V1 schema DESIGN DRAFT (Phase 0b, package 0b.4)
-- Target: PostgreSQL 16+.  Supersedes database/initial_schema.sql (ADR-005).
-- STATUS: designed, NOT executed. It must pass "apply on empty PG" in CI
-- (work package 1.10) before it is considered valid.
--
-- Not in V1 by decision:
--   * recovery_* / protection_* tables      (ADR-011, local-only)
--   * bookmark notes                        (ADR-012, local-only)
--   * cms.* tables, hadith, library         (V2, Phase 3)
--   * alarm tables                          (local-only in v1)
-- DB roles/grants are created by infra scripts, not by migrations.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS userdata;
CREATE SCHEMA IF NOT EXISTS content;
CREATE SCHEMA IF NOT EXISTS ops;

CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- search on normalized columns

-- ---------------------------------------------------------------------
-- Shared helpers
-- ---------------------------------------------------------------------
CREATE SEQUENCE userdata.server_version_seq AS BIGINT;

-- Bumps server_version and updated_at on every insert/update of a synced row.
CREATE FUNCTION userdata.bump_version() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.server_version := nextval('userdata.server_version_seq');
  NEW.updated_at     := now();
  RETURN NEW;
END;
$$;

CREATE FUNCTION ops.touch_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

-- =====================================================================
-- identity
-- =====================================================================
CREATE TABLE identity.users (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  google_subject  TEXT NOT NULL,
  display_name    VARCHAR(100),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT users_google_subject_uq UNIQUE (google_subject)
);
-- Deletion is a HARD delete after the grace period (fixes D1);
-- the pending state lives in ops.deletion_requests.
CREATE TRIGGER users_touch BEFORE UPDATE ON identity.users
  FOR EACH ROW EXECUTE FUNCTION ops.touch_updated_at();

CREATE TABLE identity.auth_nonces (
  nonce_hash   BYTEA PRIMARY KEY,                 -- SHA-256 of nonce
  expires_at   TIMESTAMPTZ NOT NULL,
  consumed_at  TIMESTAMPTZ
);
CREATE INDEX auth_nonces_expiry_idx ON identity.auth_nonces (expires_at);

CREATE TABLE identity.auth_sessions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
  family_id           UUID NOT NULL,              -- rotation family
  refresh_token_hash  BYTEA NOT NULL,             -- SHA-256; raw token never stored
  platform            VARCHAR(10) NOT NULL CHECK (platform IN ('android','ios')),
  device_label        VARCHAR(80),
  app_version         VARCHAR(32),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at          TIMESTAMPTZ NOT NULL,
  revoked_at          TIMESTAMPTZ,
  revoked_reason      VARCHAR(30) CHECK (revoked_reason IN
                        ('logout','user_revoked','rotated','reuse_detected','account_deleted','expired')),
  CONSTRAINT auth_sessions_token_uq UNIQUE (refresh_token_hash)
);
CREATE INDEX auth_sessions_user_active_idx
  ON identity.auth_sessions (user_id) WHERE revoked_at IS NULL;
CREATE INDEX auth_sessions_family_idx ON identity.auth_sessions (family_id);

-- =====================================================================
-- userdata  (all tables: client-generated UUID, server_version, tombstone)
-- =====================================================================
CREATE TABLE userdata.consents (
  user_id         UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
  category        VARCHAR(30) NOT NULL CHECK (category IN
                    ('sync.settings','sync.bookmarks','sync.reading','sync.worship','crash_reports')),
  granted         BOOLEAN NOT NULL,
  policy_version  VARCHAR(20) NOT NULL,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, category)
);

CREATE TABLE userdata.settings (
  user_id              UUID PRIMARY KEY REFERENCES identity.users(id) ON DELETE CASCADE,
  language             VARCHAR(35),
  theme                VARCHAR(10) NOT NULL DEFAULT 'system'
                         CHECK (theme IN ('system','light','dark')),
  numeral_system       VARCHAR(10) NOT NULL DEFAULT 'auto'
                         CHECK (numeral_system IN ('auto','latn','arab','arabext')),
  calculation_method   VARCHAR(40),
  asr_madhab           VARCHAR(10) CHECK (asr_madhab IN ('standard','hanafi')),
  high_latitude_rule   VARCHAR(20) CHECK (high_latitude_rule IN
                         ('none','middle_of_night','seventh_of_night','twilight_angle')),
  hijri_offset_days    SMALLINT NOT NULL DEFAULT 0 CHECK (hijri_offset_days BETWEEN -2 AND 2),
  timezone             VARCHAR(64),
  city_id              VARCHAR(40),
  -- ADR-018: at most 0.1 degree precision on the server (fixes D4/C7)
  lat_rounded          NUMERIC(4,1) CHECK (lat_rounded BETWEEN -90 AND 90),
  lon_rounded          NUMERIC(4,1) CHECK (lon_rounded BETWEEN -180 AND 180),
  client_updated_at    TIMESTAMPTZ NOT NULL,
  server_version       BIGINT NOT NULL,
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER settings_bump BEFORE INSERT OR UPDATE ON userdata.settings
  FOR EACH ROW EXECUTE FUNCTION userdata.bump_version();

CREATE TABLE userdata.bookmarks (
  id                 UUID PRIMARY KEY,                       -- client-generated
  user_id            UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
  item_type          VARCHAR(20) NOT NULL CHECK (item_type IN ('quran_ayah','hadith','article','dhikr')),
  item_key           VARCHAR(200) NOT NULL,
  client_updated_at  TIMESTAMPTZ NOT NULL,
  deleted_at         TIMESTAMPTZ,                            -- tombstone
  server_version     BIGINT NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- one live bookmark per item; tombstones excluded
CREATE UNIQUE INDEX bookmarks_live_uq
  ON userdata.bookmarks (user_id, item_type, item_key) WHERE deleted_at IS NULL;
CREATE INDEX bookmarks_sync_idx ON userdata.bookmarks (user_id, server_version);
CREATE TRIGGER bookmarks_bump BEFORE INSERT OR UPDATE ON userdata.bookmarks
  FOR EACH ROW EXECUTE FUNCTION userdata.bump_version();

CREATE TABLE userdata.reading_positions (
  id                 UUID PRIMARY KEY,
  user_id            UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
  surah              SMALLINT NOT NULL CHECK (surah BETWEEN 1 AND 114),
  ayah               SMALLINT NOT NULL CHECK (ayah BETWEEN 1 AND 286),
  client_updated_at  TIMESTAMPTZ NOT NULL,
  deleted_at         TIMESTAMPTZ,
  server_version     BIGINT NOT NULL,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX reading_positions_user_uq ON userdata.reading_positions (user_id)
  WHERE deleted_at IS NULL;                                    -- one "continue reading" per user
CREATE INDEX reading_positions_sync_idx ON userdata.reading_positions (user_id, server_version);
CREATE TRIGGER reading_positions_bump BEFORE INSERT OR UPDATE ON userdata.reading_positions
  FOR EACH ROW EXECUTE FUNCTION userdata.bump_version();

CREATE TABLE userdata.worship_goals (                         -- fixes D7
  id                 UUID PRIMARY KEY,
  user_id            UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
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
CREATE INDEX worship_goals_sync_idx ON userdata.worship_goals (user_id, server_version);
CREATE TRIGGER worship_goals_bump BEFORE INSERT OR UPDATE ON userdata.worship_goals
  FOR EACH ROW EXECUTE FUNCTION userdata.bump_version();

CREATE TABLE userdata.worship_progress (                      -- fixes D6
  id                 UUID PRIMARY KEY,
  user_id            UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
  goal_id            UUID NOT NULL REFERENCES userdata.worship_goals(id) ON DELETE CASCADE,
  local_date         DATE NOT NULL,
  value              INTEGER NOT NULL DEFAULT 0 CHECK (value >= 0),
  client_updated_at  TIMESTAMPTZ NOT NULL,
  deleted_at         TIMESTAMPTZ,
  server_version     BIGINT NOT NULL,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX worship_progress_day_uq
  ON userdata.worship_progress (goal_id, local_date) WHERE deleted_at IS NULL;
CREATE INDEX worship_progress_sync_idx ON userdata.worship_progress (user_id, server_version);
CREATE TRIGGER worship_progress_bump BEFORE INSERT OR UPDATE ON userdata.worship_progress
  FOR EACH ROW EXECUTE FUNCTION userdata.bump_version();

-- =====================================================================
-- content  (public content; written only by the content pipeline / CMS)
-- =====================================================================
CREATE TABLE content.languages (
  tag              VARCHAR(35) PRIMARY KEY,                    -- BCP 47
  native_name      VARCHAR(80) NOT NULL,
  direction        VARCHAR(3) NOT NULL CHECK (direction IN ('rtl','ltr')),
  enabled          BOOLEAN NOT NULL DEFAULT false,
  ui_completeness  SMALLINT NOT NULL DEFAULT 0 CHECK (ui_completeness BETWEEN 0 AND 100)
);

CREATE TABLE content.sources (                                -- fixes D12
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name                 VARCHAR(300) NOT NULL,
  publisher            VARCHAR(300),
  source_url           TEXT,
  license_code         VARCHAR(60) NOT NULL,                  -- e.g. SPDX id or LicenseRef-*
  license_url          TEXT,
  attribution_text     TEXT,
  allows_offline       BOOLEAN NOT NULL,
  allows_modification  BOOLEAN NOT NULL,
  verified_at          TIMESTAMPTZ,                           -- licence verified by a human
  verified_by          VARCHAR(120),
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Structural Quran data (independent of edition) — fixes D8/D9
CREATE TABLE content.quran_surahs (
  surah             SMALLINT PRIMARY KEY CHECK (surah BETWEEN 1 AND 114),
  ayah_count        SMALLINT NOT NULL CHECK (ayah_count > 0),
  revelation_place  VARCHAR(10) NOT NULL CHECK (revelation_place IN ('makkah','madinah')),
  revelation_order  SMALLINT NOT NULL CHECK (revelation_order BETWEEN 1 AND 114) UNIQUE
);

CREATE TABLE content.quran_surah_names (
  surah     SMALLINT NOT NULL REFERENCES content.quran_surahs(surah),
  language  VARCHAR(35) NOT NULL REFERENCES content.languages(tag),
  name      VARCHAR(120) NOT NULL,
  PRIMARY KEY (surah, language)
);

CREATE TABLE content.quran_ayah_meta (
  surah         SMALLINT NOT NULL REFERENCES content.quran_surahs(surah),
  ayah          SMALLINT NOT NULL CHECK (ayah > 0),
  juz           SMALLINT NOT NULL CHECK (juz BETWEEN 1 AND 30),
  hizb_quarter  SMALLINT NOT NULL CHECK (hizb_quarter BETWEEN 1 AND 240),
  page          SMALLINT CHECK (page > 0),                    -- edition-specific; nullable until layout licensed
  sajda         BOOLEAN NOT NULL DEFAULT false,
  PRIMARY KEY (surah, ayah)
);
-- Totals (114 surahs / 6236 ayahs, per-surah counts) are enforced by the
-- import verification tool + CI test, not by DB constraints.

CREATE TABLE content.quran_editions (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code       VARCHAR(60) NOT NULL UNIQUE,                    -- e.g. 'ar.hafs.uthmani'
  kind       VARCHAR(12) NOT NULL CHECK (kind IN ('text','translation')),
  language   VARCHAR(35) NOT NULL REFERENCES content.languages(tag),
  riwaya     VARCHAR(40),                                    -- for kind='text'
  script     VARCHAR(40),
  title      VARCHAR(200) NOT NULL,
  author     VARCHAR(200),                                   -- translator, for kind='translation'
  source_id  UUID NOT NULL REFERENCES content.sources(id),   -- NOT NULL (fixes D9)
  checksum_sha256 CHAR(64) NOT NULL,
  status     VARCHAR(12) NOT NULL DEFAULT 'draft'
               CHECK (status IN ('draft','in_review','published','withdrawn'))
);

CREATE TABLE content.quran_ayah_texts (
  edition_id   UUID NOT NULL REFERENCES content.quran_editions(id) ON DELETE CASCADE,
  surah        SMALLINT NOT NULL,
  ayah         SMALLINT NOT NULL,
  text         TEXT NOT NULL,                                -- displayed verbatim, never modified
  search_text  TEXT NOT NULL,                                -- normalized copy (I18N_SPEC §10)
  PRIMARY KEY (edition_id, surah, ayah),
  FOREIGN KEY (surah, ayah) REFERENCES content.quran_ayah_meta(surah, ayah)
);
CREATE INDEX quran_ayah_texts_search_trgm
  ON content.quran_ayah_texts USING gin (search_text gin_trgm_ops);

CREATE TABLE content.tafsir_works (                           -- fixes D11
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code       VARCHAR(60) NOT NULL UNIQUE,
  language   VARCHAR(35) NOT NULL REFERENCES content.languages(tag),
  title      VARCHAR(200) NOT NULL,
  author     VARCHAR(200),
  source_id  UUID NOT NULL REFERENCES content.sources(id),
  status     VARCHAR(12) NOT NULL DEFAULT 'draft'
               CHECK (status IN ('draft','in_review','published','withdrawn'))
);

CREATE TABLE content.tafsir_entries (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  work_id     UUID NOT NULL REFERENCES content.tafsir_works(id) ON DELETE CASCADE,
  surah       SMALLINT NOT NULL,
  ayah_from   SMALLINT NOT NULL,
  ayah_to     SMALLINT NOT NULL,
  text        TEXT NOT NULL,
  CHECK (ayah_to >= ayah_from),
  FOREIGN KEY (surah, ayah_from) REFERENCES content.quran_ayah_meta(surah, ayah),
  FOREIGN KEY (surah, ayah_to)   REFERENCES content.quran_ayah_meta(surah, ayah),
  UNIQUE (work_id, surah, ayah_from)
);
CREATE INDEX tafsir_entries_lookup_idx ON content.tafsir_entries (surah, ayah_from, ayah_to);

CREATE TABLE content.adhkar_categories (
  id          VARCHAR(40) PRIMARY KEY,                       -- 'morning','evening','after_prayer',...
  sort_order  SMALLINT NOT NULL
);

CREATE TABLE content.adhkar_category_names (
  category_id  VARCHAR(40) NOT NULL REFERENCES content.adhkar_categories(id),
  language     VARCHAR(35) NOT NULL REFERENCES content.languages(tag),
  name         VARCHAR(120) NOT NULL,
  PRIMARY KEY (category_id, language)
);

CREATE TABLE content.adhkar (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  category_id     VARCHAR(40) NOT NULL REFERENCES content.adhkar_categories(id),
  sort_order      SMALLINT NOT NULL,
  arabic_text     TEXT NOT NULL,
  repetitions     SMALLINT NOT NULL DEFAULT 1 CHECK (repetitions > 0),
  reference_text  TEXT NOT NULL,                             -- e.g. collection + number
  source_id       UUID NOT NULL REFERENCES content.sources(id),
  reviewed_by     VARCHAR(120),
  status          VARCHAR(12) NOT NULL DEFAULT 'draft'
                    CHECK (status IN ('draft','in_review','published','withdrawn'))
);

CREATE TABLE content.adhkar_translations (
  dhikr_id         UUID NOT NULL REFERENCES content.adhkar(id) ON DELETE CASCADE,
  language         VARCHAR(35) NOT NULL REFERENCES content.languages(tag),
  translation      TEXT NOT NULL,
  transliteration  TEXT,
  source_id        UUID NOT NULL REFERENCES content.sources(id),
  status           VARCHAR(12) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','in_review','published','withdrawn')),
  PRIMARY KEY (dhikr_id, language)
);

CREATE TABLE content.releases (                               -- fixes D19
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  area             VARCHAR(30) NOT NULL CHECK (area IN
                     ('quran_text','quran_translation','tafsir','adhkar','hadith','library','blocklist')),
  language         VARCHAR(35) REFERENCES content.languages(tag),
  edition_code     VARCHAR(60),
  version          VARCHAR(40) NOT NULL,
  size_bytes       BIGINT NOT NULL CHECK (size_bytes > 0),
  manifest_sha256  CHAR(64) NOT NULL,
  signature        TEXT NOT NULL,                            -- base64 Ed25519
  key_id           VARCHAR(40) NOT NULL,
  status           VARCHAR(12) NOT NULL DEFAULT 'draft'
                     CHECK (status IN ('draft','published','revoked')),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at     TIMESTAMPTZ,
  CONSTRAINT releases_version_uq UNIQUE NULLS NOT DISTINCT (area, language, edition_code, version)
);
CREATE INDEX releases_latest_idx ON content.releases (area, language, published_at DESC)
  WHERE status = 'published';

-- =====================================================================
-- ops
-- =====================================================================
CREATE TABLE ops.idempotency_keys (
  user_id          UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
  key              UUID NOT NULL,
  request_sha256   CHAR(64) NOT NULL,                        -- same key + different body => 422
  response_status  SMALLINT,
  response_body    JSONB,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, key)
);
CREATE INDEX idempotency_keys_age_idx ON ops.idempotency_keys (created_at);  -- purge > 48h

CREATE TABLE ops.deletion_requests (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID REFERENCES identity.users(id) ON DELETE SET NULL,  -- NULL after purge
  status              VARCHAR(12) NOT NULL CHECK (status IN ('pending','cancelled','completed')),
  requested_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  scheduled_purge_at  TIMESTAMPTZ NOT NULL,
  completed_at        TIMESTAMPTZ
);
CREATE UNIQUE INDEX deletion_requests_pending_uq
  ON ops.deletion_requests (user_id) WHERE status = 'pending';

CREATE TABLE ops.export_jobs (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
  status        VARCHAR(10) NOT NULL CHECK (status IN ('queued','running','ready','failed','expired')),
  object_key    TEXT,                                        -- private storage key, never public
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at  TIMESTAMPTZ,
  expires_at    TIMESTAMPTZ
);
CREATE INDEX export_jobs_user_idx ON ops.export_jobs (user_id, created_at DESC);

CREATE TABLE ops.audit_events (                               -- fixes D20
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id   UUID REFERENCES identity.users(id) ON DELETE SET NULL,
  actor_role      VARCHAR(20) NOT NULL CHECK (actor_role IN ('user','system','editor','reviewer','admin')),
  event_type      VARCHAR(80) NOT NULL,
  target_type     VARCHAR(60),
  target_id       VARCHAR(100),
  details         JSONB NOT NULL DEFAULT '{}'::jsonb,        -- redacted; never user content
  correlation_id  VARCHAR(64),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_time_idx ON ops.audit_events (created_at DESC);
CREATE INDEX audit_events_target_idx ON ops.audit_events (target_type, target_id);
