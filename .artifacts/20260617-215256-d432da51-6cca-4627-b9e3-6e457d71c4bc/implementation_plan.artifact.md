# Refactor AI Settings and Integrate with Plan Builder and Chat

This plan aims to separate AI configuration settings into a dedicated screen and ensure that both the Plan Builder and Chat features utilize these settings and behavioral data for a personalized experience.

## User Review Required

> [!IMPORTANT]
> The AI Settings will now be centralized. Changes to these settings will affect both Plan generation and Chat behavior.

## Proposed Changes

### Navigation and Routing

#### [Screen.kt](file:///D:/Jetpack Compose/Sirat/app/src/main/java/com/atyafcode/sirat/core/navigation/Screen.kt)
- Add `AISettings` route.

#### [AppNavigator.kt](file:///D:/Jetpack Compose/Sirat/app/src/main/java/com/atyafcode/sirat/core/navigation/AppNavigator.kt)
- Add `AISettingsScreen` to `AppNavHost`.

---

### AI Settings Component [NEW]

#### [NEW] [AISettingsViewModel.kt](file:///D:/Jetpack Compose/Sirat/app/src/main/java/com/atyafcode/sirat/features/planbuilder/ui/AISettingsViewModel.kt)
- Manage AI configuration: Language, Religion, AI Provider, Cloud Provider, API Key, Model selection.
- Handle Local AI model download and status polling.
- Persist settings via `PlanRepository`.

#### [NEW] [AISettingsScreen.kt](file:///D:/Jetpack Compose/Sirat/app/src/main/java/com/atyafcode/sirat/features/planbuilder/ui/AISettingsScreen.kt)
- Dedicated UI for AI configuration (migrated from `PlanBuilderScreen.kt`).
- Includes model download progress and OpenRouter settings.

---

### Plan Builder Refactoring

#### [PlanBuilderScreen.kt](file:///D:/Jetpack Compose/Sirat/app/src/main/java/com/atyafcode/sirat/features/planbuilder/ui/PlanBuilderScreen.kt)
- Remove AI configuration UI components.
- Add a button/card to navigate to AI Settings or show a summary of current settings.
- Keep Goal and Behavior selection.

#### [PlanBuilderViewModel.kt](file:///D:/Jetpack Compose/Sirat/app/src/main/java/com/atyafcode/sirat/features/planbuilder/ui/PlanBuilderViewModel.kt)
- Remove AI settings management logic (now in `AISettingsViewModel`).
- Update `buildPlan` to fetch latest settings from `PlanRepository`.
- Keep behavior list management and prompt construction logic.

---

### Chat Integration

#### [ChatViewModel.kt](file:///D:/Jetpack Compose/Sirat/app/src/main/java/com/atyafcode/sirat/features/chat/ui/ChatViewModel.kt)
- Update `sendMessage` to incorporate user's Religion and Plan Language into the system prompt.
- Ensure behavior context is accurately represented in the AI conversation.

---

### Resources

#### [strings.xml](file:///D:/Jetpack Compose/Sirat/app/src/main/res/values/strings.xml)
- Add or update strings for the new AI Settings screen and navigation.

## Verification Plan

### Automated Tests
- N/A (Project mostly uses manual verification for UI and AI features).

### Manual Verification
1.  **AI Settings Screen**:
    - Verify all settings (Language, Religion, Provider) can be saved.
    - Verify model download works and progress is shown.
    - Verify OpenRouter model list refreshes correctly.
2.  **Plan Builder**:
    - Verify it generates a plan using the settings configured in the AI Settings screen.
    - Verify Goal and Behavior selection still work.
3.  **Chat**:
    - Verify AI responses respect the configured Language and Religion.
    - Verify Chat acknowledges behavior logs in its context.
4.  **Navigation**:
    - Verify navigation from Plan Builder to AI Settings and back works smoothly.
