# План: SQL-режим тем (`mode: sql`)

## Context

У тем уже три движка: `trace` (поведение), `structural` (граф классов), `theory`
(теория + Boss Fight). Категория `databases` (~21 вопрос) — про **написание SQL**,
и ни один режим ей не подходит (нет Java-рантайма и графа классов).

Цель — **`mode: sql`**: тема даёт засеянную схему БД, пользователь пишет SQL,
жмёт **«Выполнить запрос»**, видит **таблицу результата**; миссия засчитывается,
когда результат совпадает с эталонным запросом автора. Запросы — в одноразовой
**H2 in-memory (MODE=PostgreSQL)**, изолированно от рабочей Postgres приложения.

## Переиспользуем (без новых полей DTO)
- `Mission.requires` → SQL-миссия `type: sql`, `requires: [{ kind: sqlResult, expectedSql, ordered? }]`.
- `TopicDetail.starterFiles` → `starter/schema.sql` (DDL+seed) + `starter/query.sql` (стартовый запрос).
- `mode` уже прокинут в DTO и `traceTypes`.

## Фазы
0. Сохранить план в `SQL-MODE-PLAN.md`. ✅
1. Бэкенд: H2-зависимость; `SqlService` (свежая `jdbc:h2:mem:<uuid>;MODE=PostgreSQL`
   на запрос; seed + query → result; compare с `expectedSql`); `SqlController`
   `POST /api/sql`; `TopicRepository` грузит `starter/` и для `sql`.
2. Тесты: `SqlServiceTest`; `TopicContractTest` mode-aware для `sql`.
3. Фронтенд: типы `SqlRunResult`; `api.runSqlQuery`; стор `sqlQuery`/`sqlResult`/`runSql()`
   (сид из `query.sql`, персист localStorage, миссии sticky).
4. UI: `SchemaPanel` (слева, парсит CREATE TABLE), `SqlResultTable`, ветка `sql`
   в `WorkspaceScreen` (Monaco sql + «Выполнить запрос» + таблица), i18n.
5. Тема `topics/sql-many-to-many/` (`databases-8`): employees/courses/enrollments,
   миссия «курсы с >10 сотрудниками» (GROUP BY ... HAVING COUNT(*) > 10) + Boss Fight.
6. Проверка: `SqlServiceTest`, контракт, `npm run build`, вживую.

## Решения
- H2 in-memory MODE=PostgreSQL — безопасно и без инфраструктуры; покрывает
  JOIN/GROUP BY/HAVING/NULL. EXPLAIN/Seq Scan остаются `theory`.
- Миссии проверяются сравнением с авторским `expectedSql` (на бэкенде, там H2).
- Кнопка «Выполнить запрос» (аналог trace→Run, structural→Analyze).

## Ключевые файлы
Бэкенд: `backend/build.gradle`, `sql/SqlDtos.java`, `sql/SqlService.java`,
`api/SqlController.java`, `topics/TopicRepository.java`, `topics/TopicContractTest.java`.
Фронтенд: `engine/{traceTypes,api,store}.ts`, `screens/WorkspaceScreen.tsx`,
`shell/SchemaPanel.tsx`, `shell/SqlResultTable.tsx`, `i18n.ts`. Тема: `topics/sql-many-to-many/`.
