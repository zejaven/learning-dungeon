# JavaRush courses: levels vs. app topics

Вспомогательный файл для будущего RoadMap Java-домена. Не коммитить.

Источник: https://javarush.com/courses (программы курсов сняты 2026-10-19 из HTML
страниц курсов, порядок уровней — как на сайте).

Отметки справа — темы приложения (id папки в `topics/`), которые совпадают с
уровнем:

- **=** — тема покрывает уровень полностью или почти полностью;
- **≈** — тема покрывает часть уровня (отдельный аспект, вопрос с собеседования
  по этой теме);
- без отметки — в приложении такой темы пока нет (кандидат на «промежуточную» тему).

## Какие курсы взяты и какие нет

Взяты (есть совпадения с Java-доменом приложения):

| Курс | Модули | Уровней |
|---|---|---|
| Курс Java (`/courses/java`) | Java Syntax, Java Core, Java Multithreading, Java Collections | 51 |
| Курс Java 25 (`/courses/java25`) | 7 модулей | 65 |
| Курс SQL (`/courses/sql`) | 6 модулей | 60 |
| Spring Foundation (`/courses/spring-foundation`) | Java Server, Spring Core, Spring Boot | 79 |
| Spring in Practice (`/courses/spring-in-practice`) | Spring REST & MVC, Spring Data JPA, Spring Security | 86 |
| Spring in Production (`/courses/spring-in-production`) | Spring Test, Hibernate Deep Dive, Docker for Spring | 80 |
| Docker (`/courses/docker`) | Linux Essentials, Docker Fundamentals, Docker Advanced, Docker Pro | 24 |

Не взяты:

- **Kotlin** — исключён по условию.
- **Python, Web (HTML/CSS/JS), C#, C++, Go, Swift** — курсы по другим языкам.
  Общие идеи (ООП, многопоточность, REST) там есть, но в Java-формулировках
  они уже покрыты курсами выше.
- **ChatGPT Apps, Claude Code** — не пересекаются с темами приложения.

---

## 1. Курс Java (51 уровень)

### Java Syntax (20 уровней)

1. Команды и первая программа — ≈ `jvm-source-code-flow`
2. Знакомство с типами и ввод с клавиатуры — ≈ `java-data-types`
3. Условный оператор
4. Циклы
5. Работа в Intellij IDEA, Debug
6. Массивы — ≈ `array-search-complexity`, `array-find-duplicates`
7. Функции — ≈ `method-signature`, `method-call-stack-frames`
8. Типы данных — = `java-data-types`, `java-primitive-sizes`, `primitive-vs-object-types`
9. Полезные нюансы
10. Работа со строками — ≈ `string-immutability`, `string-concatenation`
11. Объекты — ≈ `reference-types-storage`, `variable-storage`
12. Классы и static — = `java-static-keyword`
13. Коллекции, Generics — ≈ `java-collections-overview`, `arraylist-internals`
14. Коллекции, часть 2 — ≈ `hashmap-basics`, `java-set-implementations`
15. Исключения — = `exception-basics`
16. Потоки ввода-вывода — ≈ `resource-exception-handling`
17. Работа со временем и датой
18. Знакомство с ООП — = `oop-principles`
19. Лямбда-функции — ≈ `lambda-as-method-parameter`
20. Задачи-игры на JavaRush

### Java Core (10 уровней)

21. Основы ООП: основные принципы, наследование, инкапсуляция — = `oop-principles`, `oop-principles-applied`; ≈ `default-vs-protected`
22. Основы ООП: перегрузка, полиморфизм, абстракция, интерфейсы — ≈ `override-vs-overload`, `interface-vs-abstract-class`
23. Интерфейсы: сравнение с абстрактным классом, множественное наследование — = `interface-vs-abstract-class`
24. Приведение типов, instanceof. Большая задача на интерфейсы
25. Перегрузка методов, особенность вызова конструкторов — ≈ `override-vs-overload`, `method-signature`
26. Знакомство с нитями: Thread, Runnable, start, join, interrupt, sleep — = `thread-vs-runnable`, `java-multithreading`; ≈ `process-vs-thread`, `stopping-a-thread`
27. Знакомство с нитями: synchronized, volatile, yield — ≈ `volatile`, `critical-section`
28. Знакомство с потоками: InputStream/OutputStream, FileInputStream, FileOutputStream
29. Знакомство с потоками: Reader/Writer, FileReader/FileWriter
30. Сериализация

### Java Multithreading (10 уровней)

31. Устройство объекта Object: equals, hashCode, clone, wait, notify, toString() — ≈ `equals-hashcode-contract`, `equals-inheritance`, `wait-notify-synchronized`
32. String: mutable, immutable, format, StringTokenizer, StringBuilder, StringBuffer — = `string-immutability`, `string-concatenation`
33. Внутренние классы, примеры: Map.Entry — ≈ `static-nested-class`, `anonymous-class`, `bucket-data-structure`
34. Внутренние классы, особенности реализации — ≈ `static-nested-class`, `anonymous-class`
35. Создание и остановка нитей: start, interrupt, sleep, yield — = `stopping-a-thread`; ≈ `thread-vs-runnable`
36. Совместный доступ к монопольным данным: synchronized, volatile — = `volatile`, `critical-section`, `race-condition-avoidance`; ≈ `happens-before`, `thread-safe-addition`
37. DeadLock. Wait, notify, notifyAll — ≈ `wait-notify-synchronized`, `lock-alternatives`
38. ThreadGroup, ThreadLocal, Executor, ExecutorService, Callable. Опыт работы с Jsoup — ≈ `java-thread-pool`, `thread-vs-threadpool`, `executor-wait-all`
39. Autoboxing, особенности реализации — ≈ `primitive-vs-object-types`
40. Операторы: числовые, логические и бинарные. Опыт работы с Swing

