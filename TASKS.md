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
Статус: DONE

- Якщо в MineColonies є опція конфігу — задокументуй її в `docs/notes/server-setup.md` і нічого не пиши.
- Якщо немає — Mixin, який дозволяє створити нову колонію, коли гравець має державу і місце ратуші — на землі його держави. Поки територій ще немає: дозволяй, якщо гравець має державу (перевірку землі додамо в етапі 2 — залиш `// TODO(T-territory)`).

Готово, коли: `build` проходить; у звіті — який підхід обрано і чому.

Звіт:
- Ключ `allowinfinitecolonies` у MineColonies є, але сервер його ігнорує: він лише показує кнопку abandon у клієнтському вікні ратуші. `CreateColonyMessage.onExecute` все одно відхиляє другу колонію, якщо `getIColonyByOwner` непорожній. Тому гілка «лише документація» не підходить. Описано в `docs/notes/server-setup.md`.
- Mixin `CreateColonyMessageMixin` (`@Redirect` на `IColonyManager.getIColonyByOwner(Level, Player)`): якщо гравець уже має державу Colony Nations, метод повертає `null` і MineColonies йде звичайним шляхом створення. Нова колонія далі сама додається до держави через `ColonyCreatedModEvent` (T7). Гравець без держави як і раніше отримує відмову. У коді лишився `// TODO(T-territory)`.
- `runServer` дійшов до `Done`. У `debug.log`: `Mixing CreateColonyMessageMixin from colony_nations.mixins.json into com.minecolonies.core.network.messages.server.CreateColonyMessage`. Згенерований `neoforge.mods.toml` містить `[[mixins]] config="colony_nations.mixins.json"`.
- У грі: гравець із державою ставить другу ратушу — колонія створюється і з'являється в `/nation city list`; гравець без держави другу колонію заснувати не може.

### T9. Скарбниця (монети Numismatics)
Статус: DONE

- Підключити Create і Create: Numismatics (NeoForge 1.21.1) як залежності; координати — офіційний maven або Modrinth maven (`https://api.modrinth.com/maven`, `maven.modrinth:<slug>:<version>`).
- `compat/numismatics/`: перетворення стаку монет у вартість у spur і назад (номінали брати з API/енуму Numismatics, не хардкодити).
- `/nation treasury` — баланс; `/nation treasury deposit` — внести всі монети з руки; `/nation treasury withdraw <amount>` — видати монети найбільшими номіналами (власник держави).
- Логіка розміну — чистий клас з JUnit-тестами.

Готово, коли: `build` проходить; тести розміну проходять; у звіті — що перевірити в грі.

Звіт:
- Create `6.0.10+mc1.21.1` і Numismatics `1.1.0+neoforge-mc1.21.1` з Modrinth maven (`maven.modrinth`). Версії й діапазони — у `gradle.properties`. Numismatics вимагає Create `[6.0.10,6.1.0)`. Ponder, Flywheel і Registrate приїжджають jar-in-jar усередині Create, окремими залежностями їх не додавав.
- Номінал у spur береться з енуму `Coin.value` (`dev.ithundxr.createnumismatics.content.backend.Coin`), не з констант у коді. `NumismaticsCoins` рахує стак у руці і збирає зворотні `ItemStack` (ріже по max stack size).
- Розмін — чистий `economy/CoinChange`: сума монет у spur і виплата найбільшими номіналами. Якщо немає монети в 1 spur, залишок лишається в скарбниці. У Numismatics 1.1.0 spur = 1, тож виплата точна. Тести: `CoinChangeTest`.
- Команди власника держави (суми в spur):
  - `/nation treasury` → `Treasury of Rome: 0 spurs.`
  - `/nation treasury deposit` — увесь стак з основної руки. Не монета → `Hold Numismatics coins in your main hand.`
  - `/nation treasury withdraw 100` → `Withdrew 100 spurs from Rome. Treasury: …` і монети найбільшими номіналами (для 100: 1 cog, 2 sprocket, 4 spur).
  - Сума більша за баланс → `Rome only has N spurs in the treasury.`
  - Якщо інвентар повний, решта монет падає біля гравця.
