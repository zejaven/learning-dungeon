import type { Localized } from './i18n';
import type { TopicSummary } from './engine/traceTypes';

/**
 * The interview-question catalog shown as a tree on the home screen.
 *
 * Built from resources/questions-by-level-and-category.md: categories are merged
 * across difficulty levels, and within a category entries are ordered by
 * difficulty (1 = Junior, 2 = Middle, 3 = Senior), rendered as stars.
 *
 * An entry with a `topicId` already has a generated topic — its theory and
 * practice are available. Entries without a `topicId` offer a "generate theory"
 * action that feeds the question (plus its category id and difficulty) to the
 * same flow as "Add topic". A generated topic records the originating question in
 * its `catalogId`, so {@link buildCatalog} can re-attach it to the right entry.
 */

export type Difficulty = 1 | 2 | 3;

export interface CatalogEntry {
  id: string;
  /** Question text in both languages. */
  question: Localized;
  difficulty: Difficulty;
  /** Set when a generated topic exists for this question. */
  topicId?: string;
}

export interface CatalogCategory {
  id: string;
  /** English label (kept identical in both languages). */
  name: string;
  entries: CatalogEntry[];
}

/** Valid category ids — must stay in sync with the list in prompts/add-topic.md. */
export const CATEGORY_IDS = [
  'java-core',
  'java-collections',
  'concurrency',
  'memory-gc',
  'oop-design',
  'exceptions',
  'streams',
  'algorithms',
  'databases',
  'spring',
  'hibernate',
  'design-patterns',
  'microservices',
  'rest',
  'networking',
  'security',
  'devops',
  'performance',
  'kotlin',
  'messaging',
  'other',
] as const;