### Java Collections (11 уровней)

41. Работа с файлами и архивами
42. RMI и динамический прокси. Опыт работы с Swing — ≈ `jdk-dynamic-proxy-vs-cglib`
43. JSON, JavaScript. Опыт работы с Guava, Apache Commons Collections, JUnit
44. Рекурсия. Сборка мусора и типы ссылок в Java. Логгирование — ≈ `recursion-vs-iteration`, `stackoverflow-error`, `heap-generations`, `reference-types-cache`, `memory-leaks`
45. Системы контроля версий: Git и SVN. Generics
46. Основные паттерны для создания веб приложений. Углубленное изучение коллекций — ≈ `presentation-patterns-beyond-mvc`, `hashmap`, `treeset`, `arraylist-vs-linkedlist`, `red-black-tree`, `concurrenthashmap-vs-synchronized-map`
47. Паттерны проектирования. Утилитные классы Arrays. Collections — ≈ `design-patterns-overview`, `singleton-thread-safe`, `strategy`, `observer`, `adapter`, `builder`, `comparator-vs-comparable`, `sorting-algorithm-complexities`
48. Методологии разработки. Аннотации в Java. Иерархия исключений — ≈ `exception-basics`, `exceptions-out-of-memory`
49. Создаем первое веб приложение. Работа с Tomcat и IntelliJ IDEA — ≈ `spring-boot-starter-web`
50. URI, URL. REST сервисы. Создаем свое клиент-серверное приложение — ≈ `http-methods`, `rest-endpoint-naming`, `frontend-backend-interaction`
51. Курс пройден!

---

## 2. Курс Java 25 (65 уровней)

### Основы и современный синтаксис (15 уровней)

1. Команды и первая программа на Java — ≈ `jvm-source-code-flow`
2. Знакомство с типами int и String. Ввод с клавиатуры — ≈ `java-data-types`
3. Условный оператор
4. Циклы
5. Установка IDE, плагина JavaRush
6. Вещественные числа и char — ≈ `java-primitive-sizes`, `java-data-types`
7. Массивы — ≈ `array-search-complexity`, `array-find-duplicates`
8. Методы — ≈ `method-signature`, `method-call-stack-frames`
9. Работа со строками — ≈ `string-immutability`, `string-concatenation`
10. Константы, enum и switch (Java 14+) — ≈ `final`
11. Исключения — = `exception-basics`
12. Бонусный уровень
13. Даты, время и таймзоны (Java 8+)
14. Классы и конструкторы — ≈ `reference-types-storage`, `variable-storage`
15. Инкапсуляция — ≈ `oop-principles`, `default-vs-protected`

### ООП и современные возможности (10 уровней)

16. Вложенные и внутренние классы — = `static-nested-class`, `anonymous-class`
17. Наследование и иерархия — ≈ `oop-principles`, `equals-inheritance`
18. Полиморфизм и перегрузка — = `override-vs-overload`
19. Абстракция и абстрактные классы — ≈ `interface-vs-abstract-class`
20. Интерфейсы — ≈ `interface-vs-abstract-class`
21. Продвинутые интерфейсы и функциональные интерфейсы (Java 8+) — ≈ `lambda-as-method-parameter`
22. Record-классы (Java 16+) — ≈ `lombok-data`
23. ООП — типичные ошибки и best practices — ≈ `solid-principles`, `oop-principles-applied`
24. Продвинутая работа с исключениями — ≈ `resource-exception-handling`, `final-finally-finalize`
25. Контроль версий: работа с Git и GitHub

### Коллекции и Stream API (10 уровней)

26. Коллекции и generics — ≈ `java-collections-overview`
27. Интерфейсы коллекций — = `java-collections-overview`; ≈ `java-set-implementations`
28. Работа с коллекциями — ≈ `arraylist-internals`, `hashmap-basics`, `concurrent-modification`
29. Компараторы и сравнение объектов — = `comparator-vs-comparable`; ≈ `treeset`
30. Основы Stream API (Java 8+) — ≈ `stream-terminal-vs-intermediate`
31. Stream API: группировка и агрегация (Java 8+)
32. Stream API: объединения и проекции (Java 16+)
33. Оптимизация работы с коллекциями — ≈ `arraylist-vs-linkedlist`, `hashmap-lookup-complexity`, `arraylist-naive-growth-complexity`, `streams-vs-loops-performance`
34. Современные коллекции и неизменяемость (Java 9+, Java 21+)
35. Работа с файлами: основы

### Файлы и сериализация (12 уровней)

