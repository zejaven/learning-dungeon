# План: challenge-режим тем (`mode: challenge`) для algorithms

## Context

У тем четыре движка: `trace`, `structural`, `theory`, `sql`. Категория `algorithms`
— кодовые задачи. Нужен LeetCode-подобный режим: дописать метод, прогнать тест-кейсы,
миссия зелёная, когда тесты проходят.

**`mode: challenge`**: тема даёт редактируемый `Solution.java` (заготовка) + скрытый
авторский харнес. Кнопка **«Запустить тесты»** компилирует решение+харнес, запускает в
дочерней JVM (существующий sandbox), показывает кейсы pass/fail; миссия — когда все тесты
прошли. Показательная тема — `algorithms-6` (наибольшее произведение двух чисел).

## Переиспользуем
- `visual.Trace` → харнес шлёт результат кейса событием `TEST` через новый `visual.TestKit`
  → тесты приходят в `RunResult.traceEvents` (TraceCollector), без нового протокола.
- `JavaCodeRunner` → новый `runProject(files, mainClass)` (compileSources + execute).
- `Mission.requires`, `starterFiles`, `mode`, миссии-как-у-SQL.

## Фазы
0. Сохранить план. ✅
1. `visual/TestKit.java` (`expect(name, expected, actual)` → `Trace.event("TEST", …)`) + тест.
2. Бэкенд: `JavaCodeRunner.runProject`; `TopicRepository.harnessFiles(id)` + грузить
   `starter/` для challenge; `ChallengeController` `POST /api/challenge` (Solution+harness →
   `runProject` → TEST-события → `{tests, error, missions}`).
3. Тесты: `runProject` (Solution+harness → TEST-события); показательная тема (эталон проходит);
   `TopicContractTest` mode-aware для challenge.
4. Фронтенд: типы `TestResult`/`ChallengeResponse`; `api.runChallenge`; стор `testResults`/
   `runningTests`/`runTests()` (редактор = `code`, персист localStorage `challenge:<id>`).
5. UI: `TestResults.tsx`; ветка challenge в `WorkspaceScreen` (Monaco java + «Запустить тесты»
   + список кейсов); i18n.
6. Тема `topics/algo-max-pair-product/` (`algorithms-6`): Solution-заглушка + harness (кейсы,
   включая два отрицательных) + миссия `all-tests` + Boss Fight.
7. Промпты (`add-topic.md`/`topic-contract.md`) + подсказка `TopicGenController` (algorithms);
   проверка `./gradlew :visual-runtime:test :backend:test`, `npm run build`.

## Решения
- Тесты через `visual.Trace` (`TEST`) — переиспользует pipeline.
- Харнес скрыт (`harness/`, только бэкенд).
- Решение — один `Solution.java` (стор `code`).
- Миссии на бэкенде (`requires:[{kind: tests}]` → все TEST passed и ≥1), map как в SQL.