export const CATALOG: CatalogCategory[] = [
  {
    id: 'java-core',
    name: 'Java Core',
    entries: [
      { id: 'java-core-1', difficulty: 1, question: { en: `Are strings in Java mutable or immutable? What happens at the memory level if we change a character in a string?`, ru: `Строки в Java мутабельные или иммутабельные? Что произойдёт на уровне памяти, если мы поменяем символ в строке?` } },
      { id: 'java-core-2', difficulty: 1, question: { en: `When you take a variable in Java — what does it hold and where?`, ru: `Если взять переменную в Java — что она хранит и где?` } },
      { id: 'java-core-3', difficulty: 1, question: { en: `Where are reference types stored?`, ru: `Где хранятся ссылочные типы?` } },
      { id: 'java-core-4', difficulty: 1, question: { en: `How does overriding differ from overloading?`, ru: `Чем переопределение отличается от перегрузки?` } },
      { id: 'java-core-5', difficulty: 1, question: { en: `What is a method signature?`, ru: `Что такое сигнатура метода?` } },
      { id: 'java-core-6', difficulty: 1, question: { en: `What is the difference between the default and protected access modifiers?`, ru: `В чём разница между модификаторами доступа default и protected?` } },
      { id: 'java-core-7', difficulty: 1, question: { en: `What is the difference between final, finally and finalize?`, ru: `В чём разница между final, finally и finalize?` } },
      { id: 'java-core-8', difficulty: 1, question: { en: `Tell us what data types exist in Java and how they work.`, ru: `Расскажите, какие в Java есть типы данных и как они работают?` } },
      { id: 'java-core-9', difficulty: 1, question: { en: `What is the difference between primitive and object (reference) types?`, ru: `В чём разница между примитивными и объектными типами данных?` } },
      { id: 'java-core-10', difficulty: 1, question: { en: `How many bytes do the main primitive types take in Java?`, ru: `Сколько байт занимают основные примитивные типы данных в Java?` } },
      { id: 'java-core-11', difficulty: 1, question: { en: `What is static in Java, where can it be used and what is the point of it?`, ru: `Что такое static в Java, где оно может использоваться и какой в нём смысл?` } },
      { id: 'java-core-12', difficulty: 1, question: { en: `What is special about a static (nested) class?`, ru: `В чём особенность static-класса?` } },
      { id: 'java-core-13', difficulty: 1, question: { en: `What is an anonymous class?`, ru: `Что такое анонимный класс?` } },
      { id: 'java-core-14', difficulty: 1, question: { en: `What is final in Java? In what contexts can it appear?`, ru: `Что такое final в Java? В каком контексте оно может присутствовать?` } },
      { id: 'java-core-15', difficulty: 1, question: { en: `If we want to concatenate 1,000,000 strings, how can we do it without wasting memory?`, ru: `Если мы хотим конкатенировать 1 000 000 строк, как мы можем это сделать, чтобы не было потерь в памяти?` } },
      { id: 'java-core-16', difficulty: 1, question: { en: `What does Lombok's @Data annotation do?`, ru: `Что делает аннотация @Data от Lombok?` } },
      { id: 'java-core-17', difficulty: 2, question: { en: `What is a ClassLoader and what kinds are there?`, ru: `Что такое ClassLoader и какие они бывают?` } },
      { id: 'java-core-18', difficulty: 2, question: { en: `How does the JVM work with source code?`, ru: `Как JVM работает с исходным кодом?` } },
      { id: 'java-core-19', difficulty: 2, question: { en: `Why is Java usually faster than Python, and how can the JVM optimize code at runtime (JIT compilation)?`, ru: `Почему Java обычно быстрее Python и как JVM может оптимизировать код во время выполнения (JIT-компиляция)?` } },
    ],
  },
  {
    id: 'java-collections',
    name: 'Java Collections',
    entries: [
      { id: 'java-collections-1', difficulty: 1, topicId: 'arraylist-vs-linkedlist', question: { en: `What is the difference between ArrayList and LinkedList?`, ru: `В чём разница между ArrayList и LinkedList?` } },
      { id: 'java-collections-2', difficulty: 1, question: { en: `What is HashMap and how is it implemented in Java?`, ru: `Что такое HashMap и как он реализован в Java?` } },
      { id: 'java-collections-3', difficulty: 1, question: { en: `What is HashMap's lookup speed?`, ru: `Какая скорость выборки у HashMap?` } },
      { id: 'java-collections-4', difficulty: 1, question: { en: `What collections do you know in Java?`, ru: `Какие ты знаешь коллекции в Java?` } },
      { id: 'java-collections-5', difficulty: 1, question: { en: `What is special about TreeSet?`, ru: `В чём особенность TreeSet?` } },
      { id: 'java-collections-6', difficulty: 1, question: { en: `How does ArrayList work when data is added to it, and why is it implemented this way?`, ru: `Как работает ArrayList, когда туда добавляются данные? И зачем он реализован таким образом?` } },
      { id: 'java-collections-7', difficulty: 2, topicId: 'hashmap', question: { en: `How does HashMap work under the hood?`, ru: `Как работает HashMap «под капотом»?` } },
      { id: 'java-collections-8', difficulty: 2, question: { en: `What data structure stores the values inside a bucket?`, ru: `Какая структура данных хранения значений в bucket-е?` } },
      { id: 'java-collections-9', difficulty: 2, question: { en: `What is the difference between ConcurrentHashMap and a synchronized HashMap?`, ru: `В чём разница между ConcurrentHashMap и синхронизированным HashMap?` } },
      { id: 'java-collections-10', difficulty: 2, question: { en: `How does the JVM know which index corresponds to which object in an ArrayList?`, ru: `Как JVM понимает, что такой-то индекс относится к такому-то объекту в ArrayList?` } },
      { id: 'java-collections-11', difficulty: 2, question: { en: `If ArrayList were implemented naively — allocating a new array and copying all elements on each growth — what would the complexity of adding N elements be?`, ru: `Если бы ArrayList сделали самым примитивным образом — при расширении создавали бы новый массив и копировали все элементы — какая была бы сложность добавления N элементов?` } },
    ],
  },
  {
    id: 'concurrency',
    name: 'Java Multithreading & Concurrency',
    entries: [
      { id: 'concurrency-1', difficulty: 1, question: { en: `What is a critical section?`, ru: `Что такое критическая секция?` } },
      { id: 'concurrency-2', difficulty: 1, question: { en: `What is the difference between Thread and Runnable?`, ru: `Чем отличается Thread от Runnable?` } },
      { id: 'concurrency-3', difficulty: 2, question: { en: `How does multithreading work in Java?`, ru: `Как работает многопоточность в Java?` } },
      { id: 'concurrency-4', difficulty: 2, question: { en: `What is the difference between creating threads via Thread and via a ThreadPool?`, ru: `В чём разница между созданием потоков через Thread и ThreadPool?` } },
      { id: 'concurrency-5', difficulty: 2, question: { en: `What are concurrent and synchronized collections, and what is the difference between them?`, ru: `Что из себя представляют concurrent и synchronized коллекции и в чём между ними разница?` } },
      { id: 'concurrency-6', difficulty: 2, question: { en: `Is the addition of numbers a thread-safe operation?`, ru: `Является ли операция сложения чисел потокобезопасной?` } },
      { id: 'concurrency-7', difficulty: 2, question: { en: `What is a Thread Pool in Java and what can it be used for?`, ru: `Что такое пул потоков (Thread Pool) в Java и для чего его можно использовать?` } },
      { id: 'concurrency-8', difficulty: 2, question: { en: `How can we avoid a race condition?`, ru: `Как мы можем избежать состояния гонки?` } },
      { id: 'concurrency-9', difficulty: 2, question: { en: `What is a context switch?`, ru: `Что такое Context Switch?` } },
      { id: 'concurrency-10', difficulty: 2, question: { en: `What is volatile?`, ru: `Что такое volatile?` } },
      { id: 'concurrency-11', difficulty: 2, question: { en: `What is the compare-and-set concept?`, ru: `Что такое концепция compare and set?` } },
      { id: 'concurrency-12', difficulty: 2, question: { en: `How do you stop a thread after it has been started?`, ru: `Как остановить поток после того, как он был запущен?` } },
      { id: 'concurrency-13', difficulty: 3, question: { en: `What is the happens-before concept?`, ru: `Что такое концепция happens-before?` } },
      { id: 'concurrency-14', difficulty: 2, question: { en: `What is a semaphore and when do you use one?`, ru: `Что такое семафор и когда он нужен?` } },
      { id: 'concurrency-15', difficulty: 2, question: { en: `What causes a ConcurrentModificationException, and how do you safely modify a collection shared between threads (e.g. CopyOnWriteArrayList)?`, ru: `Из-за чего возникает ConcurrentModificationException и как безопасно изменять коллекцию, разделяемую между потоками (например, CopyOnWriteArrayList)?` } },
      { id: 'concurrency-16', difficulty: 3, question: { en: `What alternatives to synchronized exist (ReentrantLock, ReadWriteLock, StampedLock) and when would you choose each?`, ru: `Какие есть альтернативы synchronized (ReentrantLock, ReadWriteLock, StampedLock) и когда выбирать каждую?` } },
      { id: 'concurrency-17', difficulty: 3, question: { en: `How do wait/notify/notifyAll work inside a synchronized block, and does a thread release the monitor when it calls wait()?`, ru: `Как работают wait/notify/notifyAll внутри synchronized-блока и освобождает ли поток монитор при вызове wait()?` } },
      { id: 'concurrency-18', difficulty: 2, question: { en: `How do you run many tasks on a limited thread pool and make the main thread wait until all of them finish (CountDownLatch, invokeAll, Futures)?`, ru: `Как выполнить много задач на ограниченном пуле потоков и заставить main дождаться завершения всех (CountDownLatch, invokeAll, Futures)?` } },
    ],
  },
  {
    id: 'memory-gc',
    name: 'Java Memory Model & Garbage Collection',
    entries: [
      { id: 'memory-gc-1', difficulty: 1, question: { en: `When another method is called from the current one, do the new method's variables go into the same stack or a different one?`, ru: `При вызове другого метода из текущего переменные нового метода попадут в этот же стек или в какой-то другой?` } },
      { id: 'memory-gc-2', difficulty: 2, topicId: 'heap-generations', question: { en: `How does garbage collection happen? What exactly must happen to memory for GC to start?`, ru: `Как происходит сборка мусора? Что именно должно произойти с памятью, чтобы началась сборка мусора?` } },
      { id: 'memory-gc-3', difficulty: 2, question: { en: `How do you tune the garbage collector?`, ru: `Как настроить сборщик мусора?` } },
      { id: 'memory-gc-4', difficulty: 2, question: { en: `Tell us about memory structures in Java. How many stacks and heaps are there in an application?`, ru: `Расскажи про структуры памяти в Java. Сколько стеков и куч в приложении?` } },
      { id: 'memory-gc-5', difficulty: 2, question: { en: `How is memory organized in Java? How is it divided into areas? What is the difference between the stack and the heap?`, ru: `Как устроена память в Java? Как она разделена на области? В чём отличие стека и кучи?` } },
      { id: 'memory-gc-6', difficulty: 2, question: { en: `How many heaps and stacks are there in one JVM instance?`, ru: `Сколько куч и стеков в одном инстансе JVM?` } },
      { id: 'memory-gc-7', difficulty: 2, question: { en: `In what cases can a memory leak happen in Java?`, ru: `В каких случаях может произойти утечка памяти в Java?` } },
      { id: 'memory-gc-8', difficulty: 3, question: { en: `What problems can arise in the Java Memory Model?`, ru: `Какие проблемы могут возникнуть в Java Memory Model?` } },
      { id: 'memory-gc-9', difficulty: 2, question: { en: `What reference types exist in Java (strong, soft, weak, phantom), what is each for, and how would you build a cache the GC clears under memory pressure?`, ru: `Какие типы ссылок есть в Java (strong, soft, weak, phantom), для чего каждый, и как сделать кэш, который JVM сама очистит при нехватке памяти?` } },
      { id: 'memory-gc-10', difficulty: 2, question: { en: `How do you diagnose growing memory use or a leak in production — profilers, heap dump, thread dump?`, ru: `Как диагностировать рост потребления памяти или утечку на проде — профилировщики, heap dump, thread dump?` } },
    ],
  },
  {
    id: 'oop-design',
    name: 'Principles of OOP & Design',
    entries: [
      { id: 'oop-design-1', difficulty: 1, question: { en: `What are the basic OOP principles and what do they represent?`, ru: `Какие есть базовые принципы ООП и что они из себя представляют?` } },
      { id: 'oop-design-2', difficulty: 1, question: { en: `What is your understanding of the principles of object-oriented programming?`, ru: `Каково ваше понимание принципов объектно-ориентированного программирования?` } },
      { id: 'oop-design-3', difficulty: 1, question: { en: `How does an interface differ from an abstract class?`, ru: `Чем отличается интерфейс от абстрактного класса?` } },
      { id: 'oop-design-4', difficulty: 2, question: { en: `What are the SOLID principles?`, ru: `Что такое принципы SOLID?` } },
    ],
  },
  {
    id: 'exceptions',
    name: 'Java Exceptions & Error Handling',
    entries: [
      { id: 'exceptions-1', difficulty: 1, question: { en: `How are exceptions related to resources handled?`, ru: `Как обрабатываются исключения, связанные с ресурсами?` } },
      { id: 'exceptions-2', difficulty: 1, question: { en: `What is a StackOverflow? What is the simplest method that triggers this error?`, ru: `Что такое StackOverflow? Какой простейший метод вызовет эту ошибку?` } },
      { id: 'exceptions-3', difficulty: 1, question: { en: `How are exceptions structured in Java? What types of exceptions are there?`, ru: `Как устроены Exception-ы в Java? Какие есть типы exception-ов?` } },
      { id: 'exceptions-4', difficulty: 2, question: { en: `How can you speed up a stack overflow? Can you reduce the number of calls?`, ru: `Как ускорить процесс переполнения стека? Можно ли уменьшить количество вызовов?` } },
      { id: 'exceptions-5', difficulty: 2, question: { en: `Tell us about exceptions and what to do if the program runs out of memory.`, ru: `Расскажите про исключения и что делать, если в программе закончилась память?` } },
      { id: 'exceptions-6', difficulty: 2, question: { en: `Does it make sense to wrap an OutOfMemoryError in try-catch and continue running the program?`, ru: `Имеет ли смысл обкладывать OutOfMemoryError через try-catch и продолжать выполнение программы?` } },
    ],
  },
  {
    id: 'streams',
    name: 'Java Streams & Lambda Expressions',
    entries: [
      { id: 'streams-1', difficulty: 1, question: { en: `Can you write a function that takes a lambda expression as a parameter? For example, doSomeLogic(() -> System.out.println("Hello"));`, ru: `Можете ли вы написать функцию с параметром в виде лямбда-выражения? Например, doSomeLogic(() -> System.out.println("Hello"));` } },
      { id: 'streams-2', difficulty: 2, question: { en: `Will performance improve when using Java Streams instead of ordinary loops?`, ru: `Улучшится ли производительность при использовании Java Streams вместо обычных циклов?` } },
      { id: 'streams-3', difficulty: 2, question: { en: `How do terminal methods differ from intermediate (pipeline) methods in the Java Stream API?`, ru: `Чем отличаются терминальные методы от конвейерных (промежуточных) методов в Java Stream API?` } },
    ],
  },
  {
    id: 'algorithms',
    name: 'Algorithms and Data Structures',
    entries: [
      { id: 'algorithms-1', difficulty: 1, question: { en: `What is the complexity of finding an element in an array by index and by value?`, ru: `Какая сложность поиска элемента в массиве по индексу и по значению?` } },
      { id: 'algorithms-2', difficulty: 1, question: { en: `What will the search complexity be after sorting the array?`, ru: `Какая будет сложность поиска после сортировки массива?` } },
      { id: 'algorithms-3', difficulty: 1, question: { en: `What will the search complexity be for an array of 1,000,000 values?`, ru: `Чему будет равна сложность поиска для массива длиной в 1 000 000 значений?` } },
      { id: 'algorithms-4', difficulty: 1, question: { en: `Why is O(n^2) complexity bad?`, ru: `Чем плоха сложность n^2?` } },
      { id: 'algorithms-5', difficulty: 1, question: { en: `Which is better — recursion or a regular loop?`, ru: `Что лучше — рекурсия или обычный цикл?` } },
      { id: 'algorithms-6', difficulty: 1, question: { en: `There is a method that takes a list of integers. You need to get the largest product of two numbers from this list.`, ru: `Есть метод, принимающий список целых чисел. Нужно получить наибольшее произведение двух чисел из этого списка.` } },
      { id: 'algorithms-7', difficulty: 2, question: { en: `What is the Big-O complexity of bubble sort, selection sort, insertion sort and heap sort?`, ru: `Какова сложность по нотации O-большое для сортировки пузырьком, выбором, вставками и пирамидальной сортировки?` } },
      { id: 'algorithms-8', difficulty: 2, question: { en: `What is a red-black tree?`, ru: `Что такое красно-чёрное дерево?` } },
      { id: 'algorithms-9', difficulty: 2, question: { en: `How can you speed up finding an element by value in an array?`, ru: `Как можно ускорить поиск элемента по значению в массиве?` } },
      { id: 'algorithms-10', difficulty: 1, question: { en: `What are a stack and a queue (LIFO vs FIFO), and where is each used?`, ru: `Что такое стек и очередь (LIFO vs FIFO) и где каждое применяется?` } },
      { id: 'algorithms-11', difficulty: 2, question: { en: `How do you implement a queue using only stacks, and what is the amortised cost of enqueue/dequeue?`, ru: `Как реализовать очередь, используя только стеки, и какова амортизированная стоимость enqueue/dequeue?` } },
    ],
  },
  {
    id: 'databases',
    name: 'Databases and SQL',
    entries: [
      { id: 'databases-1', difficulty: 1, question: { en: `Have you worked with Prepared Statements?`, ru: `Приходилось ли работать с Prepared Statements?` } },
      { id: 'databases-2', difficulty: 1, question: { en: `Which fields are you necessarily interested in when querying?`, ru: `Какие поля обязательно интересуют при запросах?` } },
      { id: 'databases-3', difficulty: 1, question: { en: `What are the ACID principles?`, ru: `Что такое принципы ACID?` } },
      { id: 'databases-4', difficulty: 2, question: { en: `What is a join and what kinds are there?`, ru: `Что такое join и какие они бывают?` } },
      { id: 'databases-5', difficulty: 2, question: { en: `How does database normalization differ from denormalization? What is a normal form?`, ru: `Чем отличается нормализация базы данных от денормализации? Что такое нормальная форма?` } },
      { id: 'databases-6', difficulty: 2, question: { en: `What are indexes?`, ru: `Что такое индексы?` } },
      { id: 'databases-7', difficulty: 2, question: { en: `What are clustered and non-clustered indexes, and how do they differ?`, ru: `Что такое кластерные и некластерные индексы, чем они отличаются?` } },
      { id: 'databases-8', difficulty: 2, question: { en: `Employees and courses have a many-to-many relationship. Select all courses (name and id) that have more than 10 enrolled employees.`, ru: `Сотрудники и курсы связаны многие-ко-многим. Выберите все курсы (название и id), на которые записано более 10 сотрудников.` } },
      { id: 'databases-9', difficulty: 2, question: { en: `What is a query plan?`, ru: `Что такое план запроса?` } },
      { id: 'databases-10', difficulty: 2, question: { en: `How is a query plan determined?`, ru: `Как определяется план запроса?` } },
      { id: 'databases-11', difficulty: 2, question: { en: `What indexes are needed to optimize queries?`, ru: `Какие индексы нужны для оптимизации запросов?` } },
      { id: 'databases-12', difficulty: 2, question: { en: `What are the ways to make a SQL query more efficient?`, ru: `Какие есть способы сделать запрос в SQL более эффективным?` } },
      { id: 'databases-13', difficulty: 2, question: { en: `How would you design a database for a system with order, product and service entities?`, ru: `Как бы ты спроектировал базу данных для системы с сущностями заявки, продукта и сервиса?` } },
      { id: 'databases-14', difficulty: 2, question: { en: `How do you determine which queries need optimization?`, ru: `Как определять, какие запросы нужно оптимизировать?` } },
      { id: 'databases-15', difficulty: 3, question: { en: `How do you store very large numbers in a relational DB — BigInteger / uint256 / 18-decimal amounts that exceed int64 — and what indexing/sorting problems arise if you store numbers as text?`, ru: `Как хранить очень большие числа в реляционной БД — BigInteger / uint256 / суммы с 18 decimals, выходящие за int64 — и какие проблемы с индексацией/сортировкой возникают, если хранить числа как текст?` } },
      { id: 'databases-16', difficulty: 2, question: { en: `When would you use Redis versus PostgreSQL (e.g. to store and check uniqueness of generated values)?`, ru: `Когда использовать Redis, а когда PostgreSQL (например, для хранения и проверки уникальности сгенерированных значений)?` } },
    ],
  },
  {
    id: 'spring',
    name: 'Spring Framework',
    entries: [
      { id: 'spring-1', difficulty: 2, question: { en: `What is the difference between the @Bean and @Component annotations?`, ru: `В чём разница между аннотациями @Bean и @Component?` } },
      { id: 'spring-2', difficulty: 2, question: { en: `What bean scopes are there?`, ru: `Какие есть скоупы бинов?` } },
      { id: 'spring-3', difficulty: 2, question: { en: `Name a use case for a prototype bean.`, ru: `Назови use case для prototype-бина.` } },
      { id: 'spring-4', difficulty: 2, question: { en: `What is Spring Boot Starter Web? How do you write your own Spring Boot Starter?`, ru: `Что такое Spring Boot Starter Web? Как написать свой Spring Boot Starter?` } },
      { id: 'spring-5', difficulty: 2, question: { en: `What is Spring and what does Inversion of Control / Dependency Injection mean?`, ru: `Что такое Spring и что означает Inversion of Control / Dependency Injection?` } },
      { id: 'spring-6', difficulty: 2, question: { en: `What is @Configuration, and can @Bean methods live outside a @Configuration class?`, ru: `Что такое @Configuration и могут ли @Bean-методы быть вне класса с @Configuration?` } },
      { id: 'spring-7', difficulty: 2, question: { en: `What is the difference between @Repository and @Service (e.g. exception translation)?`, ru: `В чём разница между @Repository и @Service (например, трансляция исключений)?` } },
      { id: 'spring-8', difficulty: 2, question: { en: `What happens with a circular bean dependency (A→B→C→A), how do you avoid it, and what if you can't refactor (@Lazy)?`, ru: `Что произойдёт при циклической зависимости бинов (A→B→C→A), как её избежать и что делать, если рефакторинг невозможен (@Lazy)?` } },
      { id: 'spring-9', difficulty: 3, question: { en: `If a non-transactional method calls a @Transactional method on the same bean, does the transaction start? Why (Spring proxies), and how do you work around it?`, ru: `Если не-транзакционный метод вызывает @Transactional-метод того же бина, начнётся ли транзакция? Почему (прокси Spring) и как это обойти?` } },
      { id: 'spring-10', difficulty: 2, question: { en: `On which exceptions does @Transactional roll back by default (runtime vs checked), how do you change it (rollbackFor), and will it roll back if an external call throws after persist?`, ru: `На каких исключениях @Transactional откатывается по умолчанию (runtime vs checked), как это изменить (rollbackFor) и откатится ли транзакция, если внешний вызов бросит исключение после persist?` } },
      { id: 'spring-11', difficulty: 3, question: { en: `How do you run an action only after a transaction commits (or rolls back)? (@TransactionalEventListener)`, ru: `Как выполнить действие только после коммита (или отката) транзакции? (@TransactionalEventListener)` } },
      { id: 'spring-12', difficulty: 2, question: { en: `What does @Async do (with @EnableAsync), and why might it not work when the method is called from another method of the same class?`, ru: `Что делает @Async (вместе с @EnableAsync) и почему он может не сработать, если метод вызывается из другого метода того же класса?` } },
    ],
  },
  {
    id: 'hibernate',
    name: 'Hibernate & JPA',
    entries: [
      { id: 'hibernate-1', difficulty: 2, question: { en: `What is the default loading strategy for loading entities from the database?`, ru: `Какова стратегия загрузки по умолчанию для загрузки сущностей из базы данных?` } },
      { id: 'hibernate-2', difficulty: 2, question: { en: `If the default strategy is lazy, how do you load entities eagerly for a specific query?`, ru: `Если по умолчанию установлена стратегия lazy, как для конкретного запроса загрузить сущности по стратегии eager?` } },
      { id: 'hibernate-3', difficulty: 2, question: { en: `What is under the hood of Hibernate? How does it differ from plain mapping? What else does it have besides mapping?`, ru: `Что под капотом у Hibernate? Чем он отличается от стандартного маппинга? Что в нём есть кроме маппинга?` } },
      { id: 'hibernate-4', difficulty: 2, question: { en: `How do you map a one-to-many relationship in JPA (e.g. Person↔Cat), and how would you do it via a join table while keeping each child tied to one parent (UNIQUE)?`, ru: `Как смапить связь one-to-many в JPA (например, Person↔Cat) и как сделать это через промежуточную таблицу, сохранив привязку ребёнка к одному родителю (UNIQUE)?` } },
      { id: 'hibernate-5', difficulty: 2, question: { en: `What is the N+1 select problem and how do you avoid it (join fetch, entity graph, batch size)?`, ru: `Что такое проблема N+1 и как её избежать (join fetch, entity graph, batch size)?` } },
      { id: 'hibernate-6', difficulty: 3, question: { en: `What is the difference between optimistic and pessimistic locking, when do you use each, and what SQL does pessimistic locking generate (SELECT ... FOR UPDATE / FOR SHARE)?`, ru: `В чём разница между оптимистической и пессимистической блокировкой, когда что использовать и какой SQL генерирует пессимистическая (SELECT ... FOR UPDATE / FOR SHARE)?` } },
      { id: 'hibernate-7', difficulty: 2, question: { en: `What entity states are there in JPA (transient, managed, detached, removed) and how do you move between them?`, ru: `Какие состояния сущности есть в JPA (transient, managed, detached, removed) и как между ними переходить?` } },
    ],
  },
  {
    id: 'design-patterns',
    name: 'Design Patterns',
    entries: [
      { id: 'design-patterns-1', difficulty: 2, question: { en: `What design patterns exist?`, ru: `Какие существуют шаблоны проектирования?` } },
    ],
  },
  {
    id: 'microservices',
    name: 'Microservices & Architecture',
    entries: [
      { id: 'microservices-1', difficulty: 1, question: { en: `What is the difference between synchronous and asynchronous communication?`, ru: `В чём разница между синхронной и асинхронной коммуникацией?` } },
      { id: 'microservices-2', difficulty: 2, question: { en: `Why are microservices needed?`, ru: `Зачем нужны микросервисы?` } },
      { id: 'microservices-3', difficulty: 2, question: { en: `What kinds of monolithic architectures exist?`, ru: `Какие существуют виды монолитных архитектур?` } },
      { id: 'microservices-4', difficulty: 2, question: { en: `What presentation-layer patterns exist besides MVC?`, ru: `Какие паттерны презентационного уровня существуют кроме MVC?` } },
      { id: 'microservices-5', difficulty: 2, question: { en: `Which type of inter-service interaction is preferable — synchronous or asynchronous?`, ru: `Какой тип взаимодействия между сервисами предпочтительнее — синхронный или асинхронный?` } },
      { id: 'microservices-6', difficulty: 2, question: { en: `How can you optimize the response wait time from services if one of them is unavailable?`, ru: `Как оптимизировать время ожидания ответа от сервисов, если один из них недоступен?` } },
      { id: 'microservices-7', difficulty: 2, question: { en: `What types of interaction between microservices exist?`, ru: `Какие есть виды взаимодействия микросервисов между собой?` } },
      { id: 'microservices-8', difficulty: 2, question: { en: `What are the options for configuring inter-service communication?`, ru: `Какие есть варианты настройки конфигурации для взаимодействия микросервисов?` } },
      { id: 'microservices-9', difficulty: 2, question: { en: `Why is an API Gateway needed?`, ru: `Зачем нужен API Gateway?` } },
      { id: 'microservices-10', difficulty: 3, question: { en: `What options for modular architecture exist? How is it best to organize modules?`, ru: `Какие есть варианты модульной архитектуры? Как лучше организовать модули?` } },
      { id: 'microservices-11', difficulty: 3, question: { en: `What microservice patterns exist?`, ru: `Какие существуют микросервисные паттерны?` } },
      { id: 'microservices-12', difficulty: 3, question: { en: `How do you design an API for registering sales over an unreliable connection?`, ru: `Как проектировать API для регистрации продаж по неустойчивому соединению?` } },
      { id: 'microservices-13', difficulty: 3, question: { en: `How do you avoid duplicates when registering sales?`, ru: `Как избежать дубликатов при регистрации продаж?` } },
      { id: 'microservices-14', difficulty: 3, question: { en: `How do you use data that arrives asynchronously (e.g. exchange rates pushed by another service) at a synchronous decision point — cache it locally / event-carried state transfer?`, ru: `Как использовать данные, приходящие асинхронно (например, курсы, которые другой сервис шлёт по событиям), в момент синхронного решения — локальный кэш / event-carried state transfer?` } },
      { id: 'microservices-15', difficulty: 3, question: { en: `For a balance/accounting service, should you update the current balance in place or append immutable operation records and compute the balance from them (event sourcing / ledger)? What are the trade-offs?`, ru: `Для сервиса балансов: обновлять текущий баланс на месте или добавлять неизменяемые записи операций и считать баланс из них (event sourcing / ledger)? Каковы компромиссы?` } },
    ],
  },
  {
    id: 'rest',
    name: 'RESTful API & Web Services',
    entries: [
      { id: 'rest-1', difficulty: 1, question: { en: `How would you share endpoints with others, e.g. within your organization?`, ru: `Как бы вы поделились эндпоинтами с другими, например, внутри вашей организации?` } },
      { id: 'rest-2', difficulty: 1, question: { en: `What is the difference between the PUT and PATCH methods?`, ru: `В чём разница между методами PUT и PATCH?` } },
      { id: 'rest-3', difficulty: 1, question: { en: `How would you name endpoints for getting and changing data?`, ru: `Как бы вы назвали эндпоинты для получения и изменения данных?` } },
      { id: 'rest-4', difficulty: 1, question: { en: `What is CORS?`, ru: `Что такое CORS?` } },
      { id: 'rest-5', difficulty: 1, question: { en: `How does interaction between the backend and frontend happen?`, ru: `Как происходит взаимодействие между бэкендом и фронтендом?` } },
      { id: 'rest-6', difficulty: 2, question: { en: `How do you design endpoints in a REST controller?`, ru: `Как вы проектируете эндпоинты в REST-контроллере?` } },
      { id: 'rest-7', difficulty: 2, question: { en: `How would you design a security scheme for your endpoints?`, ru: `Как бы вы спроектировали схему безопасности для своих эндпоинтов?` } },
      { id: 'rest-8', difficulty: 2, question: { en: `How would you manage errors and provide error codes?`, ru: `Как бы вы управляли ошибками и предоставляли коды ошибок?` } },
      { id: 'rest-9', difficulty: 2, question: { en: `What are preflight requests?`, ru: `Что такое предварительные запросы (preflight requests)?` } },
      { id: 'rest-10', difficulty: 3, question: { en: `An endpoint worked and passed tests, you shipped to prod. The next day the Product Owner says nothing works. What are your actions?`, ru: `Endpoint работал и прошёл тесты, выкатили в прод. На следующий день Product Owner говорит, что ничего не работает. Какие твои действия?` } },
    ],
  },
  {
    id: 'networking',
    name: 'Real-time & Networking',
    entries: [
      { id: 'networking-1', difficulty: 2, question: { en: `How does a server notify a browser client of an event in real time? What technologies fit (polling, long polling, SSE, WebSocket)?`, ru: `Как сервер уведомляет браузерный клиент о событии в реальном времени? Какие технологии подходят (polling, long polling, SSE, WebSocket)?` } },
      { id: 'networking-2', difficulty: 2, question: { en: `What is long polling and how does it differ from regular HTTP requests?`, ru: `Что такое long polling и чем он отличается от обычных HTTP-запросов?` } },
      { id: 'networking-3', difficulty: 2, question: { en: `How does a WebSocket connection work (handshake, bidirectional), what transport does it use (TCP/UDP), and is a new endpoint instance created per connection or shared?`, ru: `Как работает WebSocket-соединение (рукопожатие, двусторонний обмен), по какому транспорту (TCP/UDP) и создаётся ли новый экземпляр endpoint на каждое соединение или один общий?` } },
      { id: 'networking-4', difficulty: 3, question: { en: `How do you share thread-safe state across all WebSocket connections and guarantee a value is globally unique across every connection and thread?`, ru: `Как разделять потокобезопасное состояние между всеми WebSocket-соединениями и гарантировать глобальную уникальность значения для всех соединений и потоков?` } },
    ],
  },
  {
    id: 'security',
    name: 'Security & Authentication',
    entries: [
      { id: 'security-1', difficulty: 1, question: { en: `What is cross-site scripting (XSS)?`, ru: `Что такое межсайтовый скриптинг (XSS)?` } },
      { id: 'security-2', difficulty: 1, question: { en: `What is CSRF (cross-site request forgery)?`, ru: `Что такое CSRF (межсайтовая подделка запроса)?` } },
      { id: 'security-3', difficulty: 1, question: { en: `How do you picture authentication working in a system?`, ru: `Как ты представляешь работу аутентификации в системе?` } },
      { id: 'security-4', difficulty: 2, question: { en: `What is JWT? When should you use a session token and when a JWT? For client-server or server-server?`, ru: `Что такое JWT? Когда использовать токен сессии, а когда JWT? Для client-server или server-server?` } },
      { id: 'security-5', difficulty: 2, question: { en: `How does the OAuth or OpenID Connect protocol work?`, ru: `Как работает протокол OAuth или OpenID Connect?` } },
    ],
  },
  {
    id: 'devops',
    name: 'DevOps / CI/CD / Kubernetes / Docker',
    entries: [
      { id: 'devops-1', difficulty: 2, question: { en: `How do you build a Docker image from your Spring Boot application?`, ru: `Как создать Docker-образ из вашего Spring Boot приложения?` } },
      { id: 'devops-2', difficulty: 2, question: { en: `Which variables have higher priority — those in application.properties or environment variables?`, ru: `Какие переменные имеют более высокий приоритет — в application.properties или переменные окружения?` } },
      { id: 'devops-3', difficulty: 2, question: { en: `What will an environment variable look like if you need to override, for example, job.timeout?`, ru: `Как будет выглядеть переменная окружения, если нужно переопределить, например, job.timeout?` } },
      { id: 'devops-4', difficulty: 2, question: { en: `Why is Kubernetes needed?`, ru: `Зачем нужен Kubernetes?` } },
      { id: 'devops-5', difficulty: 2, question: { en: `What is a Network Policy Definition?`, ru: `Что такое Network Policy Definition?` } },
      { id: 'devops-6', difficulty: 2, question: { en: `What are the ways to build applications?`, ru: `Какие есть способы сборки приложений?` } },
      { id: 'devops-7', difficulty: 2, question: { en: `What are the variants of the CI/CD process (e.g. via a pipeline in TeamCity)?`, ru: `Какие есть варианты процесса прохождения CI/CD (например, через pipeline в TeamCity)?` } },
      { id: 'devops-8', difficulty: 2, question: { en: `Why is Kibana needed? Where do the logs there come from?`, ru: `Зачем нужна Kibana? Откуда там появляются логи?` } },
    ],
  },
  {
    id: 'performance',
    name: 'Performance Optimization & Troubleshooting',
    entries: [
      { id: 'performance-1', difficulty: 2, question: { en: `How do you find the cause of a slow website?`, ru: `Как найти причину медленной работы сайта?` } },
      { id: 'performance-2', difficulty: 2, question: { en: `What application metrics do you know?`, ru: `Какие метрики приложения ты знаешь?` } },
      { id: 'performance-3', difficulty: 3, question: { en: `A service only writes data to PostgreSQL, the data is large (~30 MB), many services use its API. How can you improve write performance?`, ru: `Сервис только пишет в PostgreSQL, данные большие (~30 МБ), много сервисов используют его API. Как улучшить производительность записи?` } },
      { id: 'performance-4', difficulty: 3, question: { en: `What to do if a service in production stops responding to requests?`, ru: `Что делать, если сервис на проде перестал отвечать на запросы?` } },
    ],
  },
  {
    id: 'kotlin',
    name: 'Kotlin',
    entries: [
      { id: 'kotlin-1', difficulty: 2, question: { en: `How do Kotlin Coroutines work?`, ru: `Как работают Kotlin Coroutines?` } },
    ],
  },
  {
    id: 'messaging',
    name: 'Queues and Message Brokers',
    entries: [
      { id: 'messaging-1', difficulty: 2, question: { en: `How do queues like RabbitMQ work?`, ru: `Как работают очереди типа RabbitMQ?` } },
      { id: 'messaging-2', difficulty: 2, question: { en: `What is the difference between a binding key and a routing key in RabbitMQ?`, ru: `В чём разница между binding key и routing key в RabbitMQ?` } },
    ],
  },
];