36. Чтение и запись файлов (детали)
37. Работа с кодировками
38. Ошибки при работе с файлами — ≈ `resource-exception-handling`
39. Работа с файловой системой
40. Операции с файлами и директориями
41. Оптимизация IO
42. Сериализация
43. Настройка сериализации
44. Сериализация сложных структур
45. Совместимость и оптимизация сериализации
46. Сериализация JSON
47. Сериализация XML

### Лямбда и события (3 уровня)

48. Лямбда-выражения (Java 8+) — = `lambda-as-method-parameter`
49. Функциональное программирование (Java 8+) — ≈ `lambda-as-method-parameter`, `stream-terminal-vs-intermediate`
50. События и обработка событий — ≈ `observer`

### Многопоточность и Virtual Threads (9 уровней)

51. Многопоточность: основы — = `java-multithreading`, `process-vs-thread`, `thread-vs-runnable`; ≈ `context-switch`, `stopping-a-thread`
52. Синхронизация потоков — = `critical-section`, `wait-notify-synchronized`; ≈ `volatile`, `semaphore`, `lock-alternatives`
53. Проблемы многопоточности — = `race-condition-avoidance`, `jmm-problems`; ≈ `happens-before`, `concurrent-modification`, `thread-safe-addition`
54. Параллелизм и ForkJoin — ≈ `java-thread-pool`, `thread-vs-threadpool`
55. Асинхронное программирование (Java 8+) — ≈ `executor-wait-all`
56. Асинхронные операции с файлами
57. Virtual Threads (Java 21+)
58. Углубляемся в многопоточность (Java 21+) — ≈ `compare-and-set`, `concurrent-synchronized-collections`
59. Параллельная работа с файлами

### Продвинутые темы Java (6 уровней)

60. Модули и структура проекта (Java 9+)
61. Работа с процессами и командной строкой — ≈ `process-vs-thread`
62. Рефлексия и динамические возможности — ≈ `classloader`, `jdk-dynamic-proxy-vs-cglib`
63. Логирование, мониторинг и профилирование — ≈ `application-metrics`, `diagnosing-memory-leaks`, `why-kibana`
64. Память и сборка мусора — = `jvm-memory-areas`, `jvm-memory-structures`, `heap-generations`, `gc-configuration`; ≈ `memory-leaks`, `reference-types-cache`, `jvm-heaps-stacks-count`, `java-jit-compilation`
65. Pattern Matching, Sealed Classes и новинки Java 25 (Java 16+, Java 17+, Java 21+)

---

## 3. Курс SQL (60 уровней)

### SQL Basics (12 уровней)

1. Первые шаги с PostgreSQL
2. Настройка рабочего окружения
3. Основы SELECT запросов — ≈ `database-query-fields`
4. Сортировка и ограничения
5. Форматирование данных
6. Продвинутая сортировка
7. Введение в агрегацию
8. Группировка и фильтрация
9. Понимание NULL — = `sql-null-three-valued-logic`
10. Работа с NULL на практике — ≈ `sql-null-three-valued-logic`, `sql-anti-join`
11. Основы JOIN — = `sql-joins`
12. Продвинутые JOIN — ≈ `sql-joins`, `sql-anti-join`, `sql-many-to-many`

### Query Mastery (8 уровней)

13. Введение в подзапросы — ≈ `sql-anti-join`
14. Продвинутые подзапросы — ≈ `sql-anti-join`
15. Основы CTE
16. Продвинутые CTE
17. Введение в оконные функции
18. Продвинутые оконные функции
19. Типы дат и времени
20. Продвинутая работа с датами

### Database Design (12 уровней)

21. Числовые и текстовые типы — ≈ `large-numbers-database-storage`
22. Специальные типы данных
23. Создание таблиц
24. Изменение таблиц
25. Внешние ключи — ≈ `sql-many-to-many`
26. Связи между таблицами — = `sql-many-to-many`; ≈ `jpa-one-to-many-join-table`
27. Вставка данных
28. Транзакции — ≈ `acid-principles`
29. Импорт из CSV
30. Массовая загрузка данных — ≈ `postgresql-write-performance`
31. Основы нормализации — = `database-normalization`
32. Проектирование БД — ≈ `database-normalization`, `database-request-product-service-design`

### Modern SQL (8 уровней)

33. JSON и JSONB
34. Продвинутая работа с JSON
35. Основы массивов
36. Продвинутая работа с массивами
37. Введение в индексы — = `database-indexes`; ≈ `clustered-vs-nonclustered-indexes`
38. Специализированные индексы — ≈ `database-indexes`, `postgresql-index-not-used`
39. Основы транзакций — = `acid-principles`; ≈ `mvcc`
40. Изоляция транзакций — = `postgresql-isolation-levels`, `transaction-read-anomalies`; ≈ `mvcc`

### Production SQL (12 уровней)

41. Анализ производительности — = `query-execution-plan`, `query-plan`, `postgresql-explain-plan-reading`
42. Оптимизация запросов — = `sql-query-optimization`, `indexes-for-query-optimization`; ≈ `query-optimization-candidates`, `postgresql-index-not-used`, `sql-vs-java-query-splitting`
43. Резервное копирование
44. Восстановление данных
45. Основы мониторинга
46. Продвинутый мониторинг
47. Управление пользователями
48. Безопасность и шифрование — ≈ `injection-attacks`, `prepared-statements`
49. Основы PL/pgSQL
50. Функции в PL/pgSQL
51. Введение в триггеры
52. Автоматизация с триггерами

