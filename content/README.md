# مصادر المحتوى الديني — «ذِكْرَى»

هذا المجلد يحتوي **المصادر الأصلية كما نُزّلت، دون أي تعديل** (ADR-027). المحتوى لا يدخل التطبيق إلا عبر أداة بناء الحزم `android/tooling/content-pipeline`، وهي:

1. تتحقق من بصمة كل ملف مقابل `sources/SOURCES.lock.json`، وترفض أي اختلاف.
2. تتحقق من البنية: 114 سورة، و6236 آية، وتطابق عدد آيات كل سورة.
3. تبني حزمة SQLite وmanifest، وتوقّعه بمفتاح Ed25519 لا يوجد في المستودع.

## الإسناد (Attribution) والتراخيص

| الملف | المصدر | الترخيص والشروط |
|---|---|---|
| `sources/tanzil/quran-uthmani.txt` | Tanzil Project — https://tanzil.net | CC BY 3.0: نسخ حرفي فقط، وذكر المصدر، ورابط إلى tanzil.net. إشعار الحقوق في آخر الملف |
| `sources/tanzil/quran-data.xml` | Tanzil Project | CC BY |
| `sources/quranenc/english_saheeh_v1.1.2.json` | Noor International Center (Saheeh International) — QuranEnc.com | لا تعديل، وذكر الناشر والمصدر والإصدار، والتحديث، ولا إعلانات غير لائقة |

**ممنوع تعديل أي ملف في `sources/`.** التحديث يكون بتنزيل الإصدار الجديد من المصدر، ثم التحقق منه، ثم تحديث `SOURCES.lock.json` في طلب دمج مستقل.

ملفات `sources/` محمية من تحويل نهايات الأسطر في `.gitattributes`، فتبقى بصماتها ثابتة على كل نظام تشغيل.

## مفاتيح التوقيع

| المفتاح | المكان |
|---|---|
| **العام**: `keys/zikra-content-95fdefc5b8026097.pub` | في المستودع، لأن التطبيق يحتاجه للتحقق من الحزم |
| **الخاص** | **خارج المستودع فقط**، على جهاز بناء المحتوى (`Islamic App\content-signing\`). **يجب الاحتفاظ بنسخة احتياطية آمنة منه.** إن ضاع يلزم إصدار مفتاح جديد وتحديث التطبيق |

## الاستخدام

من مجلد `android/`، وبمسار لا يحتوي مسافات (انظر CONTRIBUTING §7):

```
gradlew.bat :tooling:content-pipeline:run --args="quran --sources ../content/sources --out <dir> --private <private.key> --public ../content/keys/zikra-content-95fdefc5b8026097.pub --version 2026.10.1"
gradlew.bat :tooling:content-pipeline:run --args="english ..."
gradlew.bat :tooling:content-pipeline:run --args="verify --pack <dir> --public ../content/keys/zikra-content-95fdefc5b8026097.pub"
```

كل حزمة تتكوّن من `pack.sqlite` و`manifest.signed.json`. **الحزم لا تُحفظ في المستودع**، بل تُرفع إلى CDN (ADR-024).
