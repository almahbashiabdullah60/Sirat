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
- الحفاظ على كروتينة (Coroutine) أو خيط معالجة (Thread) مستمر لقراءة الحزم الخام وكتابتها عبر واجهة الـ File Descriptor.
- تكوين واجهة الـ TUN لاعتراض **طلبات الـ DNS فقط** لتقليل استهلاك البطارية والحفاظ على الأداء وسرعة الإنترنت:
  - تعيين عنوان IP للـ TUN: `10.8.0.2/32`.
  - تعيين خادم DNS للـ TUN: `10.8.0.1`.
  - إضافة مسار (Route): `10.8.0.1/32` (هذا يضمن أن الحزم الموجهة فقط لخادم الـ DNS الافتراضي `10.8.0.1` هي التي تدخل نفق الـ VPN، بينما تمر بقية حركات مرور الإنترنت مثل الويب والتطبيقات الأخرى مباشرة بسرعة الجهاز الطبيعية دون دخول الـ VPN).
  - تشغيل الخدمة كـ Foreground Service مع إشعار دائم (مطلوب في أندرويد 8 فما فوق).

```kotlin
// هيكل برمجى مقترح لـ SiratVpnService.kt
package com.atyafcode.sirat.services.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

class SiratVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            startVpn()
        } else if (intent?.action == ACTION_STOP) {
            stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        
        // 1. تهيئة واجهة الـ TUN الافتراضية
        val builder = Builder()
            .setSession("Sirat Local DNS Filter")
            .addAddress("10.8.0.2", 32)
            .addDnsServer("10.8.0.1")
            .addRoute("10.8.0.1", 32) // التقاط حركة مرور الـ DNS فقط!
            
        // 2. إعداد الخدمة لتعمل في الواجهة (Foreground Service Notification)
        // ...
        
        vpnInterface = builder.establish()
        
        // 3. قراءة الحزم بشكل متزامن في الخلفية
        vpnJob = serviceScope.launch {
            val fileDescriptor = vpnInterface?.fileDescriptor ?: return@launch
            val inputStream = FileInputStream(fileDescriptor)
            val outputStream = FileOutputStream(fileDescriptor)
            
            val buffer = ByteArray(32767)
            while (isActive) {
                val length = inputStream.read(buffer)
                if (length > 0) {
                    // معالجة الحزم وتوجيهها (يتم برمجتها في المرحلة 2)
                }
            }
        }
    }

    private fun stopVpn() {
        vpnJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.atyafcode.sirat.vpn.START"
        const val ACTION_STOP = "com.atyafcode.sirat.vpn.STOP"
    }
}
```

### 2. [تعديل] تعديل ملف `AndroidManifest.xml`
المسار: `app/src/main/AndroidManifest.xml`

تسجيل خدمة الـ VPN وطلب صلاحية `BIND_VPN_SERVICE` ليسمح نظام أندرويد للتطبيق بالعمل كـ VPN محلي.

```xml
<!-- تحت وسم <application> -->
<service
    android:name=".services.vpn.SiratVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:exported="false"
    android:foregroundServiceType="specialUse"> 
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>
```

### 3. [ملف جديد] إنشاء ملف `VpnController.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/services/vpn/VpnController.kt`

كلاس مساعد لإدارة صلاحيات الـ VPN وتشغيل/إيقاف الخدمة من واجهة المستخدم بسهولة.

**المهام:**
- التحقق من منح صلاحية الـ VPN عبر `VpnService.prepare(context)`.
- إذا لم تكن الصلاحية ممنوحة، إرجاع الـ Intent المناسب لفتح نافذة إعدادات النظام وتفعيل الـ VPN.
- دوال مساعدة لإرسال Intents تشغيل/إيقاف الخدمة.

---

## 🏁 خطة التحقق والطلب (Verification Plan)

### التحقق اليدوي
1. تشغيل التطبيق والضغط على زر "تفعيل الحظر" (الذي يستدعي `VpnController.start`).
2. التأكد من ظهور نافذة النظام المعتادة لطلب الاتصال بشبكة VPN ("طلب اتصال").
3. الموافقة والتأكد من ظهور أيقونة "المفتاح" في شريط الحالة العلوي للهاتف.
4. التحقق من وجود إشعار دائم غير قابل للمسح في شريط الإشعارات يدل على تشغيل الخدمة.
5. إيقاف الخدمة والتأكد من اختفاء الإشعار وأيقونة المفتاح.
