# استبدال شريط التنقل السفلي بقائمة جانبية (Navigation Drawer)

الهدف هو استبدال الـ `NavigationBar` بـ `ModalNavigationDrawer` في الشاشة الرئيسية لزيادة المساحة المتاحة وإمكانية إضافة صفحات أكثر مستقبلاً بسهولة، مع تحسين واجهة المستخدم لتتوافق مع معايير Material 3.

## التغييرات المقترحة

### واجهة المستخدم (UI)

#### [MainScreen.kt](file:///D:/Jetpack Compose/Sirat/app/src/main/java/com/atyafcode/sirat/features/applist/ui/MainScreen.kt)

- إضافة حالة الـ Drawer باستخدام `rememberDrawerState`.
- إضافة `scope` (CoroutineScope) للتحكم في فتح وإغلاق القائمة.
- تغليف الـ `Scaffold` بمكون `ModalNavigationDrawer`.
- تعريف `ModalDrawerSheet` الذي سيحتوي على:
    - رأس القائمة (Drawer Header) باسم التطبيق وشعاره.
    - قائمة بالعناصر (`NavigationDrawerItem`) تعتمد على الـ `MainTab` الموجود حالياً.
- تحديث الـ `MediumTopAppBar`:
    - إضافة `navigationIcon` (أيقونة القائمة - Menu Icon) لفتح الـ Drawer.
- حذف الـ `bottomBar` من الـ `Scaffold`.

```kotlin
// مثال للتعديل المتوقع في MainScreen.kt
val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
val scope = rememberCoroutineScope()

ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
        ModalDrawerSheet {
            Spacer(Modifier.height(12.dp))
            // Drawer Header
            Text(
                stringResource(R.string.app_name),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleLarge
            )
            HorizontalDivider()
            // Navigation Items
            MainTab.entries.forEach { tab ->
                NavigationDrawerItem(
                    label = { Text(stringResource(tab.titleResId)) },
                    selected = selectedTab == tab,
                    onClick = {
                        selectedTab = tab
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(if (selectedTab == tab) tab.selectedIcon else tab.icon, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    }
) {
    Scaffold(
        topBar = {
            MediumTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                // ... بقية الـ TopAppBar
            )
        },
        // حذف bottomBar
    ) {
        // ... المحتوى
    }
}
```

## خطة التحقق

### التحقق اليدوي
1. تشغيل التطبيق والتأكد من ظهور أيقونة القائمة في الأعلى.
2. الضغط على الأيقونة أو السحب من الحافة لفتح القائمة الجانبية.
3. التنقل بين الصفحات (Apps, Behavior, Plan, Reminders) والتأكد من تغيير المحتوى وإغلاق القائمة تلقائياً عند الاختيار.
4. التأكد من أن التصميم متناسق ولا يتداخل مع الـ Padding الخاص بالنظام (Edge-to-Edge).
5. التأكد من أن الـ FloatingActionButton يعمل بشكل صحيح في صفحة التطبيقات.
