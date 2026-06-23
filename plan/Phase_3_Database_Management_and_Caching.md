# المرحلة 3: قاعدة البيانات المحلية والذاكرة المؤقتة (Cache)

## الهدف
إنشاء قاعدة بيانات **Room Database** محلية تحتوي على ثلاثة جداول منفصلة (`porn` و `gambling` و `social`) لتخزين النطاقات المستلمة من واجهة الملفات الساكنة (**Static JSON API**)، وبناء ثلاثة مخازن ذاكرة مؤقتة منفصلة (`HashSet`) في الذاكرة العشوائية للجهاز لضمان التحقق الفوري والربط المباشر مع خيارات الإعدادات الثلاثة في التطبيق.

---

## 💾 معمارية البيانات وهيكل الكاش الثلاثي

يتم تحميل كل جدول محلي بشكل منفصل في ذاكرة RAM مؤقتة ومستقلة، مما يتيح تفعيل أو تعطيل الفحص لكل فئة بشكل مستقل وبأعلى سرعة ممكنة O(1).

```text
                  +-----------------------------------------+
                  |       قاعدة بيانات Room المحلية         |
                  |                                         |
                  |  +-------------+  +------------------+  |
                  |  | PornDomain  |  |  GamblingDomain  |  |
                  |  +-------------+  +------------------+  |
                  |  +-------------+  +------------------+  |
                  |  |SocialDomain |  |    CustomRule    |  |
                  |  +-------------+  +------------------+  |
                  +--------------------+--------------------+
                                       |
                                       | تحميل عند بدء الخدمة
                                       v
                  +--------------------+--------------------+
                  |    الذاكرة المؤقتة المقسمة (RAM Cache)    |
                  |                                         |
                  |  - pornCache: HashSet<String>           |
                  |  - gamblingCache: HashSet<String>       |
                  |  - socialCache: HashSet<String>         |
                  |  - userWhitelist/Blacklist: HashSet     |
                  +--------------------+--------------------+
                                       |
                                       v
                              فحص فوري O(1)
```

---

## 🛠️ التعديلات البرمجية المطلوبة وهيكل الملفات

### 1. [ملفات جديدة] الكيانات البرمجية للـ Room (`data/filter/entities/`)

نقوم بإنشاء ثلاثة كيانات برمجية منفصلة لتمثيل الجداول المقابلة لـ MySQL:

#### أ. جدول الإباحية: `PornDomain.kt`
```kotlin
package com.atyafcode.sirat.data.filter.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "porn")
data class PornDomain(
    @PrimaryKey val domain: String
)
```

#### ب. جدول القمار: `GamblingDomain.kt`
```kotlin
package com.atyafcode.sirat.data.filter.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gambling")
data class GamblingDomain(
    @PrimaryKey val domain: String
)
```

#### ج. جدول شبكات التواصل: `SocialDomain.kt`
```kotlin
package com.atyafcode.sirat.data.filter.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "social")
data class SocialDomain(
    @PrimaryKey val domain: String
)
```

---

### 2. [تعديل] واجهة الوصول للبيانات `FilterDao.kt`
تعديل الدوال لتتعامل بشكل منفصل مع الجداول الثلاثة.

```kotlin
package com.atyafcode.sirat.data.filter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atyafcode.sirat.data.filter.entities.*

@Dao
interface FilterDao {
    // عمليات جدول الإباحية (Porn)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPorn(domains: List<PornDomain>)
    
    @Query("SELECT domain FROM porn")
    suspend fun getAllPorn(): List<String>
    
    @Query("DELETE FROM porn")
    suspend fun clearPorn()

    // عمليات جدول القمار (Gambling)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGambling(domains: List<GamblingDomain>)
    
    @Query("SELECT domain FROM gambling")
    suspend fun getAllGambling(): List<String>
    
    @Query("DELETE FROM gambling")
    suspend fun clearGambling()

    // عمليات جدول شبكات التواصل (Social)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSocial(domains: List<SocialDomain>)
    
    @Query("SELECT domain FROM social")
    suspend fun getAllSocial(): List<String>
    
    @Query("DELETE FROM social")
    suspend fun clearSocial()

    // القواعد المخصصة للمستخدم
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomRule(rule: CustomRule)

    @Query("SELECT * FROM custom_rules")
    suspend fun getAllCustomRules(): List<CustomRule>

    @Query("DELETE FROM custom_rules WHERE domain = :domain")
    suspend fun deleteCustomRule(domain: String)
}
```

