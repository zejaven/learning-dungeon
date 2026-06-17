# План: структурный режим тем для шаблонов проектирования (Design Patterns)

## Context

Текущий движок темы — **поведенческий**: пользователь пишет один файл `Playground.java`,
раннер компилирует и запускает его в дочерней JVM, инструментированные модели `visual.*`
печатают `@@TRACE@@`-события, фронтенд их проигрывает (`visualizer.tsx`) и засчитывает миссии
по совпадению `mission.event`. Шаблоны проектирования — про **структуру** (как классы связаны
через `extends`/`implements`/композицию), а не про последовательность рантайм-событий.

Цель — добавить **второй режим темы — «структурный»**: мульти-файловый редактор с деревом, где
пользователь создаёт классы; статический анализ исходников (JavaParser) строит граф связей; по
графу рисуется диаграмма классов (Mermaid, уже встроен); структурные миссии засчитываются, когда
граф удовлетворяет правилам паттерна.

**Решения:** вариант A (реальный код); на старте **без запуска** кода. Кнопка «Запустить» в
структурных темах → **«Проанализировать»** (компиляция-валидность + парсинг → диаграмма +
перепроверка миссий). После движка — показательная тема **Strategy** (теория + практика).

## Архитектура: два режима

Дискриминатор `mode` у темы: `"trace"` (по умолчанию) или `"structural"`. Структурная тема не
имеет `visualizer.tsx`/`examples/`/`trace-schema.json`; вместо них `starter/**` (стартовые файлы)
и структурные миссии в `quiz.yaml` (`type: structure` + `requires`).

## Фазы
0. Сохранить план в `DESIGN-PATTERNS-PLAN.md`. ✅
1. `mode` у темы + ветвление `WorkspaceScreen`.
2. Мульти-файловая ФС в сторе + рабочее дерево + персист (localStorage) + `starter/`.
3. Бэкенд: `compileAll` + `StructureAnalyzer` (JavaParser) + `POST /api/analyze`.
4. Диаграмма классов: `graphToMermaid` → `MermaidBlock`.
5. Структурные миссии: схема `requires` + фронтенд-чекер `evaluateStructureMission`.
6. Кнопка «Проанализировать» + i18n.
7. Тесты: `StructureAnalyzerTest`; mode-aware `TopicContractTest`/`TopicExamplesTest`.
8. Тема Strategy (mode: structural, catalogId design-patterns-5).
9. (позже) генерация структурных тем (промпты).

## Схема предикатов миссий (MVP)
- `interfaceWithImpls { minImplementations, name? }`
- `composition { targetKind: interface|class, ownerKind?, name? }`
- `edge { kind: extends|implements, from?, to? }`
- `nodeExists { kind: interface|class|abstractClass, name? }`

## Ключевые файлы
Бэкенд: `backend/build.gradle`, `runner/JavaCodeRunner.java`, `structure/StructureAnalyzer.java`,
`api/AnalyzeController.java`, `topics/TopicDtos.java`, `topics/TopicRepository.java`,
`topics/TopicContractTest.java`, `topics/TopicExamplesTest.java`.
Фронтенд: `engine/store.ts`, `engine/traceTypes.ts`, `engine/structure.ts`, `engine/api.ts`,
`screens/WorkspaceScreen.tsx`, `shell/FileTree.tsx`, `shell/ClassDiagram.tsx`, `i18n.ts`.
Тема: `topics/strategy/**`.

## Verification
`StructureAnalyzerTest`; тема Strategy вживую (создать классы → «Проанализировать» → диаграмма +
зелёные миссии); `./gradlew :backend:test`; `npm run build`.
