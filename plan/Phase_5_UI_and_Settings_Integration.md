# المرحلة 5: واجهة المستخدم والربط بالإعدادات

## الهدف
بناء واجهة مستخدم باستخدام **Jetpack Compose** و **Material 3** (باستخدام Theme التطبيق الحالي — ألوان ديناميكية من النظام). تتيح الواجهة للمستخدم التحكم بتفعيل وإيقاف خدمة الـ VPN، وإدارة خيارات الحظر **الثلاثة**، وإضافة القوائم والكلمات المخصصة، وعرض سجل عمليات الحظر.

---

## 🎨 الهوية المرئية (Design System)
**لا يتم تعريف ألوان جديدة.** تستخدم الواجهة `AppLockTheme` الموجود في `ui/theme/Theme.kt` والذي يعتمد على `dynamicColorScheme` (ألوان Material You الديناميكية من النظام) مع fallback إلى `lightColorScheme` / `darkColorScheme`. المكونات تستخدم ألوان Material 3 الدلالية:
- `MaterialTheme.colorScheme.primary` للأزرار الرئيسية والحالة النشطة
- `MaterialTheme.colorScheme.error` لحالة الحظر
- `MaterialTheme.colorScheme.tertiary` للحماية النشطة
- `MaterialTheme.colorScheme.surfaceVariant` للبطاقات

---

## 🗺️ ربط الملاحة (Navigation Integration)

### 1. [تعديل] إضافة مسار التصفية في `Screen.kt`
```kotlin
// في core/navigation/Screen.kt — أضف:
object FilteringDashboard : Screen("filtering")
object CustomRules : Screen("custom_rules")
```

### 2. [تعديل] إضافة composables في `AppNavigator.kt`
```kotlin
// في core/navigation/AppNavigator.kt — أضف داخل NavHost:
composable(Screen.FilteringDashboard.route) {
    FilteringDashboardScreen(navController)
}
composable(Screen.CustomRules.route) {
    CustomRulesScreen(navController)
}
```

### 3. [تعديل] ربطها من شاشة الإعدادات أو الشاشة الرئيسية
يمكن الوصول إلى `Screen.FilteringDashboard.route` من:
- زر في `MainScreen.kt` (الشاشة الرئيسية) — كخيار إضافي
- أو من `SettingsScreen.kt` كإعداد متقدم

---

## 🛠️ التعديلات البرمجية المطلوبة وهيكل الملفات

### 1. [ملف جديد] كيان سجل الحظر الفوري `BlockedLog.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/data/filter/BlockedLog.kt`

```kotlin
package com.atyafcode.sirat.data.filter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_logs")
data class BlockedLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val reason: String, // "porn", "gambling", "social", "keyword", "custom_blacklist"
    val timestamp: Long = System.currentTimeMillis()
)
```

### 2. إضافة دوال السجل في `FilterDao.kt`
```kotlin
@Insert
suspend fun insertLog(log: BlockedLog)

@Query("SELECT * FROM blocked_logs ORDER BY timestamp DESC LIMIT 50")
suspend fun getRecentLogs(): List<BlockedLog>

@Query("DELETE FROM blocked_logs")
suspend fun clearLogs()
```

---

### 3. [ملف جديد] إنشاء ملف `FilteringViewModel.kt`
المسار: `app/src/main/java/com/atyafcode/sirat/features/filtering/ui/FilteringViewModel.kt`

يتبع نمط `MainViewModel.kt` و `ChatViewModel.kt` الموجودين — يرث `ViewModel()` ويستخدم `StateFlow`.

```kotlin
package com.atyafcode.sirat.features.filtering.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atyafcode.sirat.data.filter.FilterDatabase
import com.atyafcode.sirat.data.repository.FilterRepository
import com.atyafcode.sirat.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FilteringViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesRepository(application)
    private val db = FilterDatabase.getInstance(application)
    val filterRepo = FilterRepository(db)
    
    // حالة الـ VPN
    private val _vpnRunning = MutableStateFlow(false)
    val vpnRunning: StateFlow<Boolean> = _vpnRunning

    // إعدادات الفئات — مخزنة في PreferencesRepository
    private val _blockPorn = MutableStateFlow(true)
    val blockPorn: StateFlow<Boolean> = _blockPorn

    private val _blockGambling = MutableStateFlow(true)
    val blockGambling: StateFlow<Boolean> = _blockGambling

    private val _blockSocial = MutableStateFlow(false)
    val blockSocial: StateFlow<Boolean> = _blockSocial

    fun toggleVpn() { /* استدعاء VpnController */ }
    fun setBlockPorn(enabled: Boolean) { _blockPorn.value = enabled }
    fun setBlockGambling(enabled: Boolean) { _blockGambling.value = enabled }
    fun setBlockSocial(enabled: Boolean) { _blockSocial.value = enabled }
}
```