---

### 3. [تعديل] تحديث مستودع التصفية `FilterRepository.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/data/repository/FilterRepository.kt`

**تعديلات الكاش والمطابقة:**
1. **الذاكرة المؤقتة الثلاثية:**
   - توفير ثلاثة مخازن `HashSet<String>` مستقلة:
     - `pornCache = HashSet<String>()`
     - `gamblingCache = HashSet<String>()`
     - `socialCache = HashSet<String>()`
2. **منطق التحقق المعتمد على خيارات التفعيل:**
   - يقرأ التطبيق خيارات الحظر المفعلة من قبل المستخدم (مثلاً عبر Preferences):
     - `blockPornActive: Boolean`
     - `blockGamblingActive: Boolean`
     - `blockSocialActive: Boolean`
   - دالة الفحص تعتمد على الخيار المفعل:
     ```kotlin
     fun shouldBlockDomain(
         domain: String, 
         blockPorn: Boolean, 
         blockGambling: Boolean, 
         blockSocial: Boolean
     ): Boolean {
         val cleanDomain = domain.lowercase()
         
         // 1. التحقق من القائمة البيضاء للمستخدم أولاً
         if (customWhitelistCache.contains(cleanDomain) || customWhitelistCache.contains(getParentDomain(cleanDomain))) {
             return false // سماح
         }
         
         // 2. التحقق من القائمة السوداء للمستخدم
         if (customBlacklistCache.contains(cleanDomain) || customBlacklistCache.contains(getParentDomain(cleanDomain))) {
             return true // حظر
         }
         
         // 3. التحقق من خيار حظر الإباحية وجدول الـ porn
         if (blockPorn && (pornCache.contains(cleanDomain) || pornCache.contains(getParentDomain(cleanDomain)))) {
             return true // حظر
         }
         
         // 4. التحقق من خيار حظر القمار وجدول الـ gambling
         if (blockGambling && (gamblingCache.contains(cleanDomain) || gamblingCache.contains(getParentDomain(cleanDomain)))) {
             return true // حظر
         }
         
         // 5. التحقق من خيار حظر السوشيال وجدول الـ social
         if (blockSocial && (socialCache.contains(cleanDomain) || socialCache.contains(getParentDomain(cleanDomain)))) {
             return true // حظر
         }
         
         return false // سماح
     }
     ```

3. **منطق المزامنة وتفريغ البيانات لكل جدول:**
   - عند استقبال ملف الـ JSON الجديد للفئة (مثال: `porn.json`) من مستودع الملفات الساكنة عبر HTTP، يقوم التطبيق بمسح الكاش المحلي وجدول الـ Room الموافق في معاملة واحدة (Transaction)، ثم يُدرج النطاقات الجديدة بالكامل كـ `PornDomain` ويعيد تعبئة الكاش `pornCache` لضمان المطابقة الفورية.

---

## 🏁 خطة التحقق والطلب (Verification Plan)
- **الاختبارات المؤتمتة:**
  - اختبار استقلال الفئات: تأكيد أن موقعاً إباحياً محظوراً في كاش الـ `pornCache` لا يتم حظره إذا كان خيار حظر الإباحية معطلاً وخيار حظر القمار مفعلاً.
  - التحقق من تعبئة كاش الـ HashSets الثلاثة بشكل صحيح ومستقل.
