-- Initial reference schema only. Review, normalize, and migrate before production.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  google_subject VARCHAR(255) UNIQUE NOT NULL,
  display_name VARCHAR(200),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);

CREATE TABLE user_settings (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  language VARCHAR(12) NOT NULL DEFAULT 'ar',
  theme VARCHAR(20) NOT NULL DEFAULT 'system',
  calculation_method VARCHAR(80),
  madhab VARCHAR(40),
  timezone VARCHAR(80),
  sync_enabled BOOLEAN NOT NULL DEFAULT false,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE prayer_preferences (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  location_mode VARCHAR(20) NOT NULL DEFAULT 'manual',
  notification_settings JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
  CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE TABLE worship_goals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  goal_type VARCHAR(80) NOT NULL,
  target_value INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE worship_progress (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  goal_id UUID REFERENCES worship_goals(id) ON DELETE SET NULL,
  local_date DATE NOT NULL,
  progress_value INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, goal_id, local_date)
);

CREATE TABLE content_sources (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(300) NOT NULL,
  publisher VARCHAR(300),
  source_url TEXT,
  license_info TEXT,
  attribution_required BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE quran_surahs (
  surah_number INTEGER PRIMARY KEY CHECK (surah_number BETWEEN 1 AND 114),
  arabic_name VARCHAR(120) NOT NULL,
  ayah_count INTEGER NOT NULL CHECK (ayah_count > 0)
);

CREATE TABLE quran_ayahs (
  surah_number INTEGER NOT NULL REFERENCES quran_surahs(surah_number),
  ayah_number INTEGER NOT NULL CHECK (ayah_number > 0),
  uthmani_text TEXT NOT NULL,
  source_id UUID REFERENCES content_sources(id),
  content_revision VARCHAR(80),
  PRIMARY KEY (surah_number, ayah_number)
);

CREATE TABLE hadith_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_id UUID NOT NULL REFERENCES content_sources(id),
  collection VARCHAR(200) NOT NULL,
  book_number VARCHAR(100),
  hadith_number VARCHAR(100),
  arabic_text TEXT NOT NULL,
  grading VARCHAR(200),
  grading_source TEXT,
  content_revision VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tafsir_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_id UUID NOT NULL REFERENCES content_sources(id),
  surah_number INTEGER NOT NULL,
  ayah_number INTEGER NOT NULL,
  language VARCHAR(12) NOT NULL DEFAULT 'ar',
  text_content TEXT NOT NULL,
  content_revision VARCHAR(80),
  FOREIGN KEY (surah_number, ayah_number)
    REFERENCES quran_ayahs(surah_number, ayah_number)
);

CREATE TABLE user_bookmarks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  item_type VARCHAR(40) NOT NULL,
  item_key VARCHAR(300) NOT NULL,
  note_encrypted BYTEA,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, item_type, item_key)
);

CREATE TABLE recovery_goals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  goal_type VARCHAR(80) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  archived_at TIMESTAMPTZ
);

-- Keep sensitive recovery records local by default. This table is only used
-- if the user explicitly opts into server sync.
CREATE TABLE recovery_checkins (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  event_date DATE NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  notes_encrypted BYTEA,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE alarm_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_time TIME NOT NULL,
  timezone VARCHAR(80),
  alarm_mode VARCHAR(40) NOT NULL DEFAULT 'gentle',
  verification_mode VARCHAR(40) NOT NULL DEFAULT 'task',
  enabled BOOLEAN NOT NULL DEFAULT true,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE alarm_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  scheduled_at TIMESTAMPTZ NOT NULL,
  first_interaction_at TIMESTAMPTZ,
  task_completed_at TIMESTAMPTZ,
  outcome VARCHAR(40),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE protection_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  profile_name VARCHAR(120) NOT NULL,
  protection_mode VARCHAR(40) NOT NULL DEFAULT 'standard',
  enabled BOOLEAN NOT NULL DEFAULT false,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE blocked_domains (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  domain VARCHAR(253) NOT NULL,
  category VARCHAR(80) NOT NULL,
  source_id UUID REFERENCES content_sources(id),
  list_revision VARCHAR(80),
  active BOOLEAN NOT NULL DEFAULT true,
  UNIQUE (domain, category)
);

CREATE TABLE content_releases (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  content_area VARCHAR(80) NOT NULL,
  version VARCHAR(100) NOT NULL,
  checksum VARCHAR(128) NOT NULL,
  published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (content_area, version)
);

CREATE TABLE audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  event_type VARCHAR(120) NOT NULL,
  target_type VARCHAR(120),
  target_id VARCHAR(255),
  correlation_id VARCHAR(120),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_worship_progress_user_date
  ON worship_progress(user_id, local_date DESC);
CREATE INDEX idx_hadith_source_collection
  ON hadith_entries(source_id, collection);
CREATE INDEX idx_recovery_checkins_user_date
  ON recovery_checkins(user_id, event_date DESC);
CREATE INDEX idx_alarm_sessions_user_schedule
  ON alarm_sessions(user_id, scheduled_at DESC);
CREATE INDEX idx_blocked_domains_active
  ON blocked_domains(active, domain);