---

### 4. [ملفات جديدة] بناء شاشات Jetpack Compose (`features/filtering/ui/`)

#### أ. شاشة لوحة التحكم الرئيسية (`FilteringDashboardScreen.kt`)
تستخدم `AppLockTheme` (كل شاشة داخل التطبيق تستخدم `AppLockTheme` تلقائياً في `MainActivity.kt`).

```kotlin
@Composable
fun FilteringDashboardScreen(navController: NavHostController) {
    val viewModel: FilteringViewModel = viewModel()
    
    FilteringDashboardContent(
        vpnRunning = viewModel.vpnRunning.collectAsState().value,
        blockPorn = viewModel.blockPorn.collectAsState().value,
        blockGambling = viewModel.blockGambling.collectAsState().value,
        blockSocial = viewModel.blockSocial.collectAsState().value,
        onToggleVpn = { viewModel.toggleVpn() },
        onBlockPornChange = { viewModel.setBlockPorn(it) },
        onBlockGamblingChange = { viewModel.setBlockGambling(it) },
        onBlockSocialChange = { viewModel.setBlockSocial(it) },
        onNavigateToCustomRules = { navController.navigate(Screen.CustomRules.route) }
    )
}

@Composable
fun FilteringDashboardContent(
    vpnRunning: Boolean,
    blockPorn: Boolean,
    blockGambling: Boolean,
    blockSocial: Boolean,
    onToggleVpn: () -> Unit,
    onBlockPornChange: (Boolean) -> Unit,
    onBlockGamblingChange: (Boolean) -> Unit,
    onBlockSocialChange: (Boolean) -> Unit,
    onNavigateToCustomRules: () -> Unit
) {
    // استخدام MaterialTheme.colorScheme.* بدلاً من ألوان مخصصة
    val activeColor = MaterialTheme.colorScheme.primary
    val dangerColor = MaterialTheme.colorScheme.error
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    
    // ... بناء الواجهة باستخدام Material 3 Components
}
```

- **بطاقة الحالة (Status Card):** تستخدم `MaterialTheme.colorScheme.primaryContainer` و `onPrimaryContainer` للحالة النشطة، و `surfaceVariant` للحالة المتوقفة.
- **خيارات التحكم الثلاثة:** ثلاثة `Card` مع `Switch`:
  - **حظر المحتوى الإباحي:** `blockPorn`
  - **حظر القمار والمراهنات:** `blockGambling`
  - **حظر شبكات التواصل الاجتماعي:** `blockSocial`
- **سجل الحجب الأخير:** `LazyColumn` يعرض آخر عمليات الحظر.

#### ب. مدير القوائم المخصصة (`CustomRulesScreen.kt`)
- واجهة مقسمة لعلامتي تبويب (Tabs): **القائمة السوداء** و**القائمة البيضاء**.
- حقل نصي (`OutlinedTextField`) لإضافة نطاق جديد.

---

## 🏁 خطة التحقق والطلب (Verification Plan)

### التحقق اليدوي
1. إضافة `Screen.FilteringDashboard.route` إلى `Screen.kt` وتجربة التنقل إليه من الشاشة الرئيسية.
2. الضغط على زر التفعيل الرئيسي للتأكد من ظهور نافذة طلب الـ VPN من النظام.
3. تشغيل خيار حظر "الإباحية" وإيقاف الخيارين الآخرين. التأكد من حظر المواقع المدرجة في جدول `porn` مع السماح بتصفح المواقع الأخرى.
4. تكرار التجربة مع خيار "السوشيال ميديا" والتأكد من حظر `facebook.com` و `instagram.com` فقط.