- `./gradlew build` пройшов, тести розміну теж. `runServer` дійшов до `Done`. У списку модів: Create 6.0.10, Numismatics 1.1.0, Ponder, Flywheel.
- У грі: заснувати державу; взяти в руку стак монет Numismatics; `/nation treasury deposit`; `/nation treasury`; `/nation treasury withdraw 100`; перевірити, що баланс збігається і що гравець без держави команди не проходить.

### T10. Стиль повідомлень і звуки
Статус: DONE (після R1-5 і R1-6)

Мета: усі повідомлення мода в чаті мають бути впізнаваними й приємними для читання, у фіолетовій гамі, з легкими звуками. Правила — у розділі «Стиль повідомлень» у `.cursorrules`.

- Клас `command/Messages` (або `ui/Messages`): єдине місце, яке будує всі `Component` мода. Методи: `success`, `error`, `info`, `header`, `line`, `event` (для важливих подій: заснування держави, нове місто).
- Перевести на нього всі наявні повідомлення: команди `/nation`, міста, скарбниця, автододавання міста, unreadable-сейв.
- Багаторядкові відповіді (`info`, `list`, `city list`, `treasury`) — із заголовком і вирівняними рядками «назва: значення»; у `info` біля кольору — квадратик `■` цього кольору.
- Звуки лише тому гравцю, якому адресовано повідомлення (`ServerPlayer#playNotifySound`), тихо: успіх — `NOTE_BLOCK_CHIME` (гучність 0.4, висота 1.4), помилка — `NOTE_BLOCK_BASS` (0.4, 0.8), важлива подія — `PLAYER_LEVELUP` (0.3, 1.2). Від консолі й командних блоків — без звуку.
- Конфіг (клієнтський, `ModConfigSpec` CLIENT): `chatSounds` (true/false). Якщо конфіг клієнтський, сервер не знає налаштування — тоді зроби серверний `chatSounds` у COMMON і задокументуй це у звіті.
- Тексти лишаються в `en_us.json` / `uk_ua.json`; колір і стиль накладаються в коді, не в перекладах.

Готово, коли: `build` зелений; у звіті — 3–4 приклади, як тепер виглядають повідомлення (опиши кольори словами), і що перевірити в грі.

Звіт:
- Усі повідомлення мода збирає `command/Messages` (`success`, `error`, `info`, `header`, `line`, `event`). Команди, автододавання міста й unreadable-сейв більше не кличуть `Component` самі.
- Префікс `✦` фіолетовий. Звичайний текст лавандовий, значення (назви, числа, id) блідо-бузкові і не жирні. Успіх — м'який фіолетовий, помилка — рожево-червоний. Заголовок багаторядкової відповіді — фіолетовий жирний; рядки під ним з відступом у два пробіли, без префікса.
- Приклади:
  1. Заснування: `✦ Founded nation Aurelia.` — префікс фіолетовий, речення м'яко-фіолетове, назва блідо-бузкова. Звук levelup.
  2. Помилка кольору: `✦ Color must look like #336699 or 336699.` — текст рожево-червоний, звук bass.
  3. `/nation info`: заголовок `✦ Nation Aurelia` жирний фіолетовий; нижче `  Owner: …`, `  Color: ■ #FFFFFF` (квадратик кольору держави), `  Capital colony: …`, `  Colonies: …`, `  Treasury: …`. Без звуку.
  4. `/nation treasury`: заголовок `✦ Treasury`, рядки `  Nation: Aurelia` і `  Balance: 100 spurs`. `/nation list` і `/nation city list` у тому ж вигляді «назва: значення».
