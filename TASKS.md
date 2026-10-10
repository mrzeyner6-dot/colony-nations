# Задачі

Статуси: `TODO` → `IN PROGRESS` → `DONE` / `BLOCKED`. Протокол — у `.cursorrules`.
Задачі ставить і перевіряє Claude (рев'ю в `docs/REVIEW.md`). Позначка **ЧЕКАЄ ТЕСТУ** = зупинись, тестує Rib у грі.

---

## Етап 1 — ядро держави

### T1. Перейменувати шаблон MDK
Статус: DONE

- `gradle.properties`: `mod_id=colony_nations`, `mod_name=Colony Nations`, `mod_group_id=com.mrzeyner.colonynations`, `mod_version=0.1.0`.
- Пакет `com.example.examplemod` → `com.mrzeyner.colonynations`, головний клас `ColonyNations`, клієнтський `ColonyNationsClient`.
- Прибрати весь приклад: example block/item/creative tab, приклад конфігу (сам клас `Config` лишити порожнім каркасом під `ModConfigSpec`).
- Ресурси: `assets/colony_nations/lang/en_us.json` і `uk_ua.json` (поки порожні `{}`).
- Ліцензію поки лишити `All Rights Reserved` (рішення буде пізніше).

Готово, коли: `./gradlew build` проходить; `./gradlew runServer` стартує (у `run/eula.txt` постав `eula=true`) і в лозі видно мод `colony_nations`; в коді не лишилось `examplemod`.

Звіт:
- `gradle.properties`: `mod_id=colony_nations`, `mod_name=Colony Nations`, `mod_group_id=com.mrzeyner.colonynations`, `mod_version=0.1.0`. Ліцензія лишилась `All Rights Reserved`.
- Пакет `com.mrzeyner.colonynations`: головний клас `ColonyNations`, клієнтський `ColonyNationsClient`, клас `Config` — порожній каркас `ModConfigSpec` без прикладів значень.
- Прибрано example block, item, creative tab і приклад конфігу. Ресурси: `assets/colony_nations/lang/en_us.json` і `uk_ua.json` — порожні `{}`.
- У `neoforge.mods.toml` опис замінено з прикладу на короткий опис аддона, щоб в коді не лишилось `examplemod` (рядок задачі в цьому файлі лишився як є).
- `./gradlew build` пройшов. `./gradlew runServer` з `run/eula.txt` (`eula=true`) дійшов до `Done`; у лозі є `Colony Nations 0.1.0 (colony_nations)` і `Colony Nations loaded (mod id: colony_nations)`.
- NeoForge на DEBUG пише, що COMMON-конфіг порожній — очікувано, поки в `Config` немає значень.
- У грі: перевірити, що мод є в списку модів під назвою Colony Nations 0.1.0.

### T2. Налаштувати юніт-тести
Статус: DONE

- Додати JUnit 5 у `build.gradle` (`testImplementation`, `useJUnitPlatform()`).
- Один тест-заглушка в `src/test/java/com/mrzeyner/colonynations/SanityTest.java`.

Готово, коли: `./gradlew test` запускає тест і він проходить; `build` теж запускає тести.

Звіт:
- У `build.gradle` додано JUnit 5.11.4 (`junit-bom` + `junit-jupiter`) і `useJUnitPlatform()`.
- Gradle 9 не кладе JUnit Platform launcher на classpath тестів сам, тому додано `testRuntimeOnly` `junit-platform-launcher` (версію бере BOM).
- Заглушка: `src/test/java/com/mrzeyner/colonynations/SanityTest.java` (`junitPlatformRuns`).
- `./gradlew test` і `./gradlew build` пройшли; у `TEST-com.mrzeyner.colonynations.SanityTest.xml` один тест, failures=0.
- У грі перевіряти нічого.

### T3. Підключити MineColonies
Статус: TODO

- Підключити MineColonies для NeoForge 1.21.1 як залежність компіляції й запуску, плюс runtime-залежності: Structurize, BlockUI, Domum Ornamentum, MultiPiston.
- Спершу спробуй офіційний maven LDT Team (дивись README / build-файли репозиторію `ldtteam/minecolonies`). Якщо не виходить — Curse Maven (`https://cursemaven.com`, `curse.maven:<slug>-<projectId>:<fileId>`, fileId береться зі сторінки файлу на CurseForge; приклад: `curse.maven:minecolonies-245506:7629602`).
- Версії винеси в `gradle.properties`.
- Додати залежність MineColonies у `neoforge.mods.toml` (type `required`, side `BOTH`).

Готово, коли: `build` проходить; `runServer` стартує з MineColonies у списку модів; у звіті — точні координати й версії всіх залежностей.

### T4. Дослідити API MineColonies (без коду мода)
Статус: TODO

Створи `docs/notes/minecolonies-api.md` з відповідями (назви класів/методів, короткі приклади):
1. Як отримати колонію за id і всі колонії світу (`IColonyManager` тощо).
2. Як дізнатись і змінити власника колонії; як працюють ранги/дозволи (`IPermissions`, `Rank`).
3. Які події є на створення / видалення колонії, ріст рівня ратуші, додавання жителя (NeoForge events або власна шина MineColonies).
4. Чи є опція конфігу «кілька колоній на гравця»; якщо ні — де в коді перевірка, яку довелось би обійти Mixin'ом.
5. Як читати й змінювати щастя колонії / жителів.
6. Як отримати список жителів, їхню стать, вік (дитина/дорослий), роботу, і чи є житель охоронцем.
7. Як отримати межі клейму колонії (чанки).

Готово, коли: файл є, кожен пункт має відповідь або чесне «не знайдено, шукав у …».

### T5. Модель держави
Статус: TODO

Чисті класи в `nation/` (без Minecraft-типів, де можливо):
- `Nation`: `UUID id`, `String name`, `UUID owner`, `int color` (RGB), `Integer capitalColonyId` (nullable), `Set<Integer> colonyIds`, `long treasury` (в найменшій монеті Numismatics — spur), `long createdGameTime`.
- `NationRegistry`: створити / видалити / перейменувати; пошук за id, за назвою (без урахування регістру), за власником, за колонією. Правила: унікальна назва; 3–24 символи; один гравець — одна держава; колонія належить максимум одній державі; перша додана колонія стає столицею; при видаленні столиці столицею стає колонія з найменшим id (тимчасово, до правила «найбільше місто»).
- `NationSavedData` (в `data/`): серіалізація реєстру в NBT і назад.

Готово, коли: `build` проходить; JUnit-тести покривають усі правила реєстру й повний цикл серіалізації (зберегти → завантажити → рівні дані).

### T6. Команди держави
Статус: TODO

Корінь `/nation`:
- `create <name>`, `disband` (з підтвердженням: повторна команда протягом 30 с), `rename <name>`, `color <#RRGGBB>`, `info [name]`, `list`.
- Тільки власник змінює свою державу; оператори (permission level 2) — будь-яку через `/nation admin <name> ...`.
- Усі повідомлення через ключі локалізації (en + uk).

Готово, коли: `build` проходить; у звіті — повний список команд з прикладами виводу.

### T7. Міста: прив'язка колоній до держави
Статус: TODO (залежить від T4)

- Колонія, створена гравцем із державою, автоматично додається до його держави.
- `/nation city add <colonyId>` і `/nation city remove <colonyId>` — для власника колонії, що є власником держави.
- `/nation city list` — список міст держави з назвами колоній, столиця позначена.
- `/nation capital <colonyId>` — змінити столицю.
- Видалення колонії в MineColonies прибирає її з держави.

Готово, коли: `build` проходить; у звіті — які події MineColonies використано і що перевірити в грі.

### T8. Кілька колоній на одного гравця
Статус: TODO (залежить від T4)

- Якщо в MineColonies є опція конфігу — задокументуй її в `docs/notes/server-setup.md` і нічого не пиши.
- Якщо немає — Mixin, який дозволяє створити нову колонію, коли гравець має державу і місце ратуші — на землі його держави. Поки територій ще немає: дозволяй, якщо гравець має державу (перевірку землі додамо в етапі 2 — залиш `// TODO(T-territory)`).

Готово, коли: `build` проходить; у звіті — який підхід обрано і чому.

### T9. Скарбниця (монети Numismatics)
Статус: TODO

- Підключити Create і Create: Numismatics (NeoForge 1.21.1) як залежності; координати — офіційний maven або Modrinth maven (`https://api.modrinth.com/maven`, `maven.modrinth:<slug>:<version>`).
- `compat/numismatics/`: перетворення стаку монет у вартість у spur і назад (номінали брати з API/енуму Numismatics, не хардкодити).
- `/nation treasury` — баланс; `/nation treasury deposit` — внести всі монети з руки; `/nation treasury withdraw <amount>` — видати монети найбільшими номіналами (власник держави).
- Логіка розміну — чистий клас з JUnit-тестами.

Готово, коли: `build` проходить; тести розміну проходять; у звіті — що перевірити в грі.

### ЧЕКАЄ ТЕСТУ — Rib перевіряє етап 1 у грі
Зупинись тут. Claude допише нові задачі після тесту.