/**
 * Resolves a URL/cross-link id to a catalog entry. Accepts either a catalog entry
 * id (`java-collections-7`, `topic-inbox-pattern`) or a topic id (`inbox-pattern`),
 * so links and routes can reference whichever is convenient.
 */
export function findCatalogEntry(
  cats: CatalogCategory[],
  id: string,
): { entry: CatalogEntry; categoryId: string } | null {
  for (const c of cats) for (const e of c.entries) if (e.id === id) return { entry: e, categoryId: c.id };
  for (const c of cats) for (const e of c.entries) if (e.topicId === id) return { entry: e, categoryId: c.id };
  return null;
}

/** Difficulty rendered as stars (1 = Junior, 2 = Middle, 3 = Senior). */
export function stars(difficulty: Difficulty): string {
  return '★'.repeat(difficulty);
}

function normalizeDifficulty(d: number): Difficulty {
  return d === 1 || d === 3 ? d : 2;
}

/** Human-readable fallback name for a freshly-invented category id. */
function prettifyCategoryId(id: string): string {
  return id
    .split('-')
    .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
    .join(' ');
}

/**
 * Merges generated topics into the static catalog:
 *  - a topic whose {@link TopicSummary.catalogId} matches a question attaches its
 *    theory to that entry;
 *  - a topic without a matching question is added as a new entry under the
 *    category it declared (so freshly generated topics appear in the tree).
 * The static catalog is left untouched (a deep copy is returned).
 */
