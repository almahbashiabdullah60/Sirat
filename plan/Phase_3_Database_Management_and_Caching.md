# المرحلة 3: قاعدة البيانات المحلية والذاكرة المؤقتة (Cache)

## الهدف
إنشاء قاعدة بيانات **Room Database** محلية تحتوي على ثلاثة جداول منفصلة (`porn` و `gambling` و `social`) لتخزين النطاقات المحجوبة، وبناء ثلاثة مخازن ذاكرة مؤقتة منفصلة (`HashSet`) في الذاكرة العشوائية للجهاز لضمان التحقق الفوري O(1). يتم تحميل البيانات من ملفات JSON مضمنة في مجلد `assets/` عند أول تشغيل، مع دعم URL مخصص للتحديثات (اختياري).

---

## 📦 إضافة dependencies مكتبة Room

```toml
# في gradle/libs.versions.toml — أضف:
[versions]
room = "2.6.1"
ksp = "2.1.0-1.0.29"

[libraries]
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

```kotlin
// في build.gradle.kts على مستوى المشروع:
plugins {
    alias(libs.plugins.ksp) apply false
}

// في app/build.gradle.kts:
plugins {
    alias(libs.plugins.ksp) // أضف هذا
}

android {
    // ...
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
```

**هام لـ ProGuard (release build):**
```
# في proguard-rules.pro:
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
```

---

## 💾 معمارية البيانات وهيكل الكاش الثلاثي

يتم تحميل كل جدول محلي بشكل منفصل في ذاكرة RAM مؤقتة ومستقلة، مما يتيح تفعيل أو تعطيل الفحص لكل فئة بشكل مستقل وبأعلى سرعة ممكنة O(1). أما إعدادات التفعيل (أي فئة مفعلة) فتُخزَّن في `PreferencesRepository` مثل باقي إعدادات التطبيق.

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

نقوم بإنشاء ثلاثة كيانات برمجية منفصلة لتمثيل الجداول:

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

#### د. الكيان البرمجي للقواعد المخصصة: `CustomRule.kt`
```kotlin
package com.atyafcode.sirat.data.filter.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_rules")
data class CustomRule(
    @PrimaryKey val domain: String,
    val isWhitelist: Boolean // true = مسموح, false = محظور
)
```

---

### 2. [ملف جديد] واجهة الوصول للبيانات `FilterDao.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/data/filter/FilterDao.kt`

```kotlin
package com.atyafcode.sirat.data.filter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atyafcode.sirat.data.filter.entities.*
import com.atyafcode.sirat.data.filter.BlockedLog

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

    // سجل الحظر
    @Insert
    suspend fun insertLog(log: BlockedLog)

    @Query("SELECT * FROM blocked_logs ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentLogs(): List<BlockedLog>

    @Query("DELETE FROM blocked_logs")
    suspend fun clearLogs()
}
```

---

### 3. [ملف جديد] قاعدة البيانات `FilterDatabase.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/data/filter/FilterDatabase.kt`

```kotlin
package com.atyafcode.sirat.data.filter

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.atyafcode.sirat.data.filter.entities.*
import com.atyafcode.sirat.data.filter.BlockedLog

@Database(
    entities = [PornDomain::class, GamblingDomain::class, SocialDomain::class, CustomRule::class, BlockedLog::class],
    version = 1,
    exportSchema = false
)
abstract class FilterDatabase : RoomDatabase() {
    abstract fun filterDao(): FilterDao

    companion object {
        @Volatile private var INSTANCE: FilterDatabase? = null

        fun getInstance(app: Application): FilterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    app, FilterDatabase::class.java, "sirat_filter.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
```

---

### 4. [ملف جديد] مستودع التصفية `FilterRepository.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/data/repository/FilterRepository.kt`

**الكاش والمطابقة:**
```kotlin
package com.atyafcode.sirat.data.repository

import com.atyafcode.sirat.data.filter.FilterDatabase
import com.atyafcode.sirat.data.filter.entities.*
import kotlinx.coroutines.*

class FilterRepository(private val database: FilterDatabase) {

    // ذاكرة مؤقتة ثلاثية: تحميل كامل للجداول في HashSet للفحص الفوري
    private var pornCache = HashSet<String>()
    private var gamblingCache = HashSet<String>()
    private var socialCache = HashSet<String>()
    private var whitelistCache = HashSet<String>()
    private var blacklistCache = HashSet<String>()

    /**
     * تحميل جميع البيانات من Room إلى الذاكرة المؤقتة.
     * تُستدعى عند بدء تشغيل الـ VPN.
     */
    suspend fun loadCaches() = withContext(Dispatchers.IO) {
        val dao = database.filterDao()
        pornCache = dao.getAllPorn().toHashSet()
        gamblingCache = dao.getAllGambling().toHashSet()
        socialCache = dao.getAllSocial().toHashSet()
        
        val rules = dao.getAllCustomRules()
        whitelistCache = rules.filter { it.isWhitelist }.map { it.domain }.toHashSet()
        blacklistCache = rules.filter { !it.isWhitelist }.map { it.domain }.toHashSet()
    }

    /**
     * فحص النطاق: هل يجب حظره؟
     * @param domain النطاق المراد فحصه (بالأحرف الصغيرة)
     * @param blockPorn هل خيار حظر الإباحية مفعل؟
     * @param blockGambling هل خيار حظر القمار مفعل؟
     * @param blockSocial هل خيار حظر السوشيال ميديا مفعل؟
     */
    fun shouldBlockDomain(
        domain: String,
        blockPorn: Boolean = true,
        blockGambling: Boolean = true,
        blockSocial: Boolean = true
    ): Boolean {
        // 1. القائمة البيضاء للمستخدم لها الأولوية المطلقة
        if (domain in whitelistCache) return false
        
        // 2. القائمة السوداء للمستخدم
        if (domain in blacklistCache) return true
        
        // 3. فحص حسب الخيارات المفعلة
        if (blockPorn && domain in pornCache) return true
        if (blockGambling && domain in gamblingCache) return true
        if (blockSocial && domain in socialCache) return true
        
        return false
    }
}
```

### 5. إعداد ملفات JSON الأولية في `assets/`

وضع ملفات JSON الأولية للنطاقات في مجلد `assets/filter/`. ملفات JSON موجودة مسبقاً في `plan/` وتحتاج نقلها:

```
من: plan/porn.json          ← (موجود فعلياً)
من: plan/gambling.json      ← (موجود فعلياً)
من: plan/social.json        ← (موجود فعلياً)
إلى: app/src/main/assets/filter/
```

**تنسيق الملفات:** كل مدخل في JSON هو نص بالصيغة `"0.0.0.0 domain.com"`. دالة `syncCategory` أدناه تقوم بتنظيف البادئة `0.0.0.0 ` تلقائياً عند القراءة.

### 6. [ملف جديد] مدير المزامنة `SyncManager.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/data/filter/SyncManager.kt`

```kotlin
package com.atyafcode.sirat.data.filter

import android.content.Context
import com.atyafcode.sirat.data.filter.entities.*
import com.atyafcode.sirat.data.repository.FilterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SyncManager(
    private val context: Context,
    private val database: FilterDatabase,
    private val repository: FilterRepository
) {
    /**
     * تحميل البيانات من ملفات assets/ أو HTTP.
     * تُستدعى مرة واحدة عند بدء تشغيل الـ VPN.
     */
    suspend fun syncFromAssets() = withContext(Dispatchers.IO) {
        val dao = database.filterDao()
        
        // تحميل كل فئة من ملف JSON في assets
        syncCategory("filter/porn.json", "porn") { list ->
            dao.clearPorn()
            dao.insertPorn(list.map { PornDomain(it) })
        }
        syncCategory("filter/gambling.json", "gambling") { list ->
            dao.clearGambling()
            dao.insertGambling(list.map { GamblingDomain(it) })
        }
        syncCategory("filter/social.json", "social") { list ->
            dao.clearSocial()
            dao.insertSocial(list.map { SocialDomain(it) })
        }
        
        // إعادة تحميل الكاش
        repository.loadCaches()
    }

    /**
     * تحميل فئة واحدة من ملف JSON.
     * تنسيق الملف: ["0.0.0.0 domain1.com", "0.0.0.0 domain2.com", ...]
     * يتم استخراج اسم النطاق فقط (إزالة "0.0.0.0 ").
     */
    private fun syncCategory(assetPath: String, category: String, inserter: suspend (List<String>) -> Unit) {
        try {
            val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val domains = JSONArray(json).let { arr ->
                (0 until arr.length()).map { i ->
                    arr.getString(i)
                        .trim()
                        .removePrefix("0.0.0.0 ") // تنسيق hosts file
                        .lowercase()
                }
            }
            // استخدام runBlocking لتبسيط المزامنة داخل هذه الدالة
            kotlinx.coroutines.runBlocking {
                inserter(domains)
            }
        } catch (e: Exception) {
            // ملف غير موجود أو تالف — تجاهل
        }
    }
}
```

---

## 🏁 خطة التحقق والطلب (Verification Plan)
- **الاختبارات المؤتمتة:**
  - اختبار استقلال الفئات: تأكيد أن موقعاً في `pornCache` لا يتم حظره إذا كان خيار حظر الإباحية معطلاً.
  - التحقق من تعبئة الكاش بشكل صحيح من ملفات JSON.
- **التحقق اليدوي:**
  - تصحيح أخطاء الترجمة بعد إضافة KSP و Room.
  - تشغيل التطبيق في وضع release والتأكد من أن ProGuard لم يحذف كلاسات Room.