- Звуки лише гравцю, якому адресовано рядок: успіх — chime (0.4, 1.4), помилка — bass (0.4, 0.8), подія (заснування, місто додано вручну або саме) — levelup (0.3, 1.2). Консоль і командний блок звуку не мають.
- `chatSounds` лежить у COMMON, не в CLIENT: звук програє сервер через `playNotifySound`, тож клієнтський конфіг він не побачив би. За замовчуванням увімкнено. `./gradlew build` зелений.
- У грі: `/nation create`, невалідний `/nation color`, `/nation info` (квадратик біля кольору), `/nation list`, `/nation city list`, `/nation treasury`; потім `chatSounds = false` у `config/colony_nations-common.toml` і перевірити, що звуку немає.

### ТЕСТ ПРОЙДЕНО — етап 1 перевірено в грі (Rib, 10.10.2026), злито в main

---

## Етап 2 — територія і карта

Загальні правила етапу:
- Працюй у новій гілці від `main`.
- Одиниця території — чанк. Ключ — `ChunkRef(String dimension, int x, int z)` (чистий record, як `CityRef`).
- Уся геометрія й правила (суміжність, ціни, ліміти, вибір чанків на зняття) — у чистих класах `territory/` з JUnit-тестами.
- Числа балансу — у конфігу (COMMON, розділ `territory`), значення за замовчуванням вказані в задачах. Це тимчасові числа, Rib уточнить їх за специфікацією Nations.
- Державна земля і міська земля: міська = чанки клейму колоній MineColonies, що належать державі; державна = анексовані аддоном чанки. Чанк може бути або державною землею, або міською (міська має пріоритет), але не обома.

### T11. Модель території
Статус: DONE

- `ChunkRef`; `TerritoryMap` — чанк → id держави (тільки державна земля, міська рахується з колоній). Операції: claim, release, owner(chunk), chunks(nation), count(nation), `isAdjacentToNation(chunk, nation)` (4 сусіди, той самий вимір), `releaseAll(nation)`.
- `NearestCity`: для чанка знайти найближче місто держави (за відстанню в чанках від центру колонії; рівність — менший `CityRef` за id, потім вимір). Знадобиться для відділення міст і утримання.
- Збереження — у той самий `NationSavedData` (підніми версію формату до 3, з міграцією з версії 2: порожня територія). Правила R1-4 (unreadable) поширюються й на територію.
- Розпуск держави звільняє всю її державну землю.

Готово, коли: `build` зелений; тести суміжності, міграції 2→3, циклу кодека, `NearestCity`.

Звіт:
- Чисті класи в `territory/`: `ChunkRef`, `TerritoryMap`, `CityCenter`, `NearestCity`. Державна земля — чанк → id держави. Міська земля тут не зберігається.
- `claim` повторно для тієї самої держави нічого не робить; чужий чанк — `TerritoryException.ALREADY_CLAIMED`. `release` порожнього чанка — нічого. `isAdjacentToNation` дивиться лише 4 ортогональних сусіди в тому самому вимірі (діагональ і інший вимір не рахуються, сам чанк теж).
- `NearestCity`: шахова відстань у чанках `max(|dx|, |dz|)` від центру колонії, лише міста того самого виміру. За рівної відстані — менший id колонії, потім рядок виміру (той самий порядок, що й вибір столиці). Місто в іншому вимірі не стає найближчим, навіть якщо його координати ближчі.
- Формат сейву — версія 3: після держав дописано список чанків. Версія 2 читається як порожня територія і при наступному збереженні пишеться вже як версія 3. Невідома версія (зокрема 1) як і раніше лишає оригінальні байти й блокує зміни (R1-4). Розпуск держави (`/nation disband` і admin) викликає `releaseAll` перед видаленням держави.
- `./gradlew build` зелений. Тести: суміжність, `releaseAll`, цикл кодека, міграція 2→3, `NearestCity`.
- У грі: світ, збережений етапом 1, після оновлення має як і раніше показувати держави в `/nation list`; розпуск як і раніше звільняє назву й власника. Окремих команд території ще немає — це T13.

### T12. Міська земля і перша територія
Статус: DONE (залежить від T11)

