# SoPlau

RP-клиент для текстовых ролевых игр в стиле SillyTavern / Character.AI.
Kotlin, Jetpack Compose, Material 3, Room, OkHttp (SSE streaming).

## Возможности

- Персонажи: имя, аватарка из галереи (копируется в приватное хранилище), характер, сценарий, первое сообщение, системный промт.
- Чаты для каждого персонажа: создание, переименование, удаление. Боковая панель (Navigation Drawer).
- Сообщения: редактирование, удаление, перегенерация последнего ответа.
- Форматирование: `*действие*` курсивом и отдельным цветом, `"речь"` — цветом речи.
- Генерация со стримингом (ответ печатается по словам).
- Два режима:
  - **Online API** — любой OpenAI-совместимый эндпоинт (OpenRouter, DeepSeek, OpenAI и т.д.).
  - **Offline** — локальный сервер (`http://127.0.0.1:8080/v1`, например llama.cpp server в Termux) или выбор `.gguf` файла.
- Настройки: Temperature, Max Tokens, количество последних сообщений в контексте.
- Темы: розовый градиент и черная.

## Сборка

Сборка выполняется в GitHub Actions (`.github/workflows/build.yml`):
на каждый push создаётся debug APK, он доступен как artifact `SoPlau-debug`.

Локально:

```bash
gradle wrapper --gradle-version 8.10.2   # один раз, если wrapper не закоммичен
./gradlew assembleDebug
```

Файл `app/build/outputs/apk/debug/app-debug.apk`.

## Offline: нативный GGUF

Выбор `.gguf` копирует файл в `files/models/` и сохраняет путь.
Прямой вызов llama.cpp через JNI подготовлен в `data/engine/GgufEngine.kt`:
контракт JNI-функций описан там же. Пока библиотека `soplau_llama` не собрана и
не упакована в APK, приложение использует локальный сервер (`localBaseUrl`).
Это сделано специально: без нативной библиотеки сборка не падает.

## Структура

```
app/src/main/java/com/soplau/rp/
  data/db          Room: Character, Chat, Message + DAO
  data/engine      LlmEngine, OpenAI-совместимый клиент (SSE), GGUF-обёртка
  data/repository  ChatRepository: CRUD и сборка промпта
  data/settings    AppSettings, DataStore
  ui/              AppRoot (навигация), Drawer, MainViewModel
  ui/chat          ChatScreen, ChatViewModel, диалоги
  ui/character     CharacterEditScreen
  ui/settings      SettingsScreen
  ui/theme         Pink gradient / Black
  ui/format        RpFormatter (*действия* и "речь")
```
