# Chat UI Feature — Implementation Prompt for InsuScan

## Context
InsuScan is an Android app (Kotlin) that helps people with diabetes manage insulin doses by scanning meal photos. The app currently has a flow of Scan → Summary → Save across multiple screens. We're adding a **chat-based UI** as a new screen that guides the user through the entire meal scanning and insulin calculation flow in a single conversational interface.

## Decisions Already Made
- **Architecture:** Hybrid — state machine handles the happy path with predefined messages and buttons (local, free). GPT-4o-mini is called ONLY when user types free text that the local parser doesn't recognize.
- **Insulin calculation:** Extract `performCalculation()` from `SummaryFragment.kt` into a new shared `InsulinCalculatorUtil.kt` in `utils/`. Do NOT modify SummaryFragment — just create a new file.
- **Camera:** Use simple `ActivityResultContracts` for camera/gallery — do NOT reuse ScanFragment.
- **Navigation:** Add a 6th tab "Chat" to the existing bottom nav. Do not replace any existing tab.
- **Language:** English for all chat messages. Hebrew support can be added later via strings.xml.
- **Code style:** Comments in English only. Short, clear, casual — not formal. Clean OOP/OOD.
- **Folder structure:** Follow the existing package-per-feature pattern. All chat files go in one flat `chat/` package (like `scan/`, `summary/`). Shared utility goes in existing `utils/`.

## Existing App Structure (key packages)
```
com.example.insuscan/
├── auth/           → LoginFragment, AuthManager
├── home/           → HomeFragment
├── scan/           → ScanFragment (camera + image upload + scan trigger)
├── summary/        → SummaryFragment (food display, insulin calc, save) ← contains performCalculation()
├── history/        → HistoryFragment, HistoryViewModel, adapters
├── profile/        → ProfileFragment, UserProfileManager
├── manualentry/    → ManualEntryFragment, EditableFoodItem
├── meal/           → Meal.kt (data class), FoodItem, MealSessionManager
├── mapping/        → MealDtoMapper
├── network/        → RetrofitClient, ApiConfig, ApiService, dto/, repository/
├── utils/          → FileLogger, ToastHelper, DateTimeHelper
├── res/navigation/ → nav_graph.xml (single nav graph)
├── res/menu/       → bottom_nav_menu.xml (5 tabs: Home, Summary, Scan, History, Profile)
```

## New Files to Create
```
com.example.insuscan/
├── chat/
│   ├── ChatFragment.kt            // main chat screen - RecyclerView + input bar + camera button
│   ├── ChatViewModel.kt           // holds message list (LiveData), delegates to ConversationManager
│   ├── ChatAdapter.kt             // RecyclerView adapter with ViewTypes for each ChatMessage type
│   ├── ConversationManager.kt     // state machine - manages ChatState transitions and generates messages
│   ├── ChatMessage.kt             // sealed class: BotText, UserText, UserImage, BotLoading, BotFoodCard, BotMedicalCard, BotActionButtons, BotDoseResult, BotSaved
│   ├── ChatState.kt               // enum: IDLE, AWAITING_IMAGE, SCANNING, REVIEWING_FOOD, CLARIFYING, REVIEWING_MEDICAL, COLLECTING_EXTRAS, CALCULATING, SHOWING_RESULT, SAVING, DONE
│   └── FreeTextParser.kt          // regex-based local text parser (remove/add/update items, confirm, numbers)
│
├── utils/
│   └── InsulinCalculatorUtil.kt   // extracted from SummaryFragment.performCalculation() — shared between Summary and Chat
│
├── res/layout/
│   ├── fragment_chat.xml          // RecyclerView + bottom input bar (EditText + send button + camera button)
│   ├── item_chat_bot_text.xml     // left-aligned bot bubble
│   ├── item_chat_user_text.xml    // right-aligned user bubble
│   ├── item_chat_user_image.xml   // right-aligned image thumbnail
│   ├── item_chat_food_card.xml    // card showing food items list + total carbs + confirm/edit buttons
│   ├── item_chat_medical_card.xml // card showing ICR, ISF, target + confirm/update buttons
│   ├── item_chat_buttons.xml      // horizontal row of action chips (sport, sick, continue, etc.)
│   ├── item_chat_dose_result.xml  // dose breakdown card + save button
│   └── item_chat_loading.xml      // "Analyzing..." with typing indicator animation
```