export function buildCatalog(topics: TopicSummary[]): CatalogCategory[] {
  const cats: CatalogCategory[] = CATALOG.map((c) => ({ ...c, entries: c.entries.map((e) => ({ ...e })) }));
  const byCategory = new Map(cats.map((c) => [c.id, c]));
  const entryById = new Map<string, CatalogEntry>();
  for (const c of cats) for (const e of c.entries) entryById.set(e.id, e);

  const linkedTopicIds = new Set<string>();
  for (const c of cats) for (const e of c.entries) if (e.topicId) linkedTopicIds.add(e.topicId);

  for (const t of topics) {
    // Re-attach to the originating question, if any.
    if (t.catalogId && entryById.has(t.catalogId)) {
      const entry = entryById.get(t.catalogId)!;
      if (!entry.topicId) {
        entry.topicId = t.id;
        linkedTopicIds.add(t.id);
      }
      continue;
    }
    if (linkedTopicIds.has(t.id)) continue; // already placed (e.g. a static link)
    if (!t.categoryId) continue; // legacy topics without metadata stay statically linked

    let cat = byCategory.get(t.categoryId);
    if (!cat) {
      // A category Claude invented because nothing existing fit.
      cat = { id: t.categoryId, name: t.categoryName || prettifyCategoryId(t.categoryId), entries: [] };
      cats.push(cat);
      byCategory.set(cat.id, cat);
    }
    cat.entries.push({
      id: `topic-${t.id}`,
      question: t.title,
      difficulty: normalizeDifficulty(t.difficulty),
      topicId: t.id,
    });
    linkedTopicIds.add(t.id);
  }
  return cats;
}