- `compat/minecolonies`: метод «чанки клейму колонії» (див. `docs/notes/minecolonies-api.md`, пункт 7). Кешуй результат, онови при події росту клейму, якщо така є; інакше — перерахунок раз на хвилину для колоній держав.
- Коли місто додається до держави (автоматично чи командою), уся державна земля в межах клейму цього міста знімається з `TerritoryMap` (вона стає міською), а навколо клейму анексується безкоштовно кільце шириною `foundingRingChunks` (default 2) — лише вільні чанки, не чужі.
- Обмеження заснування міста (TODO з T8): гравець з державою може заснувати нове місто тільки якщо чанк ратуші — державна земля його держави, або якщо в держави ще немає жодного міста і чанк нічий. Чужа державна чи міська земля — відмова з повідомленням (стиль `Messages`). Перевірку роби в тому ж спільному методі, що й Mixin'и R1-6, щоб вікно заснування навіть не відкривалось.

Готово, коли: `build` зелений; у звіті — як визначаються чанки клейму і що перевірити в грі.

Звіт:
- Чанки клейму: `IColonyManager.getClaimData(вимір)`. Чанк належить колонії, якщо `getOwningColony()` дорівнює її id або id є в `getStaticClaimColonies()`. Окремої події росту клейму на шині MineColonies немає (є створення/видалення колонії, будівлі, жителі), тому `ColonyClaimCache` перечитує клейми колоній держав раз на 1200 тіків і ще раз, коли місто приєднується. Якщо в мапі клеймів колонії ще немає жодного чанка, для кільця береться чанк центру колонії.
- Коли місто приєднується (подія створення або `/nation city add`), державна земля всередині клейму знімається з `TerritoryMap`, а навколо клейму безкоштовно анексується кільце шириною `territory.foundingRingChunks` (COMMON, default 2). Кільце — шахова околиця, без самого клейму. Чанки чужої держави і чужої міської землі в кільці не чіпаються. Своя державна земля в кільці лишається своєю.
- Заснування: `NationColonyGate` (його кличуть обидва Mixin'и R1-6). Якщо місце не підходить, `onExecute` скасовується — вікно заснування не відкривається і вікно знесення теж. Можна: чанк — державна земля своєї держави, або в держави ще немає міст і чанк нічий. Не можна: чужа державна земля, міська земля (клейм колонії будь-якої держави), дика земля коли міста вже є. Повідомлення: `You cannot found a city on %s land.` / `Не можна заснувати місто на землі держави %s.`; `You cannot found a city on city land.` / `Не можна заснувати місто на міській землі.`; `New cities must be founded on your nation's land.` / `Нове місто можна заснувати лише на землі вашої держави.`
- Розбіжність із `docs/DESIGN.md`: там нове місто лише на державній землі. Задача дозволяє перше місто на нічийному чанку — зроблено так.
- `./gradlew build` зелений. Тести: `FoundingRulesTest`, `FoundingRingTest`.
- У грі: держава без міст — ратуша на дикій землі створює колонію, у `/nation city list` вона є, навколо клейму з'являється кільце державної землі (поки без карти, перевірити пізніше `/nation territory` у T13). Друга ратуша на дикій землі — відмова, вікно заснування не відкривається. Друга ратуша на кільці своєї держави — колонія створюється. Ратуша на чужій землі або в клеймі міста — відмова. Гравець без держави як і раніше не може заснувати другу колонію.

### T13. Анексія
Статус: DONE (залежить від T11, T12)

Команди (власник держави, у чанку, де стоїть гравець):
- `/nation annex` — анексувати поточний чанк. Умови: чанк нічий (ні державна, ні міська земля); суміжний з територією держави (державною або міською); в межах адміністративної межі; у скарбниці вистачає грошей.
- `/nation unannex` — відпустити поточний чанк своєї державної землі (гроші не повертаються). Не можна, якщо після цього якась ділянка державної землі втратить зв'язок з містами держави (перевірка зв'язності — чиста, з тестом).
- `/nation territory` — скільки державних і міських чанків, ліміт, щоденне утримання.

Формули (усе в конфігу):
- ціна = `annexBaseCost` (default 50 spur) + `annexCostPerChunkDistance` (default 10) × відстань у чанках до найближчого міста держави;
- адміністративна межа державних чанків = `adminBase` (default 30) + `adminPerCity` (default 20) × кількість міст + `adminPerTownHallLevel` (default 5) × сума рівнів ратуш.

Готово, коли: `build` зелений; тести формули ціни, межі, суміжності й зв'язності; у звіті — приклади повідомлень.

Звіт:
- Команди власника, у чанку де він стоїть: `/nation annex`, `/nation unannex`, `/nation territory`. Ціна `annexBaseCost + annexCostPerChunkDistance × шахова відстань до найближчого міста` (default 50 + 10 × відстань). Межа `adminBase + adminPerCity × міста + adminPerTownHallLevel × сума рівнів ратуш` (default 30 + 20 × міста + 5 × рівні). Усе в COMMON, розділ `territory`. Рівень ратуші — `getServerBuildingManager().getTownHall().getBuildingLevel()`.
- Анексія: чанк нічий (не державна і не міська земля), ортогонально торкається своєї державної або міської землі, після анексії кількість державних чанків не вища за межу, у скарбниці вистачає spur. Сума списується. Відстань і найближче місто — `NearestCity` (лише цей вимір).
- Відпуск не повертає гроші. Відмова, якщо після зняття якийсь державний чанк не дійде до міста кроками через державну або міську землю (діагональ і інший вимір не рахуються).
- `/nation territory` показує державні чанки, міські (об'єднання клеймів), межу і щоденне утримання `upkeepPerChunk × державні чанки` (default 2). Саме списання на світанку — T14; ставку додано зараз, бо команда має її показати.
- `./gradlew build` зелений. Тести: `AnnexRulesTest` (ціна 50 + 10×4 = 90; межа 30 + 20×2 + 5×3 = 85; суміжність; місток до міста).
- Приклади (лавандовий текст, значення блідо-бузкові, успіх зі звуком chime, помилка — bass):
  - `✦ Annexed chunk 12, -3 in minecraft:overworld for 70 spurs. Treasury: 430.` / `✦ Анексовано чанк 12, -3 у minecraft:overworld за 70 spur. Скарбниця: 430.`
  - `✦ You can annex only a chunk next to your nation.` / `✦ Анексувати можна лише чанк поруч із землею вашої держави.`
  - `✦ Rome needs 90 spurs to annex this chunk and has 40.` / `✦ Щоб анексувати цей чанк, державі Rome потрібно 90 spur, а в скарбниці 40.`
  - `✦ Releasing this chunk would cut state land off from your cities.` / `✦ Якщо відпустити цей чанк, частина державної землі втратить зв'язок із містами.`
  - `/nation territory`: заголовок `✦ Territory` / `✦ Територія`, нижче рядки державних чанків, міських, межі й утримання.
- У грі: стати на кільце біля міста, `/nation treasury deposit`, `/nation annex`, перевірити баланс і `/nation territory`; чанк через один — відмова; відпустити місток між містом і дальнім чанком — відмова; крайній чанк — відпускається без повернення грошей.

### T14. Утримання території
Статус: DONE (залежить від T13)

- Раз на ігровий день (світанок overworld) кожна держава платить `upkeepPerChunk` (default 2 spur) × кількість державних чанків (міська земля безкоштовна).
- Якщо грошей не вистачає: списати все, що є, і звільнити `upkeepReleasePerDay` (default 4) державних чанків, найдальших від міст, не порушуючи зв'язності решти. Власнику — повідомлення (або при вході в гру, якщо був офлайн: зберігай непрочитані сповіщення).
- Логіка вибору чанків на зняття — чиста, з тестом.

Готово, коли: `build` зелений; тести списання і вибору чанків; у звіті — як перевірити в грі (можна командою оператора прискорити: `/nation admin upkeep run`).

Звіт:
- На світанку overworld (день = `getDayTime() / 24000`) кожна держава платить `upkeepPerChunk` (default 2) × кількість державних чанків. Міська земля безкоштовна. Якщо днів пропущено кілька, за тік закривається один, тож сон і `/time add` не звалюють усе в один тік. Перший тік після створення сейву лише запам'ятовує поточний день і не списує.
- Якщо скарбниці не вистачає: списується все, що є (навіть 0), і відпускається до `upkeepReleasePerDay` (default 4) державних чанків. Спочатку найдальші від найближчого міста (шахова відстань, той самий вимір). За рівної відстані — більший x, потім більший z, потім вимір з кінця алфавіту. Чанк, чиє зняття відріже решту від міст, пропускається. Логіка в чистому `Upkeep`.
- Власнику онлайн — тихе повідомлення про повну сплату і повідомлення-подія, якщо землю відпущено. Якщо офлайн — рядок лежить у тому самому `NationSavedData` (поруч із payload, не в версії кодека) і віддається при вході.
- Оператор: `/nation admin upkeep run` (permission level 2) робить одне списання зараз і не зсуває день світанку. Нечитабельний сейв блокує і це.
- `./gradlew build` зелений. Тести `UpkeepTest`: 100 spur і 2 чанки → плата 4, нічого не відпущено; 5 spur і 3 чанки → плата 5 і два найдальші; порожня скарбниця відпускає дальній кінець, місток лишається, поки дальній чанк не знято; рівна відстань — спочатку більший x.
- У грі: анексувати кілька чанків, покласти в скарбницю мало spur, `/nation admin upkeep run` оператором. Онлайн-власник бачить рядок; офлайн — той самий рядок після входу. `/nation territory` після цього має менший баланс і менше державних чанків, якщо грошей не вистачило. Повна сума в скарбниці — чанки лишаються. Далі можна дочекатись світанку і переконатись, що списання саме.

### T15. Захист державної землі
Статус: DONE (залежить від T11)

- На державній землі чужі гравці не можуть ламати й ставити блоки, виливати/набирати рідини, підпалювати, використовувати контейнери; ходити, їздити й користуватися дверима/кнопками можуть. Власник держави й оператори — без обмежень. Міська земля — як і раніше, захищає MineColonies.
- Вибухи (кріпери, TNT) на державній землі не ламають блоки (сутності шкоди зазнають).
- Повідомлення про відмову — не частіше ніж раз на 3 с на гравця, стиль `Messages` без звуку.
- Залиш `// TODO(T-war)` у місці, де під час війни захист буде зніматись.

Готово, коли: `build` зелений; у звіті — список подій NeoForge, на які підписано, і чек-лист для тесту вдвох (Rib + друг).

Звіт:
- Захист лише державної землі (`TerritoryMap`). Міську як і раніше тримає MineColonies. Власник держави й оператор (permission level 2) без обмежень. Інші гравці не ламають і не ставлять блоки, не виливають і не набирають відро, не підпалюють кременем чи вогняним зарядом і не відкривають контейнери (блок або block entity з `MenuProvider`). Двері, кнопки, важелі, хвіртки й ходьба не чіпаються — для них подія використання не скасовується.
- Вибух не скасовується: з `ExplosionEvent.Detonate` прибираються лише блоки в державних чанках, сутності в списку лишаються.
- Відмова — стиль помилки `Messages`, без звуку, не частіше ніж раз на 60 тіків (3 с) на гравця: `This land belongs to Rome.` / `Ця земля належить державі Rome.`
- `// TODO(T-war)` стоїть у `StateLandGuard.deniedBy`, де під час війни захист для ворога зніметься.
- Події NeoForge: `BlockEvent.BreakEvent`, `BlockEvent.EntityPlaceEvent`, `PlayerInteractEvent.RightClickBlock`, `ExplosionEvent.Detonate`.
- `./gradlew build` зелений.
- У грі вдвох: друг на вашій державній землі не ламає блок, не ставить блок, не виливає воду, не відкриває скриню, не підпалює; двері й кнопка працюють; вибух крипера/TNT не ламає блоки, але б'є гравця; ви самі й оператор усе можете; на міській землі правила MineColonies як раніше; повідомлення не сиплеться частіше ніж раз на 3 с і без звуку.

### T16. Синхронізація території з клієнтом
Статус: DONE (залежить від T11, T12)

- Payload'и NeoForge: при вході гравця — повний знімок (держави: id, назва, колір; чанки державної і міської землі з власником); далі — лише зміни (claim/release, зміна кольору/назви, нове/видалене місто).
- Клієнтський кеш `client/TerritoryClientCache`; без JourneyMap він просто лежить у пам'яті.
- Не слати чанки інших вимірів, ніж той, у якому гравець (при зміні виміру — знімок цього виміру).

Готово, коли: `build` зелений; `runServer` стартує; у звіті — формат payload'ів і орієнтовний розмір знімка для 1000 чанків.

Звіт:
- Два play-to-client payload'и, реєстрація в `TerritorySync.register` зі слухача мод-шини (`ColonyNations`): `colony_nations:territory_snapshot` і `colony_nations:territory_delta`. Обробники кладуть роботу в клієнтський потік і пишуть у `TerritoryClientCache`.
- Знімок: список `NationStamp` (UUID, utf-назва, int колір) і список `ChunkPlot` лише поточного виміру гравця. Міські чанки йдуть першими; державний чанк, який уже є міським, не дублюється (місто перемагає). Надіслання: вхід гравця і зміна виміру (`event.getTo()`). Знімок замінює кеш і ставить вимір з рівня гравця.
- Дельта: список `NationStamp`, список знятих UUID (свій codec: varint + UUID), список `ChunkPlot`. Створення, перейменування, колір і розпуск держави йдуть усім гравцям. Зміни чанків (анексія, зняття, кільце заснування, утримання, нове чи зняте місто) — лише гравцям цього виміру. Розпуск на клієнті знімає всі ділянки цієї держави, без окремого release на кожен чанк. Зняття міста не стирає чанк, який ще тримає інше місто тієї ж держави.
- `ChunkPlot`: varint x, varint z, UUID (16 байт), boolean city, boolean release. Release несе UUID(0, 0). Для типових координат varint — 1–2 байти, тож ділянка ≈ 20–22 байти. Знімок на 1000 чанків ≈ 20–22 КБ плюс заголовки держав (UUID + utf-назва + 4 байти кольору).
- Ріст клеймів MineColonies між входами окремим пакетом не шлеться: наступний вхід або зміна виміру дає свіжий знімок. Кеш — звичайний клас, на виділеному сервері безпечний; JourneyMap не потрібен. `TerritoryClientEvents` (лише клієнт) чистить кеш при виході.
- `./gradlew build` зелений. `runServer` дійшов до `Done`; у логу лишилось старе попередження MineColonies про відсутню таблицю луту `minecolonies:entities/cavalry_horse` (ще з T3), сервер після нього працює.
- У грі: після входу кеш має держави і чанки виміру (карти ще немає — це T17); `/nation annex` і `unannex` міняють ділянку в гравців цього виміру; rename і color доходять до всіх; перехід у інший вимір замінює знімок; другий гравець в іншому вимірі чанки першого не отримує, доки сам туди не зайде.

### T17. Політична карта в JourneyMap
Статус: DONE (залежить від T16)

- JourneyMap API як `compileOnly` (координати — з репозиторію/maven TeamJM для 1.21.1 NeoForge, API v2.x); runtime для `runClient` — сам JourneyMap (`localRuntime`). Плагін-клас завантажується лише якщо JourneyMap присутній.
- Шари: державна земля — заливка кольором держави (прозорість ~25%) і контур; міська земля — та сама заливка, але насиченіша (~40%) і товщий контур; сусідні чанки однієї держави зливаються в полігони (злиття — чистий клас з тестом: набір чанків → контури, з дірками).
- Підказка при наведенні: назва держави, тип землі (державна / місто «Назва»).
- Перемальовувати лише змінені держави, не всю карту.

Готово, коли: `build` зелений; у звіті — скріншот неможливий, тож опиши, що має бути видно на мінімапі й повноекранній карті, і чек-лист для тесту.

Звіт:
- JourneyMap API `info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1` як `compileOnly` з maven `https://maven.blamejared.com` (поточний публічний maven TeamJM). Сам мод для гри в dev — `localRuntime` `maven.modrinth:journeymap:1.21.1-6.0.9+neoforge`. У `gradle.properties` згадка про 6.0.2 лишилась як нижня межа NeoForge; для запуску клієнта взято свіжий реліз 1.21.1 тієї ж гілки API 2.0. У `neoforge.mods.toml` залежність `journeymap` необов'язкова, лише клієнт. Класи API в jar мода не пакуються.
- Плагін `ColonyNationsMapPlugin` (`@JourneyMapPlugin`, api 2.0.0) реалізує `IClientPlugin`. `JourneyMapGate` викликає `Class.forName` лише з `FMLClientSetupEvent` і лише якщо `ModList` бачить `journeymap`. Прямого посилання на клас плагіна немає (лише рядок), тож виділений сервер і клієнт без JourneyMap ці типи не вантажать. Конструює плагін сам JourneyMap.
- Заливка кольором держави: державна земля 25% і контур шириною 2; міська — 40% і контур шириною 4, малюється поверх. Видно на мінімапі й повноекранній карті. Сусідні чанки (лише ортогонально) однієї держави й одного типу зливаються в полігон. Діагональ лишається двома фігурами. Дірка в кільці чанків — окремий внутрішній контур. Чистий клас `ChunkOutlines`, тест `ChunkOutlinesTest` (квадрат, прямокутник із двох чанків, діагональ, дірка 3×3).
- Підказка при наведенні: назва держави і тип землі. Ключі `map.colony_nations.land.state` / `land.city`: `State land` / `City "%s"`, `Державна земля` / `Місто «%s»`. Напис на полігоні — той самий тип землі.
- Щоб у підказці була назва колонії, у `ChunkPlot` додано utf-поле `label` (порожнє для державної землі і для release). У звіті T16 його ще не було: знімок T16 ніс лише власника чанка. Це розбіжність із тим описом формату, не з `DESIGN.md`.
- Дельта перемальовує лише зачеплені держави (анексія, зняття, колір, назва, місто). Повний знімок, зміна виміру, вихід і старт мапи JourneyMap збирають усі полігони з кешу наново. Ріст клеймів MineColonies, як у T16, на карту сам не приїжджає: він з'явиться після повторного входу або зміни виміру.
- `DESIGN.md` ще описує штрихування окупації, прапор і підсвітку котлів. Ця задача просить лише заливку й контур державної та міської землі, тож решту шарів не робив.
- `Context.UI` в API 2.0.0-1.21.1 позначений застарілим наперед (переїзд у 26.2). Іншого класу в цьому jar немає, тож мінімапа й повна карта вмикаються через нього.
- `./gradlew build` зелений, 62 тести.
- У грі без JourneyMap: світ відкривається, карта мода відсутня. З JourneyMap на мінімапі й на повній карті (клавіша карти): державні чанки — прозора заливка кольору держави й тонкий контур, сусідні злиті в одну фігуру; місто — густіша заливка й товщий контур, у підказці назва держави і `Місто «Назва»`; дірка або чужий чанк усередині не залитий цією фігурою; `/nation annex` змінює лише цю державу; `/nation color` змінює заливку; в іншому вимірі цих полігонів немає, доки гравець туди не зайде; вихід у меню прибирає шар, повторний вхід малює знову.

### ЧЕКАЄ ТЕСТУ — Rib перевіряє етап 2 у грі
Зупинись тут. Claude допише нові задачі після тесту.