## Existing Files to Modify (minimal changes only)
1. `bottom_nav_menu.xml` — add one `<item>` for Chat tab
2. `nav_graph.xml` — add `<fragment>` destination for `chatFragment`
3. `strings.xml` — add chat-related strings (~20 lines)
4. `MainActivity.kt` — optionally hide bottom nav in chat screen (1 line in the existing `when` block)

## State Machine Flow
```
IDLE → AWAITING_IMAGE → SCANNING → REVIEWING_FOOD → CLARIFYING (if items have 0 carbs) → REVIEWING_MEDICAL → COLLECTING_EXTRAS → CALCULATING → SHOWING_RESULT → SAVING → DONE
```
Back transitions allowed: REVIEWING_MEDICAL → REVIEWING_FOOD, SHOWING_RESULT → COLLECTING_EXTRAS

## Integration Points (reuse existing code, don't rewrite)
| Chat action              | Existing code to call                          |
|--------------------------|------------------------------------------------|
| Analyze image            | `ScanRepositoryImpl.scanImage()`               |
| Get medical profile      | `UserProfileManager` (all getters)             |
| Calculate insulin        | `InsulinCalculatorUtil` (NEW, extracted)        |
| Save meal to server      | `MealRepositoryImpl.saveScannedMeal()`         |
| Convert to DTO           | `MealDtoMapper.mapToDto()`                     |
| Session management       | `MealSessionManager`                           |
| Meal data model          | `Meal.kt`, `FoodItem` (existing data classes)  |
| User auth                | `AuthManager.getUserEmail()`                   |
| Logging                  | `FileLogger`                                   |

## FreeTextParser — Local Patterns (before calling LLM)
```
"remove/delete [name]"     → RemoveItem(name)
"[name] [number] grams"    → UpdateWeight(name, grams)
"add [name]"               → AddItem(name)
"[number]" (during CLARIFYING) → AnswerClarification(number)
"confirm/ok/yes/looks good" → Confirm
"edit/change/fix"          → EditMode
```
If no pattern matches → call GPT-4o-mini with structured JSON response.

## Implementation Phases

### Phase 1 — Skeleton (start here)
- `ChatMessage.kt` (sealed class with all types)
- `ChatState.kt` (enum)
- `ChatAdapter.kt` (only BotText + UserText ViewTypes initially)
- `ChatFragment.kt` + `fragment_chat.xml`
- `ChatViewModel.kt` (holds message list, handles user input)
- Add to nav_graph + bottom_nav_menu
- **Test:** type a message, get echo response

### Phase 2 — Scanning
- Camera/gallery button with ActivityResultContracts
- Connect to `ScanRepositoryImpl`
- Add ViewTypes: BotLoading, BotFoodCard, UserImage
- `ConversationManager` with states: AWAITING_IMAGE → SCANNING → REVIEWING_FOOD
- **Test:** take photo, see results in chat

### Phase 3 — Editing & Confirmation
- Confirm/Edit buttons on BotFoodCard
- `FreeTextParser.kt` with basic regex
- CLARIFYING state for items with missing carb data
- BotActionButtons ViewType
- **Test:** edit items via buttons or text, confirm

### Phase 4 — Calculation & Saving
- `InsulinCalculatorUtil.kt` — extract from SummaryFragment
- BotMedicalCard + BotDoseResult ViewTypes
- Connect to UserProfileManager + MealRepositoryImpl
- States: REVIEWING_MEDICAL → COLLECTING_EXTRAS → CALCULATING → SAVING
- **Test:** full end-to-end flow

### Phase 5 — LLM + Polish
- GPT-4o-mini integration for unrecognized free text
- UI polish (colors, animations, spacing)
- Edge cases and testing

## Important Notes
- Do NOT modify any existing logic — the chat is purely additive
- `performCalculation()` in SummaryFragment is private — extract a copy to `InsulinCalculatorUtil`, don't change SummaryFragment
- All ViewHolders live inside `ChatAdapter.kt` as inner classes (keeps it clean, like existing adapters)
- Each `ChatMessage` gets a unique `id` (UUID) and `timestamp`
- The chat messages list in `ChatViewModel` is the single source of truth — the adapter observes it

Start with Phase 1. Build each file completely before moving to the next. After each phase, confirm it compiles and works before proceeding.
