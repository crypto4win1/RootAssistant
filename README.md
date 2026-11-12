# Root Assistant

تطبيق أندرويد لتصحيح boot.img باستخدام Magisk مع تحقق دقيق قبل التفعيل.

## البناء التلقائي
هذا المشروع مكوّن للبناء عبر GitHub Actions. عند رفع الملفات إلى GitHub، يبني الـ workflow ملف APK تلقائيًا.

## الحصول على APK
1. اذهب لتبويب Actions
2. اختر أحدث run
3. حمّل RootAssistant-debug-apk artifact

## آلية العمل
1. فحص Shizuku تلقائيًا
2. التحقق من boot.img (بصمة ANDROID! + إصدار الرأس + SHA-256)
3. تنزيل magiskboot تلقائيًا من GitHub الرسمي
4. تصحيح boot.img وإنتاج magisk_patched.img

## حدود التطبيق
- لا يفلش الصورة تلقائيًا (يتطلب Fastboot)
- لا يفتح Bootloader