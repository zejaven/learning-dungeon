# API сотрудника: Команды

> **Режим практики.** Это *challenge*-тема: реализуй методы в `Solution.java`, жми
> **Run tests**, и скрытый харнесс оценит твой код кейс за кейсом. Дизайн за этим —
> в теме [API сотрудника: Дизайн](topic:employee-api-design); концепции — в
> [API сотрудника: REST и разделение ответственности](topic:employee-api-rest-cqrs).

## Задача

Реализуй in-memory логику, которой делегировали бы контроллеры — четыре команды,
без Spring и HTTP:

- `addEmployee(name, surname, passportNumber, passportDate, salary)` → возвращает
  новый уникальный `id` (начиная с `1` и по возрастанию).
- `changeSalary(id, newSalary)` → меняет **только** зарплату.
- `changePassport(id, passportNumber, passportDate)` → меняет **только** паспортные
  поля.
- `getCard(id)` → возвращает карточку ответа строкой ровно в таком формате:

  ```
  name + " " + surname + " | " + passportNumber + " | " + passportDate + " | " + salary
  ```

  например, `"Ann Lee | AA111 | 2020-01-15 | 1000"`.

Операция над id, которого никогда не добавляли, обязана **бросить исключение**
(например, `NoSuchElementException`).

## Суть: независимость команд

Скрытые тесты проверяют не то, что ты умеешь хранить запись, — они проверяют, что
каждая команда трогает **только свою зону**:

- после `changeSalary` паспортные поля не изменились;
- после `changePassport` зарплата не изменилась;
- два сотрудника получают разные id, и правка одного никогда не влияет на другого.

Эта независимость — отражение на уровне кода заметки про «разные отделы» из задачи:
каждая команда — маленькая запись с единственным назначением.

## Аккуратный подход

Держи по одному маленькому холдеру на сотрудника в `Map` по ключу id плюс счётчик
следующего id. Каждая команда находит свой холдер и меняет только своё поле:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class Solution {
    private static final class Record {
        String name, surname, passportNumber, passportDate;
        long salary;
    }

    private final Map<Long, Record> byId = new HashMap<>();
    private long nextId = 0;

    public long addEmployee(String name, String surname,
                            String passportNumber, String passportDate, long salary) {
        Record r = new Record();
        r.name = name; r.surname = surname;
        r.passportNumber = passportNumber; r.passportDate = passportDate;
        r.salary = salary;
        long id = ++nextId;
        byId.put(id, r);
        return id;
    }

    private Record require(long id) {
        Record r = byId.get(id);
        if (r == null) throw new NoSuchElementException("no employee " + id);
        return r;
    }

    public void changeSalary(long id, long newSalary) {
        require(id).salary = newSalary;            // только зарплата
    }

    public void changePassport(long id, String number, String date) {
        Record r = require(id);
        r.passportNumber = number;                 // только паспорт
        r.passportDate = date;
    }

    public String getCard(long id) {
        Record r = require(id);
        return r.name + " " + r.surname + " | " + r.passportNumber
                + " | " + r.passportDate + " | " + r.salary;
    }
}
```

## Ответ за 60 секунд

> Я храню каждого сотрудника в map по сгенерированному id и выставляю каждое
> изменение отдельной командой. `changeSalary` пишет только поле зарплаты,
> `changePassport` — только паспортные поля, поэтому две зоны никогда не
> пересекаются, что отражает два владеющих ими отдела. `getCard` читает текущее
> состояние и маппит его в форму ответа, отдельно от того, как я храню данные.
> Неизвестные id бросают исключение, так что вызывающий получает явную ошибку, а не
> тихий no-op.

## Типичные ловушки

- ❌ **Единственный `update(...)`, перезаписывающий всё.** Снова связывает зарплату и
  паспорт и ломает тесты на независимость.
- ❌ **Пересоздание записи при каждом изменении.** Если `changeSalary` собирает холдер
  заново, он может стереть паспортные поля. Меняй только своё единственное поле.
- ❌ **Возврат хранимого объекта вместо форматированной карточки.** Формат карточки —
  часть контракта; собери строку ровно как указано.
- ❌ **Тихий игнор неизвестных id.** Тесты ждут брошенного исключения, а не `null`
  или no-op.
- ❌ **Неверная последовательность id.** Id начинаются с `1` и растут на единицу при
  каждом `addEmployee`.