### SQL Master (8 уровней)

53. Управляющие конструкции
54. Отладка PL/pgSQL
55. Вложенные транзакции — ≈ `transaction-propagation`
56. Комплексные процедуры
57. Аналитические функции
58. Автоматизация отчетов
59. Диагностика и отладка — ≈ `query-plan`
60. Финальная оптимизация — ≈ `sql-query-optimization`

---

## 4. Spring Foundation (79 уровней)

### Java Server (26 уровней)

1. Зачем этот курс нужен между Java Core и первым Spring backend
2. Создаем каркас проекта и входим в Gradle через Wrapper — ≈ `application-build-options`
3. Плагины, репозитории и зависимости без магии IDE — ≈ `application-build-options`
4. Build lifecycle, ресурсы и единая точка входа приложения — ≈ `application-build-options`
5. Транзитивные зависимости, полезные команды и стабильный baseline проекта
6. HTTP как язык backend-разработчика — ≈ `http-methods`, `browser-page-load`, `http-vs-https`
7. Методы HTTP и ресурсное мышление — = `http-methods`; ≈ `http-idempotency`, `put-vs-patch`
8. Status codes, headers и размещение данных в запросе — ≈ `api-error-handling`
9. Stateless-модель, сетевые сбои и идея API-контракта — ≈ `http-idempotency`, `sales-api-unreliable-connection`, `jwt-vs-session-token`
10. JSON как основной формат обмена данными
11. DTO-мышление и проектирование JSON-контрактов — ≈ `employee-api-design`
12. Postman как рабочий инструмент исследования HTTP-контракта
13. Postman environments, negative-path и защита курса от нестабильного внешнего API
14. Первый рабочий HTTP-клиент на Java 21
15. URI, POST, таймауты и transport-слой без хаоса — ≈ `service-timeouts-fallbacks`
16. Jackson 3 и стабильный JSON mapping для проекта
17. Завершаем клиентскую фазу проекта
18. Структура backend-проекта и ручная сборка зависимостей — ≈ `spring-repository-vs-service`, `spring-ioc-di`
19. Domain, DTO, response model и единый error contract — ≈ `api-error-handling`
20. Конфигурация вне кода: properties, env vars и args — ≈ `env-var-property-naming`, `spring-config-property-precedence`
21. Логирование через SLF4J и Logback как минимальная operational-культура
22. Входим в серверную фазу проекта через JDK HttpServer
23. Роутинг, входящие запросы и JSON-ответы без Spring MVC — ≈ `rest-controller-endpoint-design`
24. Read-only часть локального API — ≈ `rest-endpoint-naming`
25. Create, update, patch, delete и прикладная HTTP-семантика CRUD — ≈ `put-vs-patch`, `employee-api-commands`, `http-methods`
26. Ручная валидация, единый error response, упаковка результата и мост к Spring — ≈ `api-error-handling`

### Spring Core (25 уровней)

27. Зачем нужен Spring и какую проблему он решает — ≈ `spring-ioc-di`
28. IoC и DI до подключения Spring — = `spring-ioc-di`
29. Первый Spring-контекст и правильная точка входа
30. Что такое bean и как контейнер видит приложение — ≈ `spring-bean-vs-component`
31. Component scanning и stereotype-аннотации — = `spring-bean-vs-component`; ≈ `spring-repository-vs-service`
32. @Configuration и @Bean без мифов — = `spring-configuration-bean-methods`
33. Constructor injection как основной стиль — ≈ `spring-ioc-di`
34. Несколько реализаций одного интерфейса
35. Optional dependencies, lazy wiring и циклические зависимости — = `spring-circular-dependencies`
36. Bean lifecycle от старта до завершения — = `spring-bean-lifecycle`
37. Scopes и состояние объектов в контейнере — = `spring-bean-scopes`; ≈ `spring-prototype-bean-use-case`
38. Внешняя конфигурация и Environment — ≈ `spring-config-property-precedence`
39. Type conversion и binding внутри контейнера
40. Profiles и условная сборка приложения
41. Resource abstraction и работа с внешними ресурсами
42. MessageSource и модульная конфигурация
43. Application Events как мягкая развязка частей приложения — ≈ `observer`
44. @EventListener и современная event-модель — ≈ `spring-transactional-event-listener`
45. Startup pipeline и extension points контейнера — ≈ `spring-bean-lifecycle`
46. FactoryBean и Aware interfaces
47. Proxy-модель Spring без мистики — = `spring-aop-proxy`, `jdk-dynamic-proxy-vs-cglib`; ≈ `spring-self-invocation`
48. Spring AOP basics как фундамент для следующих курсов — = `spring-aop-basics`, `spring-aop-terms`, `spring-aop-advice-types`; ≈ `spring-aop-limitations`
49. Как читать legacy Spring и не бояться XML
50. Тестирование чистой Spring-конфигурации
51. Финальная сборка знаний и мост к Spring Boot

### Spring Boot (28 уровней)

