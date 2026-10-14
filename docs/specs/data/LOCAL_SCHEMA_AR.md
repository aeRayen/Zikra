# مخطط البيانات المحلية (Room) — الحزمة 0b.5

**المرجع:** ADR-006.

**قواعد عامة لكل القواعد الثلاث:**

- تصدير المخطط (`exportSchema = true`) مُفعَّل، وملفات JSON محفوظة في git.
- كل ترحيل له اختبار `MigrationTestHelper`.
- **ممنوع** `fallbackToDestructiveMigration` في الإنتاج.

---

## 1. `content.db`: محتوى للقراءة فقط

- نسخة أولى مُضمَّنة في APK (`createFromAsset`) تحتوي نص القرآن العربي ومعلوماته البنيوية.
- الحزم الإضافية (الترجمات، والتفسير، والأذكار) تُنزَّل كملفات SQLite موقّعة، وتُربط بـ `ATTACH`، أو تُنسخ بعد التحقق.
- التطبيق **لا يكتب** في هذه القاعدة إلا من خلال مُثبِّت الحزم.

| الجدول | الأعمدة الرئيسية | ملاحظات |
|---|---|---|
| `languages` | `tag` PK، و`native_name`، و`direction` | |
| `sources` | `id` PK، و`name`، و`license_code`، و`attribution_text`، و`source_url` | لشاشة «عن هذا النص» |
| `quran_surah` | `surah` PK، و`ayah_count`، و`revelation_place`، و`revelation_order` | |
| `quran_surah_name` | (`surah`, `language`) PK، و`name` | |
| `quran_ayah_meta` | (`surah`, `ayah`) PK، و`juz`، و`hizb_quarter`، و`page`، و`sajda` | |
| `quran_edition` | `id` PK، و`code`، و`kind`، و`language`، و`title`، و`author`، و`source_id`، و`checksum_sha256` | |
| `quran_ayah_text` | (`edition_id`, `surah`, `ayah`) PK، و`text` | يُعرض كما هو حرفيًا |
| `quran_ayah_fts` | جدول FTS4 على `search_text`، مع `content=` يشير إلى جدول ظل | التطبيع حسب `I18N_SPEC_AR.md` §10 |
| `tafsir_work` و`tafsir_entry` | كما في الخادم | |
| `adhkar_category` و`adhkar_category_name` و`dhikr` و`dhikr_translation` | كما في الخادم | |
| `installed_pack` | `pack_id` PK، و`area`، و`language`، و`edition_code`، و`version`، و`manifest_sha256`، و`installed_at` | سجل الحزم المُثبتة |

---

## 2. `user.db`: بيانات المستخدم العادية

كل جدول قابل للمزامنة يحتوي الأعمدة التالية:

```
id TEXT (UUID) PK  ·  client_updated_at INTEGER (epoch ms)
server_version INTEGER NULL  ·  deleted INTEGER (0/1)  ·  dirty INTEGER (0/1)
```

| الجدول | الأعمدة الرئيسية | يُزامَن؟ (فئة الموافقة) |
|---|---|---|
| `bookmark` | `item_type`، و`item_key`، و`created_at` | نعم (`sync.bookmarks`) |
| `bookmark_note` | `bookmark_id` FK، و`text` | **لا**، محلي فقط (ADR-012) |
| `reading_position` | `surah`، و`ayah` | نعم (`sync.reading`) |
| `worship_goal` | `goal_type`، و`title`، و`target_value`، و`period`، و`archived` | نعم (`sync.worship`) |
| `worship_progress` | `goal_id` FK، و`local_date` (TEXT ISO)، و`value`، مع UNIQUE(`goal_id`, `local_date`) | نعم (`sync.worship`) |
| `dhikr_counter` | `dhikr_id`، و`local_date`، و`count` | لا |
| `alarm_profile` | `label`، و`time_local`، و`repeat_days` (bitmask)، و`sound`، و`task_type`، و`task_config`، و`snooze_minutes`، و`max_snoozes`، و`escalation`، و`enabled` | لا (في الإصدار الأول) |
| `alarm_session` | `profile_id` FK، و`scheduled_at`، و`rang_at`، و`first_interaction_at`، و`task_completed_at`، و`snooze_count`، و`checkin_result`، و`confidence` | لا |
| `scheduled_alarm` | `request_code` PK، و`kind` (adhan/alarm)، و`ref_id`، و`trigger_at_utc` | لا. يُستخدم لإعادة الجدولة بعد الإقلاع |
| `sync_outbox` | `seq` PK AUTOINCREMENT، و`entity`، و`entity_id`، و`op`، و`idempotency_key`، و`attempts`، و`last_error` | — |
| `sync_state` | `key` PK، و`value` (المؤشر، ووقت آخر مزامنة) | — |

**الإعدادات** لا تُخزَّن في Room. مكانها Proto DataStore (`settings.pb`)، وتُزامَن ككيان `settings` واحد.

**ملاحظة الإقلاع المباشر:** جدولة المنبهات تُنسخ إلى ملف صغير في **Device-Protected Storage**، لأن `user.db` العادية غير متاحة قبل أول فتح للقفل.

---

## 3. `private.db`: مشفرة بـ SQLCipher (المراحل 4 و5)

- **المفتاح:** 256 بت عشوائي، ملفوف بمفتاح AES-GCM من Android Keystore، ولا يغادر الجهاز.
- **مستثناة من النسخ الاحتياطي:** في `data_extraction_rules.xml` و`backup_rules.xml`.
- **الجداول تُعرَّف هنا فقط**، وتُنشأ في مراحلها. لا تُضاف الآن جداول فارغة بلا استخدام.

| الجدول | المرحلة | الأعمدة الرئيسية |
|---|---|---|
| `protection_event` | 4 | `at`، و`kind` (started/stopped/revoked/conflict/settings_changed)، و`detail_code` |
| `protection_exception` | 4 | `domain`، و`added_at`، و`expires_at` |
| `recovery_plan` | 5 | `id`، و`title`، و`steps_json`، و`updated_at` |
| `recovery_trigger` | 5 | `id`، و`at`، و`trigger_code`، و`intensity`، و`note` (اختياري) |
| `recovery_checkin` | 5 | `id`، و`local_date`، و`kind`، و`note` |
| `recovery_intervention` | 5 | `id`، و`at`، و`type`، و`completed` |
| `trusted_contact` | 5 | `id`، و`display_name`، و`channel_hint`. لا تُرسل أي بيانات آليًا |

---

## 4. التطابق مع الخادم

| محلي | خادم | التحويل |
|---|---|---|
| `bookmark` | `userdata.bookmarks` | `deleted=1` يقابل `deleted_at` |
| `reading_position` | `userdata.reading_positions` | |
| `worship_goal` و`worship_progress` | `userdata.worship_*` | |
| `settings.pb` | `userdata.settings` | الإحداثيات تُقرَّب إلى 0.1° قبل الإرسال (ADR-018) |
