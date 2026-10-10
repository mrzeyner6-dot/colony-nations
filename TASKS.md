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
Статус: DONE

- Підключити MineColonies для NeoForge 1.21.1 як залежність компіляції й запуску, плюс runtime-залежності: Structurize, BlockUI, Domum Ornamentum, MultiPiston.
- Спершу спробуй офіційний maven LDT Team (дивись README / build-файли репозиторію `ldtteam/minecolonies`). Якщо не виходить — Curse Maven (`https://cursemaven.com`, `curse.maven:<slug>-<projectId>:<fileId>`, fileId береться зі сторінки файлу на CurseForge; приклад: `curse.maven:minecolonies-245506:7629602`).
- Версії винеси в `gradle.properties`.
- Додати залежність MineColonies у `neoforge.mods.toml` (type `required`, side `BOTH`).

Готово, коли: `build` проходить; `runServer` стартує з MineColonies у списку модів; у звіті — точні координати й версії всіх залежностей.

Звіт:
- Репозиторій: офіційний LDT Team maven `https://ldtteam.jfrog.io/ldtteam/modding/` (з `settings.gradle` репозиторію `ldtteam/minecolonies`, гілка `version/1.21`). Curse Maven не знадобився.
- Версії взято з Gradle module metadata релізу `minecolonies` `1.1.1403-1.21.1` (той самий jar, що на CurseForge, 4 Oct 2026). Усі з `transitive = false`, щоб не підтягнути JEI і старіші blockui/domum зі structurize/multipiston.
  - `com.ldtteam:minecolonies:1.1.1403-1.21.1` — `implementation` (компіляція і запуск).
  - `com.ldtteam:structurize:1.0.832-1.21.1-snapshot` — `runtimeOnly`, mod id `structurize`.
  - `com.ldtteam:blockui:1.0.199-1.21.1-snapshot` — `runtimeOnly`, mod id `blockui`.
  - `com.ldtteam:domum-ornamentum:1.0.223-snapshot` — `runtimeOnly`, mod id `domum_ornamentum` (jar з classifier `main` через module metadata).
  - `com.ldtteam:multipiston:1.2.51-1.21.1-snapshot` — `runtimeOnly`, mod id `multipiston`. У `neoforge.mods.toml` MineColonies його немає серед required (CurseForge і ця задача його вимагають); сервер з ним стартує.
- У `neoforge.mods.toml`: `minecolonies`, type `required`, `versionRange=[1.1.1403-1.21.1,)`, ordering `AFTER`, side `BOTH`.
- `./gradlew build` пройшов. `runServer` дійшов до `Done`. Список модів: Colony Nations 0.1.0, Domum Ornamentum 1.0.223-snapshot, MineColonies 1.1.1403-1.21.1, Multi-Piston 1.2.51-1.21.1-snapshot, NeoForge 21.1.256, Structurize 1.0.832-1.21.1-snapshot, UI Library Mod 1.0.199-1.21.1-snapshot.
- На DEBUG MineColonies ловить `IllegalStateException` для loot table `minecolonies:entities/cavalry_horse` під час compat discovery. Процес після цього лишається живим. У грі перевірити, чи попередження повторюється і чи не ламає рецепти або рейди.

### T4. Дослідити API MineColonies (без коду мода)
Статус: DONE

Створи `docs/notes/minecolonies-api.md` з відповідями (назви класів/методів, короткі приклади):
1. Як отримати колонію за id і всі колонії світу (`IColonyManager` тощо).
2. Як дізнатись і змінити власника колонії; як працюють ранги/дозволи (`IPermissions`, `Rank`).
3. Які події є на створення / видалення колонії, ріст рівня ратуші, додавання жителя (NeoForge events або власна шина MineColonies).
4. Чи є опція конфігу «кілька колоній на гравця»; якщо ні — де в коді перевірка, яку довелось би обійти Mixin'ом.
5. Як читати й змінювати щастя колонії / жителів.
6. Як отримати список жителів, їхню стать, вік (дитина/дорослий), роботу, і чи є житель охоронцем.
7. Як отримати межі клейму колонії (чанки).