52. Зачем появился Spring Boot и какой слой backend-разработки он закрывает
53. Минимальный Spring Core refresher, без которого Boot превращается в магию
54. Создание проекта: Initializr, Gradle Wrapper и каркас приложения — ≈ `application-build-options`
55. Dependency management и starters без страха — ≈ `spring-boot-starter-web`
56. Анатомия Boot-приложения от main() до поднятого runtime
57. Жизненный цикл приложения, startup hooks и ранняя диагностика
58. Бины, инъекция зависимостей и проектная структура живого Boot-приложения — ≈ `spring-repository-vs-service`
59. Auto-configuration без мистики
60. Безопасная кастомизация Boot
61. Первый web-baseline на Spring Boot — = `spring-boot-starter-web`
62. MVC customization без framework takeover
63. JSON baseline в Spring Boot 4
64. Почему externalized configuration — это не "просто application.yaml" — ≈ `spring-config-property-precedence`
65. Property sources и precedence без поверхностного понимания — = `spring-config-property-precedence`; ≈ `env-var-property-naming`
66. Profiles и стратегия окружений
67. Import, дополнительные локации и модульная конфигурация
68. @ConfigurationProperties — правильный путь к type-safe конфигурации
69. Immutable configuration, validation и metadata
70. DevTools и быстрый feedback loop
71. Logging fundamentals для backend-разработчика
72. Modern logging in Boot 4
73. Actuator baseline: visibility first, exposure later — ≈ `application-metrics`
74. Actuator глубже: diagnostics, startup visibility и custom health — ≈ `application-metrics`
75. Packaging и запуск приложения вне IDE — ≈ `application-build-options`
76. Container-friendly mindset до отдельного Docker-курса — ≈ `spring-boot-docker-image`
77. Минимальный testing baseline для любого Boot-проекта
78. Boot-centric тесты: configuration, Actuator и легкий web smoke
79. Финальная демистификация Boot и handoff следующим курсам

---

## 5. Spring in Practice (86 уровней)

### Spring REST & MVC (30 уровней)

1. Позиционирование курса, его границы в линейке и старт сквозного проекта Task Tracker API
2. HTTP глазами backend-разработчика, а не только как «методы и статусы из теории» — ≈ `http-methods`, `http-idempotency`
3. REST как практический стиль проектирования API, а не как набор догматических правил — ≈ `employee-api-design`, `employee-api-rest-cqrs`
4. URI, границы ресурсов и финальная карта endpoints проекта — = `rest-endpoint-naming`; ≈ `sharing-api-endpoints`
5. Spring MVC request handling без магии и без раннего ухода в детали framework internals — ≈ `rest-controller-endpoint-design`
6. Источники входных данных в контроллере: path, query, headers и criteria DTO — ≈ `rest-controller-endpoint-design`
7. Request body, response body и корректные HTTP-ответы на create/update/delete операции — ≈ `employee-api-commands`
8. HttpMessageConverter и реальная механика body conversion в Spring MVC
9. Архитектура проекта внутри REST-курса: контроллеры, сервисы, in-memory репозитории и storage abstraction — ≈ `spring-repository-vs-service`
10. DTO как защита внешнего контракта и как способ не утечь внутренней моделью наружу — ≈ `employee-api-design`
11. Jackson 3 и JSON basics для выбранной Boot-линии курса
12. Jackson-аннотации, которые действительно нужны в REST-курсе, и где надо остановиться
13. JSON beyond simple DTO: generics, tree model и частично динамический input без ухода в хаос
14. Стабильные response shape, list envelopes и безопасная эволюция JSON-контракта
15. Базовая валидация входных данных через Bean Validation и Spring Boot validation stack
16. Валидация вложенных структур, коллекций и параметров запроса
17. Кастомная валидация, cross-field rules и граница между input validation и business validation
18. Как возвращать ошибки валидации так, чтобы они были полезны клиенту и не ломали единый error contract — ≈ `api-error-handling`
19. Категории ошибок в API и современный базовый путь через ProblemDetail — ≈ `api-error-handling`
20. @ControllerAdvice, @ExceptionHandler и перевод исключений в единые HTTP-ответы — = `api-error-handling`
21. Проектирование хорошего error response: коды, детали, field errors и конфликтные бизнес-сценарии — ≈ `api-error-handling`
22. Collection endpoints, pagination и sorting без опоры на Spring Data abstractions
23. Filtering и search без превращения API в хаотичный набор query-параметров
24. Создание, обновление и удаление ресурсов: POST, PUT, PATCH, DELETE без лишней путаницы — = `put-vs-patch`; ≈ `http-methods`, `http-idempotency`
25. Не-CRUD сценарии, переходы состояний и поддерживающие подресурсы без RPC-хаоса — ≈ `employee-api-commands`, `employee-api-rest-cqrs`
26. File upload в Spring MVC и multipart/form-data как часть нормального прикладного API
27. Download endpoints, локальное файловое хранилище и разделение metadata/content
28. Externalized configuration в контексте REST-курса и прикладных сценариев проекта — ≈ `spring-config-property-precedence`
29. Эволюция API и OpenAPI-документация как продолжение contract-first подхода — ≈ `sharing-api-endpoints`
30. @WebMvcTest, MockMvc и финальная сборка навыка production-like REST API

### Spring Data JPA (28 уровней)

