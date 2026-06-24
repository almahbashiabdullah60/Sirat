# تقرير: ما الذي تحتاجه Sirat لتعمل مثل BlockerX

## 1. كيف يعمل BlockerX؟

BlockerX تطبيق بسيط يستخدم **VpnService** فقط — لا يحتاج روت، لا Shizuku، لا iptables، لا Sui.

آلية العمل:
1. ينشئ VPN محلي (`VpnService.Builder`)
2. يضبط DNS السيرفر على IP داخل نطاق الـ VPN
3. يمرر كل استعلام DNS عبر TUN interface
4. يتحقق من النطاق ضد قائمة حظر محلية
5. يرد بـ NXDOMAIN للمواقع المحظورة، ويعيد التوجيه لـ 1.1.1.1 للباقي
6. يعرض إشعار foreground (مطلوب لأي VpnService في Android 8+)

**لا يدعم التشغيل مع VPN آخر** — أي تطبيق يستخدم VpnService يحتل سلوت VPN ولا يمكن تشغيل VPN آخر في نفس الوقت.

## 2. الوضع الحالي في Sirat

### ✅ الموجود ويعمل بعد التصليحات:

| المكون | الحالة |
|--------|--------|
| `SiratVpnService.kt` | موجود — VpnService يستقبل حزم UDP port 53 عبر TUN |
| `DnsResolver.kt` | موجود — يستخدم dnsjava لحل الاستعلامات والرد NXDOMAIN |
| `FilterRepository.kt` | **تم التصليح** — `loadCaches()` تننادى الآن بعد sync |
| `SyncManager.kt` | موجود — يقرأ JSON من assets/ ويملأ قاعدة البيانات |
| قوائم الحظر (porn.json وغيره) | موجودة — 76,722 domain للإباحية، 6,151 للمقامرة، 3,244 للتواصل الاجتماعي |
| UI (FilteringDashboardScreen) | موجود — toggle تشغيل/إيقاف مع StatusCard |
| سلامة الخدمة | **تم التصليح** — `startForeground()` تننادى أولاً (لا مزيد من crash) |
| إشعار foreground | موجود — `vpn_filter_channel` مع `NOTIFICATION_ID=113` |

### ❌ مشكلة Shizoku/iptables:

وضع **Shizoku** يحاول عمل ما يلي:
1. تشغيل خادم DNS محلي على port 5354
2. استخدام iptables لإعادة توجيه ترافيك port 53 إلى 5354
3. حجب DoH عبر حظر IPs خوادم Cloudflare/Google/Quad9

المشكلة:
- iptables يحتاج `CAP_NET_ADMIN` (صلاحية روت)
- جهازك: `Initialize Sui: false` ← Sui غير active ← لا صلاحية
- Shizuku يعمل كـ `shell` user ← ممنوع من تعديل `nat` table
- **بدون iptables، DNS server يشغل لكن لا ترافيق يصل إليه**
- حتى لو اشتغل iptables، المتصفحات تستخدم DoH (port 443) ويتجاوزن port 53

هذا الوضع كُتِب بناءً على طلبك الأصلي: **"أريد تشغيل VPN آخر مع التصفية"**. لكن أثبتت التجربة وواقع Android أن هذا غير ممكن بدون روت.

## 3. ما هو الحل لتطابق BlockerX بالضبط؟

**ببساطة: استخدم وضع VPN فقط، وأزل أو عطّل وضع Shizoku.**

سيرات حاليًا:

```
[Switch On] → يتحقق: هل Shizoku متاح؟
             ← نعم: يشغل وضع SHIZOKU (DnsProxyService + iptables → يفشل بدون روت)
             ← لا: يشغل وضع VPN (SiratVpnService → يشتغل)
```

بعد التصليحات:
- وضع **VPN** يشتغل 100% — الكاش محمّل، لا crash، كل النطاقات تُفحص
- الفرق عن BlockerX: القوائم مختلفة (Sirat تركّز على إباحة/مقامرة/تواصل)، لكن الآلية واحدة

الخطوات اللازمة لتطابق BlockerX بالضبط:

### أ. إزالة وضع Shizoku (اختياري - تبسيط)
حذف `IptablesManager`، `LocalDnsServer`، `DnsProxyService`، و `DnsFilterMode.SHIZUKU`. يجعل الكود أبسط ويضمن أن المستخدم دائمًا يستخدم VPN mode.

### ب. تحسين DnsResolver
إضافة `flags` ديناميكية (porn/gambling/social/keywords/safesearch) تصل من `PreferencesRepository` عوضًا عن القيم الثابتة في constructor.

### ج. إضافة Custom Rules
شاشة لإضافة/إزالة نطاقات يدويًا (blacklist/whitelist) مع ربطها بـ `FilterRepository`.

### د. إحصائيات الحظر
عرض عدد النطاقات المحجوبة في الجلسة الحالية (بدلاً من مجرد سجل زمني).

### هـ. تصفية الكلمات المفتاحية (Keywords)
`FilterRepository.setKeywords()` موجود لكن `keywordCache` فاضي. يحتاج واجهة لإضافة كلمات مثل "xxx", "casino", إلخ.

## 4. الخلاصة

| الميزة | BlockerX | Sirat (قبل) | Sirat (بعد التصليح) |
|--------|----------|-------------|-------------------|
| VpnService | ✅ | ✅ | ✅ |
| تصفية DNS | ✅ | ✅ (لكن الكاش فاضي) | ✅ |
| لا يحتاج روت | ✅ | ✅ | ✅ |
| إشعار foreground | ✅ | ✅ | ✅ |
| قوائم حظر محدّثة | ✅ | ✅ (3 فئات) | ✅ |
| شاشة إعدادات | ✅ | ✅ | ✅ |
| Custom rules | ✅ | ❌ | ❌ (يحتاج إضافة) |
| Keywords | ✅ | جزئي | جزئي |