Готово, коли: файл є, кожен пункт має відповідь або чесне «не знайдено, шукав у …».

Звіт:
- Файл `docs/notes/minecolonies-api.md`. Джерело — sources jar `com.ldtteam:minecolonies:1.1.1403-1.21.1`. Коду мода не додавав.
- Усі 7 пунктів мають відповідь. Окремої NeoForge-події на створення/видалення колонії, рівень ратуші чи нового жителя немає: це власна шина `IMinecoloniesAPI.getEventBus()` (`ColonyCreatedModEvent`, `ColonyDeletedModEvent` з `ColonyManager`, `CitizenAddedModEvent`, `BuildingConstructionModEvent` для завершеного наряду ратуші).
- Конфіг `allowinfinitecolonies` є (default false), але сервер його не читає при заснуванні. Заборона другої колонії — `CreateColonyMessage.onExecute` через `getIColonyByOwner` по всіх колоніях. Це вхід для Mixin у T8.
- Числового віку жителя і прямого setter щастя немає: вік — `isChild()`, щастя колонії — середнє модифікаторів жителів.
- У грі перевіряти нічого.

### T5. Модель держави
Статус: DONE

Чисті класи в `nation/` (без Minecraft-типів, де можливо):
- `Nation`: `UUID id`, `String name`, `UUID owner`, `int color` (RGB), `Integer capitalColonyId` (nullable), `Set<Integer> colonyIds`, `long treasury` (в найменшій монеті Numismatics — spur), `long createdGameTime`.
- `NationRegistry`: створити / видалити / перейменувати; пошук за id, за назвою (без урахування регістру), за власником, за колонією. Правила: унікальна назва; 3–24 символи; один гравець — одна держава; колонія належить максимум одній державі; перша додана колонія стає столицею; при видаленні столиці столицею стає колонія з найменшим id (тимчасово, до правила «найбільше місто»).
- `NationSavedData` (в `data/`): серіалізація реєстру в NBT і назад.

Готово, коли: `build` проходить; JUnit-тести покривають усі правила реєстру й повний цикл серіалізації (зберегти → завантажити → рівні дані).

Звіт:
- Чисті класи `nation/Nation`, `NationRegistry`, `NationException`, `NationCodec`. `data/NationSavedData` кладе байти кодека в NBT-поле `Payload` (`SavedData` на overworld ще не підключено — це наступні задачі).
- Правила: назва 3–24 коди (після `trim`), унікальна без регістру (`Locale.ROOT`); один власник — одна держава; колонія щонайбільше в одній державі; перша колонія стає столицею; після зняття столиці столицею стає колонія з найменшим id, або `null`, якщо міст не лишилось.
- Розбіжність із `docs/DESIGN.md`: там після падіння столиці тимчасово працює найбільше місто. Задача вимагає найменший id — зроблено так.
- JUnit не піднімає класи Minecraft (`SavedData` тягне `LogUtils`, якого немає на test classpath ModDevGradle). Тому цикл «зберегти → завантажити → рівні дані» перевіряє `NationCodec`, ті самі байти, які `NationSavedData` пише в NBT. `NationRegistryTest` — 9 тестів.
- `./gradlew build` пройшов.
- У грі перевіряти нічого: команд і збереження світу ще немає.

### T6. Команди держави
Статус: DONE

Корінь `/nation`:
- `create <name>`, `disband` (з підтвердженням: повторна команда протягом 30 с), `rename <name>`, `color <#RRGGBB>`, `info [name]`, `list`.
- Тільки власник змінює свою державу; оператори (permission level 2) — будь-яку через `/nation admin <name> ...`.
- Усі повідомлення через ключі локалізації (en + uk).

Готово, коли: `build` проходить; у звіті — повний список команд з прикладами виводу.

Звіт:
- Команди в `command/NationCommands`. Стан — `NationSavedData` на overworld (`colony_nations.dat`), `setDirty()` після змін.
- Змінювати свою державу може власник. Будь-яку — оператор (permission level 2) через `/nation admin <name> ...`. Назва — один аргумент Brigadier; пробіл у назві береться в лапках.
- Підтвердження розпуску: повтор тієї самої команди протягом `disbandConfirmSeconds` (конфіг, default 30). Вікно в тіках ігрового часу, у пам'яті, після рестарту скидається. Тест `DisbandConfirmationsTest`.
- Нова держава отримує колір `#FFFFFF`.
- `./gradlew build` пройшов.