31. Зачем приложению нужен persistence layer, как соотносятся JDBC, JPA, Hibernate и Spring Data JPA — ≈ `hibernate-under-the-hood`
32. SQL refresher — таблицы, ключи, ограничения и joins как фон для entity mapping — ≈ `sql-joins`
33. SQL refresher для write-сценариев — DML, индексы и транзакционная логика до @Transactional — ≈ `database-indexes`, `acid-principles`
34. Поднимаем PostgreSQL, настраиваем DataSource, HikariCP и собираем каркас проекта shop-data-jpa
35. Первая entity, первичный ключ и базовый field mapping
36. Entity design — states, @Embeddable, value objects и границы модели — ≈ `jpa-entity-states`
37. Repository abstraction — как интерфейс становится компонентом и где граница ответственности репозитория — ≈ `spring-repository-vs-service`
38. ManyToOne, OneToMany, owning side и ментальная модель связей в JPA — ≈ `jpa-one-to-many-join-table`
39. OneToOne, controlled ManyToMany и первые fetch-ловушки, которые лучше увидеть рано — ≈ `hibernate-default-fetch-strategy`
40. Derived queries, сортировка, Page и Slice для типичных REST- и service-сценариев
41. JPQL и @Query как следующий шаг, когда имена методов перестают быть удобными
42. Projections как нормальный read-side инструмент, а не редкая экзотика
43. Native queries — только там, где JPQL уже неудобен, без превращения курса в SQL-first историю
44. @Modifying, bulk update/delete и разница между «изменить сущности» и «изменить данные быстро»
45. Pragmatic Specification для динамической фильтрации и controlled preview более гибкого querying
46. Современный repository API, legacy-паттерны старых статей и финализация querying-блока
47. Почему @Transactional — это граница бизнес-операции, а не декоративная аннотация — = `spring-transactional-proxy`; ≈ `spring-transactional-self-invocation`
48. Проектируем placeOrder() как полноценную unit of work, а не набор вызовов репозиториев — ≈ `spring-transactional-rollback`
49. Необходимая глубина по propagation, isolation и transactional defaults для Junior-уровня — = `transaction-propagation`; ≈ `postgresql-isolation-levels`, `spring-transactional-rollback`
50. Persistence context, dirty checking, flush и detached-объекты — то, что воспринимается как «магия Hibernate» — = `jpa-entity-states`, `hibernate-under-the-hood`
51. Lazy loading, LazyInitializationException и N+1 как первая настоящая боль ORM — = `hibernate-n-plus-one`; ≈ `hibernate-default-fetch-strategy`
52. join fetch, @EntityGraph и projections для осознанного управления чтением данных — = `hibernate-eager-for-one-query`
53. Flyway и жизнь схемы БД — от демо-схемы к воспроизводимому data-layer
54. Ограничения БД, exception translation и граница между кодовой validation и защитой данных — ≈ `redis-vs-postgresql-uniqueness`
55. Конкурентный доступ, lost update и optimistic locking как база для многопользовательского приложения — = `optimistic-vs-pessimistic-locking`; ≈ `transaction-read-anomalies`
56. Auditing, общие поля и поддерживаемый data-layer без лишней архитектурной тяжести
57. @DataJpaTest, repository tests и проверка маппингов, запросов и pagination на изолированном data-layer
58. Transactional service tests, H2 traps, optional Testcontainers preview и финальная сборка data-layer

### Spring Security (28 уровней)

59. Зачем Spring Security появляется в Spring Boot приложении и какие проблемы он решает — ≈ `endpoint-security-design`
60. Что меняется в приложении сразу после подключения spring-boot-starter-security
61. Request lifecycle и anatomy filter chain в servlet-приложении — ≈ `chain-of-responsibility`
62. Authentication internals без магии и лишнего low-level перегруза — ≈ `authentication-flow`
63. Первый осмысленный SecurityFilterChain и первые собственные access rules — ≈ `endpoint-security-design`
64. Пароли, PasswordEncoder и безопасная модель хранения credentials
65. In-memory users, roles и authorities как первая рабочая security-модель
66. Form login как лучший учебный мост к пониманию Spring Security — ≈ `authentication-flow`
67. Session-based security как полноценная модель, а не "устаревший способ до JWT" — ≈ `jwt-vs-session-token`
68. HTTP Basic и API-клиенты как простой и честный сравнительный baseline
69. CSRF fundamentals на реальных state-changing запросах — = `csrf`
70. CORS, cookies и frontend-интеграция без мифов и blind disable — = `cors`; ≈ `preflight-requests`
71. Multipart и file upload security на примере аватара пользователя
72. Переход от in-memory authentication к DB-backed users
73. CustomUserDetailsService и DaoAuthenticationProvider поверх БД
74. Регистрация пользователя и жизненный цикл аккаунта
75. REST-friendly security errors и различие между 401 и 403 — ≈ `api-error-handling`
76. Request-level authorization для REST API и вспомогательных endpoint'ов — ≈ `endpoint-security-design`
77. Method security как отдельный уровень защиты, а не хвостик к URL-rules — ≈ `endpoint-security-design`
78. Ownership-based access, current user и правила свой / чужой
79. От stateful security к stateless API без идеологической войны — = `jwt-vs-session-token`
80. JWT basics без маркетингового тумана и лишнего OAuth2-контекста — ≈ `jwt-vs-session-token`, `oauth-openid-connect`
81. Выдача JWT и проектирование login endpoint для stateless API — ≈ `authentication-flow`
82. Custom JWT filter как самый прозрачный путь понять stateless authentication
83. Built-in Bearer/JWT support как альтернативная, более framework-native реализация
84. MockMvc и базовый набор security-тестов для Spring MVC приложения
85. Тестирование CSRF, form login, JWT и негативных security-сценариев
86. Hardening, troubleshooting и финальный security audit проекта — ≈ `owasp-top-ten`

