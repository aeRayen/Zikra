# سير عمل وكيل الذكاء الاصطناعي

استخدم هذا التسلسل مع وكيل البرمجة:

## الدور الأول: Architect
اقرأ المتطلبات والمستودع، واكتب:
- ملخص الوضع الحالي.
- قرارات معمارية مع مبرراتها.
- مخطط الوحدات والاعتماديات.
- مخطط بيانات مبدئي.
- المخاطر والأسئلة.
لا تعدل الملفات في هذه الخطوة.

## الدور الثاني: Foundation
بعد الموافقة، أنشئ الهيكل الأساسي فقط:
- Gradle modules.
- design system.
- navigation.
- Room.
- API client.
- Backend skeleton.
- Docker Compose.
- CI build and tests.
شغّل البناء والاختبارات وسجّل النتائج الحقيقية.

## الدور الثالث: Feature implementation
نفّذ وحدة واحدة في كل مرة:
1. Prayer / Qibla.
2. Quran / Dhikr.
3. Islamic library.
4. Protection.
5. Recovery.
6. Smart alarm.
7. AI retrieval.
لا تخلط ميزات غير مرتبطة في pull request واحد.

## الدور الرابع: Quality gate
قبل قبول كل وحدة:
- Unit tests.
- Integration tests.
- UI tests للمسارات الأساسية.
- Offline / error / permission states.
- Security and privacy review.
- Performance and battery checks عند صلة الأمر.

## قالب تقرير المرحلة
- المرحلة:
- ما تم:
- الملفات الجديدة/المعدلة:
- أوامر البناء والاختبار:
- النتائج الفعلية:
- مشكلات معروفة:
- قرارات تحتاج موافقة:
- الخطوة التالية:

لا تدّعِ تشغيل اختبار إذا لم تشغله. لا تخفِ فشل البناء. لا تستخدم بيانات وهمية في ميزات الإنتاج.