Команди й приклади виводу (en_us; uk_ua є в `uk_ua.json`):

- `/nation create Rome` → `Founded nation Rome.`
- `/nation create Ro` → `Nation name must be 3–24 characters.`
- `/nation create Rome` (назва зайнята) → `A nation with that name already exists.`
- `/nation create Gaul` (у гравця вже є держава) → `You already have a nation.`
- `/nation disband` → `Run the disband command again within 30 seconds to disband Rome.`
- `/nation disband` (повтор) → `Disbanded Rome.`
- `/nation rename Roma` → `Renamed Rome to Roma.`
- `/nation color #336699` → `Set Rome color to #336699.`
- `/nation color red` → `Color must look like #RRGGBB.`
- `/nation info` або `/nation info Rome` →
  `Nation Rome` / `Owner: Steve` / `Color: #336699` / `Capital colony: —` / `Colonies: —` / `Treasury: 0`
- `/nation info Missing` → `No nation named Missing.`
- `/nation info` (немає держави) → `You have no nation.`
- `/nation list` → `Nations (1):` / `- Rome (owner Steve, colonies 0)`
- `/nation list` (порожньо) → `There are no nations.`
- `/nation admin Rome rename Gaul` → `Renamed Rome to Gaul.`
- `/nation admin Rome color #00FF00` → `Set Rome color to #00FF00.`
- `/nation admin Rome disband` → те саме підтвердження, потім `Disbanded Rome.`

У грі: зайти гравцем, пройти create → info → color → rename → disband двічі; оператором змінити чужу державу через admin; перевірити, що після рестарту `/nation list` той самий.

### T7. Міста: прив'язка колоній до держави
Статус: DONE

- Колонія, створена гравцем із державою, автоматично додається до його держави.
- `/nation city add <colonyId>` і `/nation city remove <colonyId>` — для власника колонії, що є власником держави.
- `/nation city list` — список міст держави з назвами колоній, столиця позначена.
- `/nation capital <colonyId>` — змінити столицю.
- Видалення колонії в MineColonies прибирає її з держави.

Готово, коли: `build` проходить; у звіті — які події MineColonies використано і що перевірити в грі.

Звіт:
- Події власної шини MineColonies (не NeoForge), підписка в `NationColonyEvents.register` з `FMLCommonSetupEvent.enqueueWork`:
  - `ColonyCreatedModEvent` — якщо власник нової колонії вже має державу, колонія додається до неї (перша стає столицею). Гравцю йде `command.colony_nations.city.auto`.
  - `ColonyDeletedModEvent` (`ColonyManager` поститься перед видаленням) — id колонії прибирається з держави; якщо це була столиця, столицею стає колонія з найменшим id.
- Команди (власник держави і власник колонії в MineColonies):
  - `/nation city add <colonyId>` → `Added MyColony (#3) to Rome.`
  - `/nation city remove <colonyId>` → `Removed MyColony (#3) from Rome.`
  - `/nation city list` → `Cities of Rome:` / `- MyColony (#3) — capital` / `- Outpost (#4)`
  - `/nation capital <colonyId>` → `Capital of Rome is now Outpost (#4).`
  - чужа колонія → `You do not own colony #3.`
  - немає в державі → `Colony #3 is not part of Rome.`
- Id колонії в MineColonies унікальний у межах виміру, а в моделі держави зберігається лише `int`. Якщо в різних вимірах збіжаться id, `byColony` може зачепити не ту. Для етапу 1 (overworld) цього достатньо.
- `./gradlew build` пройшов. `runServer` дійшов до `Done`, common setup без помилки.
- У грі: заснувати державу, поставити ратушу — колонія має з'явитись у `/nation city list` як столиця; другу колонію додати командою (поки гравець не може мати другу колонію MineColonies — це T8); `/nation capital`; видалити колонію в MineColonies і перевірити, що її немає в списку міст.

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