---

## 6. Spring in Production (80 уровней)

### Spring Test (26 уровней)

1. Зачем тестировать Spring Boot приложение
2. JUnit 6 как рабочая база курса
3. AssertJ, Hamcrest, JSONassert и JsonPath как язык проверок
4. Mockito без магии и без ложного чувства надежности
5. Unit-тесты бизнес-логики без Spring-контекста
6. Spring Boot 4 testing landscape и карта test annotations
7. Test profiles, properties и управляемая тестовая конфигурация
8. JSON tests как отдельный слой качества API
9. @WebMvcTest как базовый инструмент узкого controller testing
10. MockMvc и HTTP-механика без поднятия сервера
11. MockMvcTester как более ergonomic MVC path
12. Validation и error handling в controller tests
13. Более прикладные MVC-сценарии: pagination, files и boundary cases
14. @DataJpaTest и честное тестирование repository-layer
15. TestEntityManager, flush, clear и реальные последствия JPA-мэппинга
16. Queries, pagination и мост к реальной базе данных
17. Связи сущностей, lazy loading и конкурентные сценарии
18. Flyway, @Sql и repeatable test data
19. @SpringBootTest и карта полного контекста
20. Full-context tests с MockMvc
21. Running server integration tests через RestTestClient
22. Минимальный профессиональный regression suite
23. Security testing для защищенного Spring Boot API
24. Тестирование внешних интеграций и простого async-поведения
25. Testcontainers и real PostgreSQL path
26. REST Docs, platform settings и финальная стратегия test suite

### Hibernate Deep Dive (30 уровней)

27. Зачем Hibernate, что будем строить и как читать SQL-вывод — ≈ `hibernate-under-the-hood`
28. Жизненный цикл сущности и persistence context как основа всего остального — = `jpa-entity-states`
29. Dirty checking и как Hibernate автоматически замечает изменения в объектах — ≈ `hibernate-under-the-hood`
30. Flush и когда именно Hibernate решает идти в базу данных — ≈ `hibernate-under-the-hood`
31. Detached-объекты, что с ними происходит и как правильно использовать merge() — ≈ `jpa-entity-states`
32. Lazy loading, proxy-объекты и почему границы транзакции важнее чем кажется — = `hibernate-default-fetch-strategy`
33. N+1 на живых примерах как первая настоящая ORM-проблема — = `hibernate-n-plus-one`
34. JOIN FETCH, EntityGraph и @BatchSize как инструменты осознанной загрузки данных — = `hibernate-eager-for-one-query`; ≈ `hibernate-n-plus-one`
35. Entity как write-model и projection как read-model в зрелом persistence layer — ≈ `employee-api-rest-cqrs`
36. HQL, Criteria API и native SQL — три инструмента и понимание когда что выбрать
37. Owning side, cascades и lifecycle design как фундамент корректного mapping — ≈ `jpa-one-to-many-join-table`
38. Link entity вместо наивного ManyToMany — честная модель с контролем над lifecycle — ≈ `sql-many-to-many`, `jpa-one-to-many-join-table`
39. equals(), hashCode() и proxy в коллекциях как источник тихих дорогих багов — ≈ `equals-hashcode-contract`, `lombok-data`
40. Embeddables, value objects и converters для выразительной и удобной модели
41. Стратегии идентификаторов, natural id и их реальная цена в runtime
42. Наследование, @DynamicUpdate, ограничения схемы и зрелый mapping design
43. Hibernate внутри Spring-транзакций и transaction boundary на уровне сервиса — ≈ `spring-transactional-proxy`, `transaction-propagation`
44. Optimistic locking как базовая защита от lost update в бизнес-сценариях — = `optimistic-vs-pessimistic-locking`
45. Pessimistic locking как точечный инструмент для критических конкурентных секций — = `optimistic-vs-pessimistic-locking`
46. Soft delete, filters и управление видимостью данных в реальном приложении
47. Hibernate Envers как полноценная история изменений без ручных audit fields
48. Lifecycle callbacks и listeners — удобный механизм со скрытыми side effects
49. JDBC batching как практический способ ускорить массовые записи — ≈ `postgresql-write-performance`
50. Bulk operations и StatelessSession как выход за пределы entity-модели — ≈ `postgresql-write-performance`
51. Read-only mode, first-level cache и кэширование как часть performance-мышления — ≈ `hibernate-under-the-hood`
52. SQL diagnostics и performance audit как системный подход к оптимизации — ≈ `query-plan`, `sql-query-optimization`
53. Hibernate под капотом Spring Data JPA и границы удобства абстракции — = `hibernate-under-the-hood`
54. ORM integration testing как способ ловить реальные баги слоя данных
55. Hibernate anti-patterns и рефакторинг как подготовка к честному code review — ≈ `hibernate-n-plus-one`
56. Финальный persistence audit, decision framework и roadmap роста после курса

