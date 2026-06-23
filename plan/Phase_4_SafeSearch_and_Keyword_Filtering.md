# المرحلة 4: فرض البحث الآمن وتصفية الكلمات المفتاحية

## الهدف
فرض وضع البحث الآمن (SafeSearch) على محركات البحث الكبرى (Google, YouTube, Bing, DuckDuckGo) على مستوى الـ DNS، وتضمين محرك فلترة على مستوى الكلمات المفتاحية الموجودة في النطاقات لحظر المواقع المشبوهة تلقائياً.

---

## 🔍 1. فرض البحث الآمن على مستوى الـ DNS (SafeSearch)

### لماذا على مستوى الـ DNS؟
تتطلب الطرق التقليدية لتفعيل البحث الآمن تعديل ترويسات HTTP أو معاملات الرابط (مثل إلحاق `&safe=active`)، وهو أمر مستحيل عملياً على اتصالات HTTPS المشفرة دون فك تشفير SSL (والذي يتطلب تثبيت شهادة جذر Root Certificate وهو أمر معقد وغير آمن للخصوصية). 

لذلك، توفر محركات البحث الكبرى طريقة رسمية وآمنة عبر الـ DNS لإجبار الأجهزة والشبكات على استخدام البحث الآمن من خلال إعادة توجيه طلبات النطاق.

### جدول إعادة التوجيه للبحث الآمن (DNS Mapping)
عندما يطلب جهاز المستخدم عنوان محرك البحث، نقوم باعتراض استعلام الـ DNS وإرجاع عنوان الـ IP الخاص بالنطاق الآمن المقابل له. **عنوان IP يُحل ديناميكياً** (لا يُستخدم عنوان ثابت لتجنب تغيره في المستقبل):

| المحرك | النطاق الأصلي | نطاق البحث الآمن المستهدف |
|---|---|---|
| **Google** | `google.com`, `www.google.com` (وكافة النطاقات الإقليمية مثل `google.com.sa`) | `forcesafesearch.google.com` |
| **Bing** | `bing.com`, `www.bing.com` | `strict.bing.com` |
| **YouTube** | `youtube.com`, `www.youtube.com`, `m.youtube.com` | `restrict.youtube.com` |
| **DuckDuckGo** | `duckduckgo.com`, `www.duckduckgo.com` | `safe.duckduckgo.com` |

### منطق التنفيذ البرمجي
1. **حل العناوين ديناميكياً عند بدء الخدمة:**
   - عند تشغيل الـ VPN، تقوم الخدمة بحل (`resolve`)  عناوين الـ A (IPv4) للنطاقات الآمنة عبر dnsjava (`SimpleResolver`) وحفظها في `HashMap<String, InetAddress>`.
   - هذا يضمن استخدام أحدث العناوين دائماً بدلاً من العناوين الثابتة التي قد تتغير.
2. **الاعتراض وإعادة التوجيه باستخدام dnsjava:**
   - عندما يرد طلب DNS لأحد محركات البحث، نتحقق من مطابقة النطاق مع قائمة المحركات.
   - ننشئ رد DNS عبر dnsjava باستخدام عنوان IP المُحل مسبقاً:
     ```kotlin
     val response = Message(query.toWire().size)
     response.header.setFlag(Flags.QR)
     response.addRecord(query.getQuestion(), Section.QUESTION)
     response.addRecord(ARecord(safeName, Type.A, 300, safeIp), Section.ANSWER)
     ```
3. **قيود معروفة:**
   - لا تعمل هذه الطريقة مع تطبيق YouTube إذا كان يستخدم عناوين IP مباشرة بدلاً من DNS.
   - بعض تطبيقات الطرف الثالث لمحركات البحث قد تتجاوز DNS الخاص بالنظام.

---

## 🚫 2. تصفية الكلمات المفتاحية في النطاقات (Keyword Filtering)

### تنويه وقيود
بسبب العمل على مستوى الـ DNS، **لا يمكننا** قراءة مسارات الروابط الداخلية (مثال: لا نرى `example.com/bad-keyword`). نحن نرى فقط اسم الموقع (النطاق) المطلوب (مثل `bad-keyword-site.com` أو `sub.bad-keyword.net`).

### منطق التنفيذ البرمجي
1. **تخزين الكلمات المفتاحية:**
   - توفير قائمة بالكلمات المفتاحية المحظورة (مثل: `porn`, `adult`, `casino`, `betting`, `gambling`, `sex`).
   - تخزينها في `PreferencesRepository` كـ `Set<String>` عبر serialization في SharedPreferences (تجنباً لـ Room للبيانات الصغيرة).
   - السماح للمستخدم بإضافة كلماته المفتاحية الخاصة عبر الواجهة.
2. **محرك الفحص والمطابقة:**
   - قبل التحقق من قاعدة البيانات في `FilterRepository.kt`، نقوم بإجراء فحص سريع لاحتواء الكلمات:
     ```kotlin
     fun containsBlockedKeyword(domain: String): Boolean {
         return blockedKeywords.any { keyword -> 
             domain.contains(keyword, ignoreCase = true) 
         }
     }
     ```

---

## 🏁 خطة التحقق والطلب (Verification Plan)

### الاختبارات المؤتمتة (Automated Tests)
- إنشاء اختبار وحدة في `app/src/test/java/com/atyafcode/sirat/vpn/SafeSearchAndKeywordTest.kt`:
  - التحقق من مطابقة الكلمات المفتاحية (تأكيد حظر `my-gambling-forum.com` وتمرير `normaldomain.com`).
  - اختبار مطابقة النطاقات الإقليمية لجوجل ويوتيوب.

### التحقق اليدوي
1. تشغيل الـ VPN وتفعيل خياري "البحث الآمن" و"حظر الكلمات المفتاحية".
2. فتح المتصفح والدخول إلى `google.com` وإجراء بحث عشوائي.
3. فتح إعدادات جوجل في المتصفح والتأكد من إظهار قفل البحث الآمن ورسالة **"تم تفعيل البحث الآمن على هذه الشبكة"**.
4. محاولة البحث في يوتيوب والتأكد من تفعيل "وضع تقييد المحتوى" (Restricted Mode) وعدم إمكانية رؤية التعليقات أو الفيديوهات غير اللائقة.
5. محاولة تصفح موقع يحتوي نطاقه على كلمة محظورة (مثل `test-casino-play.com`) والتأكد من حظره فوراً.
