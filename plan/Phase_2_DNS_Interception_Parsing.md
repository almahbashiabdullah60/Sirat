# المرحلة 2: اعتراض وتحليل حزم الـ DNS باستخدام dnsjava

## الهدف
استخدام مكتبة **dnsjava** (مكتبة Kotlin/Java معروفة وموثوقة) لتحليل طلبات DNS الواردة من واجهة الـ TUN، بدلاً من كتابة parser يدوي معقد للبايتات. يتم استخراج النطاق المطلوب، وفحصه في قوائم الحظر، ثم إما حظره (NXDOMAIN) أو تمريره إلى خادم DNS عام (مثل `1.1.1.1`).

---

## 📦 إضافة مكتبة dnsjava

```toml
# في gradle/libs.versions.toml — أضف:
[dependencies]
dnsjava = { module = "dnsjava:dnsjava", version = "3.6.2" }
```

```kotlin
// في app/build.gradle.kts — أضف:
implementation(libs.dnsjava)
```

**لماذا dnsjava بدلاً من الـ parsing اليدوي؟**
- dnsjava هي مكتبة مستقرة ومختبرة منذ 20 عاماً
- تتعامل مع تعقيدات DNS (مثل ضغط الأسماء، أنواع السجلات المختلفة، EDNS)
- توفر بناءً سهلاً لحزم الرد (NXDOMAIN)
- لا حاجة لإعادة اختراع العجلة

---

## 🏗️ آلية معالجة الحزم (Packet Processing Flow)

```text
واجهة الشبكة TUN
       │
       ▼ (قراءة ByteArray الحزمة الخام)
  تحديد ما إذا كانت الحزمة UDP على منفذ 53
  (يتم فحص أول 20 بايت للـ IPv4 header:
   - البايت 9 == 17 (UDP)؟
   - البايتات 22-23 == 53 (منفذ DNS)؟
   - إذا لا: تُكتب الحزمة في TUN كما هي بدون تعديل)
       │
       ▼ (استخراج Payload الـ DNS من الحزمة)
  DnsResolver.processRequest(byteArray, ...)
       │
       ├─► تحليل بايتات DNS باستخدام dnsjava
       │      val message = Message(byteArray)
       │      val question = message.getQuestion()
       │      val domain = question.getName().toString(true)
       │
       ├─► فحص النطاق في FilterRepository
       │      │
       │      ├─► محظور ← بناء رد NXDOMAIN:
       │      │      val response = message.clone()
       │      │      response.header.setFlag(Flags.QR)
       │      │      response.header.setRcode(Rcode.NXDOMAIN)
       │      │      val responseBytes = response.toWire()
       │      │      // تغليف في IP/UDP header وكتابته في TUN
       │      │
       │      └─► مسموح ← تمرير الطلب لخادم DNS عام:
       │             val forwardBytes = message.toWire()
       │             // إرسال forwardBytes إلى 1.1.1.1:53 عبر DatagramSocket
       │             // استقبال الرد وإعادة تغليفه بترويسة IP/UDP
       │             // كتابة الرد في TUN
```

---

## 🛠️ الملفات البرمجية المطلوبة

### 1. [ملف جديد] إنشاء ملف `DnsResolver.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/services/vpn/DnsResolver.kt`

يدير تحليل طلبات DNS باستخدام dnsjava واتخاذ قرار الحظر أو التمرير.

```kotlin
package com.atyafcode.sirat.services.vpn

import com.atyafcode.sirat.data.filter.FilterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xbill.DNS.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsResolver(
    private val filterRepository: FilterRepository,
    private val dnsServer: InetAddress = InetAddress.getByName("1.1.1.1")
) {
    /**
     * معالجة طلب DNS: إما حظره (NXDOMAIN) أو تمريره لخادم DNS العام.
     * @param dnsPayloadBytes بايتات طلب DNS فقط (بدون ترويسات IP/UDP).
     * @param onResponse (بايتات الرد المغلفة) -> كتابتها في TUN
     */
    suspend fun resolve(
        dnsPayloadBytes: ByteArray,
        onResponse: (ByteArray) -> Unit
    ) = withContext(Dispatchers.IO) {
        val query = try {
            Message(dnsPayloadBytes)
        } catch (e: Exception) {
            return@withContext // حزمة غير صالحة، تجاهل
        }
        
        val question = query.question ?: return@withContext
        val domain = question.name.toString(true).lowercase()
        
        if (filterRepository.shouldBlockDomain(domain)) {
            // بناء رد NXDOMAIN
            val response = Message(query.toWire().size) // نفس ID
            response.header.setFlag(Flags.QR) // استجابة
            response.header.setFlag(Flags.AA) // Authoritative
            response.header.setRcode(Rcode.NXDOMAIN)
            response.addRecord(query.getQuestion(), Section.QUESTION)
            onResponse(response.toWire())
        } else {
            // تمرير الطلب لخادم DNS عام
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 2000
                val packet = DatagramPacket(
                    dnsPayloadBytes, dnsPayloadBytes.size, dnsServer, 53
                )
                socket.send(packet)
                
                val buffer = ByteArray(4096)
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)
                socket.close()
                
                onResponse(reply.data.copyOf(reply.length))
            } catch (e: Exception) {
                // فشل الاتصال، تجاهل
            }
        }
    }
}
```

