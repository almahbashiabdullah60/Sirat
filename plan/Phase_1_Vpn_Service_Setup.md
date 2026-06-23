# المرحلة 1: إعداد خدمة الـ VPN المحلية وهيكل المحرك

## الهدف
إنشاء الهيكل البرمجي لخدمة `VpnService` الخاصة بأندرويد، وتصريحها في ملف `AndroidManifest.xml` وتكوين واجهة شبكة افتراضية (TUN) لاعتراض حركة مرور الـ DNS فقط، مع تفعيل عناصر التحكم لبدء وإيقاف الخدمة.

---

## 🛠️ التعديلات البرمجية المطلوبة وهيكل الملفات

### 1. [ملف جديد] إنشاء ملف `SiratVpnService.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/services/vpn/SiratVpnService.kt`

هذه الخدمة تعمل في الخلفية وتقوم بتهيئة واجهة الشبكة الافتراضية (TUN).

**تفاصيل التنفيذ:**
- وراثة الكلاس `android.net.VpnService`.
- الحفاظ على كروتينة (Coroutine) مستمرة لقراءة الحزم عبر مكتبة **dnsjava** (تتم المعالجة في المرحلة 2).
- تكوين واجهة الـ TUN لاعتراض **طلبات الـ DNS فقط** لتقليل استهلاك البطارية والحفاظ على الأداء وسرعة الإنترنت:
  - تعيين عنوان IP للـ TUN: `10.8.0.2/32`.
  - تعيين خادم DNS للـ TUN: `10.8.0.1`.
  - إضافة مسار (Route): `10.8.0.1/32` (هذا يضمن أن الحزم الموجهة فقط لخادم الـ DNS الافتراضي `10.8.0.1` هي التي تدخل نفق الـ VPN، بينما تمر بقية حركات مرور الإنترنت مباشرة بسرعة الجهاز الطبيعية).
  - تشغيل الخدمة كـ Foreground Service مع إشعار دائم (يستخدم نفس قناة الإشعارات الموجودة للتطبيق، أو يقنًاة جديدة باسم `vpn_filter`).

**هام - التفاعل مع الخدمات الحالية:** هذه الخدمة مستقلة ولا تعتمد على `AppLockAccessibilityService` أو `ShizukuAppLockService`. يجب إعلام `AppLockManager` بحالة الخدمة (شغالة/متوقفة) لتنسيق الموارد ومنع تعارضات البطارية. لا تؤثر الـ VPN على آلية كشف التطبيقات المفتوحة.

### 2. [تعديل] تعديل ملف `AndroidManifest.xml`
المسار: `app/src/main/AndroidManifest.xml`

إضافة صلاحية الـ VPN وتصريح الخدمة (ملاحظة: `VpnService` لا يتطلب `BIND_VPN_SERVICE` - هذا فقط لـ `AccessibilityService`):

```xml
<!-- تحت وسم <manifest> -->
<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />

<!-- تحت وسم <application> -->
<service
    android:name=".services.vpn.SiratVpnService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="vpn_dns_filter" />
</service>
```

```xml
<!-- إضافة قناة الإشعارات في ملف strings.xml -->
<string name="channel_vpn_filter">تصفية الإنترنت</string>
<string name="channel_vpn_filter_description">إشعار خدمة تصفية المحتوى عبر VPN</string>
```

### 3. [ملف جديد] إنشاء ملف `VpnController.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/services/vpn/VpnController.kt`

كلاس مساعد لإدارة صلاحيات الـ VPN وتشغيل/إيقاف الخدمة من واجهة المستخدم بسهولة.

**المهام:**
- التحقق من منح صلاحية الـ VPN عبر `VpnService.prepare(context)`.
- إذا لم تكن الصلاحية ممنوحة، إرجاع الـ Intent المناسب (مع `ActivityResultLauncher`) لفتح نافذة إعدادات النظام وتفعيل الـ VPN.
- دوال مساعدة لإرسال Intents تشغيل/إيقاف الخدمة.
- استدعاء `AppLockManager` عند تغيير الحالة لتسجيلها.

---

## 🏁 خطة التحقق والطلب (Verification Plan)

### التحقق اليدوي
1. تشغيل التطبيق والضغط على زر "تفعيل الحظر" (الذي يستدعي `VpnController.start`).
2. التأكد من ظهور نافذة النظام المعتادة لطلب الاتصال بشبكة VPN ("طلب اتصال").
3. الموافقة والتأكد من ظهور أيقونة "المفتاح" في شريط الحالة العلوي للهاتف.
4. التحقق من وجود إشعار دائم غير قابل للمسح في شريط الإشعارات يدل على تشغيل الخدمة.
5. إيقاف الخدمة والتأكد من اختفاء الإشعار وأيقونة المفتاح.