### Docker for Spring (24 уровня)

57. Контейнерное мышление для Java/Spring-разработчика и карта всего курса
58. Ежедневный Docker CLI без DevOps-overload
59. Первый контейнер для Spring Boot приложения — = `spring-boot-docker-image`
60. Dockerfile глазами Java-разработчика — ≈ `spring-boot-docker-image`
61. Слои, кэш и быстрая пересборка образа — ≈ `spring-boot-docker-image`
62. Multi-stage build как стандартный путь для Java — ≈ `spring-boot-docker-image`, `application-build-options`
63. Базовые образы, воспроизводимость и платформы
64. Layered Spring Boot images на Boot 4.0.3 — ≈ `spring-boot-docker-image`
65. Buildpacks и bootBuildImage — ≈ `spring-boot-docker-image`, `application-build-options`
66. Development target и debug-oriented image
67. Externalized configuration внутри контейнеризированного Boot-приложения — ≈ `env-var-property-naming`, `spring-config-property-precedence`
68. Profiles и внешние конфигурационные файлы — ≈ `spring-config-property-precedence`
69. Volumes, bind mounts и файловые сценарии
70. Логи, health и корректное завершение приложения — ≈ `why-kibana`, `application-metrics`
71. Compose как нормальный способ жить с несколькими контейнерами
72. Первый взрослый стенд — app + PostgreSQL
73. Startup order и настоящая readiness-модель
74. Flyway и жизненный цикл локальной среды
75. Добавление Redis в тот же Compose-стек
76. Добавление RabbitMQ без превращения курса в messaging-track — ≈ `rabbitmq-queues`
77. Developer workflow вокруг Compose
78. Troubleshooting workshop — ≈ `service-stopped-responding`
79. JVM под memory и CPU limits — ≈ `gc-configuration`, `jvm-memory-areas`
80. Image hygiene, non-root и финальный reusable template

---

## 7. Docker (24 уровня)

### Linux Essentials (8 уровней)

1. Знакомство с Linux
2. Права доступа и работа с процессами — ≈ `process-vs-thread`
3. Управления сервисами и написание bash-скриптов
4. Работа с сетью и SSH
5. Основы безопасности Linux
6. Работа с устройствами в Linux
7. Работа с веб-серверами в Linux
8. Полезные утилиты Linux

### Docker Fundamentals (6 уровней)

9. Знакомство с Docker — ≈ `spring-boot-docker-image`
10. Основные команды Docker
11. Основы работы с Docker
12. Подключение к работающему контейнеру
13. Работа с Docker Images — ≈ `spring-boot-docker-image`
14. Оптимизация Dockerfile — ≈ `spring-boot-docker-image`

### Docker Advanced (6 уровней)

15. Знакомство с Docker Compose
16. Настройка сервисов в Docker Compose
17. Сетевые настройки Docker — ≈ `network-policy-definition`
18. Введение в Docker Swarm — ≈ `why-kubernetes`
19. Управление данными в Docker
20. Работа с конфигурациями и секретами — ≈ `env-var-property-naming`

### Docker Pro (4 уровня)

21. Мониторинг контейнеров — ≈ `application-metrics`
22. Логирование контейнеров — ≈ `why-kibana`
23. Демонстрация работы с Docker'ом
24. Настройка и документирование приложения

---

## Темы приложения, которых нет ни в одном курсе

Эти темы не легли ни на один уровень, в RoadMap для них нужно будет отдельное
место (в основном архитектура и алгоритмы):

- **Алгоритмы:** `algo-max-pair-product`, `array-million-search-complexity`,
  `array-value-search-optimization`, `quadratic-complexity`, `queue-using-two-stacks`,
  `search-complexity-after-sorting`, `stack-and-queue-lifo-fifo`
  (сложность и структуры данных в курсах почти не разбираются).
- **Микросервисы и интеграция:** `api-gateway`, `balance-in-place-vs-ledger`,
  `duplicate-sale-prevention`, `event-carried-state-transfer`, `inbox-pattern`,
  `outbox-pattern`, `inter-service-communication-options`,
  `microservice-interaction-types`, `microservice-patterns`,
  `modular-architecture-options`, `monolithic-architecture-types`,
  `service-communication-choice`, `sync-vs-async-communication`, `why-microservices`.
- **Messaging:** `kafka-vs-rabbitmq`, `rabbitmq-binding-vs-routing-key`
  (`rabbitmq-queues` задет только краем в Docker for Spring).
- **Сеть:** `long-polling`, `realtime-server-push`, `websocket-connection`,
  `websocket-shared-state`, `scaling-an-overloaded-server`, `ssl-tls-certificate`.
- **Безопасность:** `xss`, `symmetric-vs-asymmetric-encryption`.
- **Паттерны проектирования (точечно):** `chain-of-responsibility` (только как
  filter chain), `decorator-vs-proxy`, `factory-method-vs-abstract-factory`,
  `strategy-vs-state`, `template-method`.
- **Производительность и эксплуатация:** `slow-website-diagnosis`,
  `endpoint-broken-in-prod`, `cicd-process-variants`.
- **Spring (точечно):** `spring-async-self-invocation`.