### 2. [تعديل] ربط `DnsResolver` مع `SiratVpnService`
في `SiratVpnService.kt` (من المرحلة 1)، يتم تعديل حلقة القراءة لاستخدام `DnsResolver`:

```kotlin
// داخل حلقة قراءة الحزم من TUN:
private val resolver = DnsResolver(filterRepository)

// لكل حزمة خام من TUN:
fun processPacket(rawPacket: ByteArray, outputStream: FileOutputStream) {
    // 1. فحص ترويسة IP: هل هي IPv4 (أول 4 بتات = 4)؟
    if (rawPacket[0].toInt() shr 4 != 4) {
        outputStream.write(rawPacket) // ليست IPv4، مررها كما هي
        return
    }
    val headerLength = (rawPacket[0].toInt() and 0x0F) * 4
    // 2. هل البروتوكول UDP (17)؟
    if (rawPacket[9].toInt() != 17) {
        outputStream.write(rawPacket) // ليس UDP، مررها
        return
    }
    // 3. هل المنفذ الهدف 53؟
    val destPort = ((rawPacket[headerLength + 2].toInt() and 0xFF) shl 8) or 
                   (rawPacket[headerLength + 3].toInt() and 0xFF)
    if (destPort != 53) {
        outputStream.write(rawPacket) // ليس DNS، مررها
        return
    }
    // 4. استخراج Payload DNS
    val udpLengthIndex = headerLength + 4
    val udpLength = ((rawPacket[udpLengthIndex].toInt() and 0xFF) shl 8) or 
                    (rawPacket[udpLengthIndex + 1].toInt() and 0xFF)
    val dnsStart = headerLength + 8
    val dnsPayload = rawPacket.copyOfRange(dnsStart, headerLength + udpLength)
    
    // 5. معالجة DNS عبر dnsjava
    resolver.resolve(dnsPayload) { responseBytes ->
        // بناء ترويسة IP/UDP للرد
        // ... (IP المصدر والوجهة معكوسان، منفذ 53)
        outputStream.write(buildResponsePacket(rawPacket, responseBytes))
    }
}
```

> **ملاحظة:** البايتات القليلة التي يتم فحصها يدوياً (IPv4 header، UDP port) هي عملية بسيطة ومستقرة ولا تحتاج مكتبة خارجية. فقط تحليل DNS هو ما يتم عبر dnsjava.

---

## ⚡ معايير الأداء والسرعة

- **العمل في الخلفية:** كل عمليات الـ DNS تتم على `Dispatchers.IO` عبر الكوروتينات.
- **تحديد مهلة الاتصال (Timeout):** `socket.soTimeout = 2000` (2 ثانية) لمنع تعليق الطلبات.
- **عدم حظر الحزم غير المتعلقة بـ DNS:** الحزم التي ليست على منفذ 53 تمر فوراً دون تأخير يُذكر (مجرد فحص 3 بايتات فقط).

---

## 🏁 خطة التحقق والطلب (Verification Plan)

### الاختبارات المؤتمتة (Automated Tests)
- إنشاء اختبار وحدة في `app/src/test/java/com/atyafcode/sirat/vpn/DnsResolverTest.kt`:
  - اختبار تحليل طلب DNS حقيقي والتأكد من استخراج النطاق.
  - اختبار بناء رد NXDOMAIN والتأكد من صياغته الصحيحة.

### التحقق اليدوي
1. تشغيل خدمة الـ VPN في التطبيق.
2. فتح متصفح الهاتف وتصفح موقع مسموح (مثل `wikipedia.org`).
3. التأكد من تحميل الصفحة بسلاسة وبدون تأخير ملحوظ.
4. مراجعة سجلات الـ Logcat للتأكد من التقاط طلبات الـ DNS وتحليلها.
