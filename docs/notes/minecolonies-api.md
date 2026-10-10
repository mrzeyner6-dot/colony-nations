# MineColonies API (1.21.1)

Джерело: sources jar `com.ldtteam:minecolonies:1.1.1403-1.21.1` з LDT maven (`minecolonies-1.1.1403-1.21.1-sources.jar`, зібраний 2026-10-04). Це власна шина MineColonies, не NeoForge events, якщо не сказано інакше.

Точка входу: `IMinecoloniesAPI.getInstance()` (`com.minecolonies.api.IMinecoloniesAPI`).

## 1. Колонія за id і всі колонії

`IColonyManager.getInstance()` делегує в `IMinecoloniesAPI.getInstance().getColonyManager()`.

```java
IColonyManager manager = IColonyManager.getInstance();

IColony byId = manager.getColonyByDimension(colonyId, dimension);
IColony byIdInWorld = manager.getColonyByWorld(colonyId, level);
IColony atPos = manager.getColonyByPosFromWorld(level, blockPos);

List<IColony> inWorld = manager.getColonies(level);
List<IColony> everywhere = manager.getAllColonies();
```

- `getColonyByDimension(int id, ResourceKey<Level> dimension)` і `getColonyByWorld(int id, Level world)` — колонія за числовим id. Коментар у інтерфейсі каже «UUID», але параметр — `int`.
- `getColonies(Level)` — колонії одного світу. `getAllColonies()` — усі виміри.
- `getIColonyByOwner(Level, Player)` / `getIColonyByOwner(Level, UUID)` — перша колонія, де цей гравець є власником. На сервері пошук іде по `getAllColonies()`, не лише по переданому світу (`ColonyManager.getColonyByOwner`).

## 2. Власник, ранги, дозволи

Власник живе в `IColony.getPermissions()` (`IPermissions`).

```java
IPermissions permissions = colony.getPermissions();
UUID ownerId = permissions.getOwner();
String ownerName = permissions.getOwnerName();
boolean changed = permissions.setOwner(player);

Rank rank = permissions.getRank(player);
boolean canBuild = permissions.hasPermission(player, Action.PLACE_BLOCKS);
permissions.setPlayerRank(playerId, permissions.getRankOfficer(), level);
permissions.addPlayer(playerId, name, permissions.getRankFriend());
```

- `setOwner(Player)` повертає `boolean`. `setOwnerAbandoned()` знімає власника.
- Готові ранги: `getRankOwner()`, `getRankOfficer()`, `getRankFriend()`, `getRankNeutral()`, `getRankHostile()`. Повний набір — `getRanks()` (`Map<Integer, Rank>`).
- `Rank` — клас, не enum (`com.minecolonies.api.colony.permissions.Rank`): `id`, `name`, `isInitial`, `isColonyManager`, `isHostile`, бітова маска `permissionData`. Старий `OldRank` лишився як enum для сумісності.
- Дії — enum `Action` (`PLACE_BLOCKS`, `BREAK_BLOCKS`, `MANAGE_HUTS`, `EDIT_PERMISSIONS`, …). `hasPermission(Rank, Action)` і `hasPermission(Player, Action)`. Зміна маски: `setPermission` / `alterPermission`. Свої ранги: `addRank(String)`, `removeRank(Rank)`.
- Зміна рангу гравця також поститься як `ColonyPlayerRankChangedModEvent`.

## 3. Події: створення, видалення, рівень ратуші, новий житель

Окремих NeoForge-подій на ці чотири випадки немає. Шукав у `com.minecolonies.api.eventbus.events`: це власна шина `com.minecolonies.api.eventbus.EventBus`.

```java
EventBus bus = IMinecoloniesAPI.getInstance().getEventBus();
bus.subscribe(ColonyCreatedModEvent.class, event -> {
    IColony colony = event.getColony();
});
bus.subscribe(ColonyDeletedModEvent.class, event -> { /* event.getColony() */ });
bus.subscribe(CitizenAddedModEvent.class, event -> {
    ICitizenData citizen = event.getCitizen();
    CitizenAddedModEvent.CitizenAddedSource source = event.getSource();
});
bus.subscribe(BuildingConstructionModEvent.class, event -> {
    IBuilding building = event.getBuilding();
    WorkOrderBuilding order = event.getWorkOrder();
});
```

- `ColonyCreatedModEvent` поститься з `CreateColonyMessage.onExecute` після `createColony`.
- `ColonyDeletedModEvent` — клас є; поститься при видаленні колонії (подія шини, не NeoForge).
- Новий житель: `CitizenAddedModEvent`. Джерело `CitizenAddedSource`: `INITIAL`, `BORN`, `HIRED`, `RESURRECTED`, `COMMANDS`. Поруч є `CitizenRemovedModEvent`, `CitizenDiedModEvent`, `CitizenJobChangedModEvent`.
- Окремої події «ратуша виросла на рівень» немає. Завершення наряду (BUILD, UPGRADE, REPAIR, REMOVE) поститься як `BuildingConstructionModEvent` з `AbstractEntityAIStructureWithWorkOrder` (після завершення work order). Ратуша — `building instanceof ITownHall`. Тип наряду — `event.getWorkOrder().getWorkOrderType()` (`WorkOrderType.UPGRADE`). Поточний рівень — `IBuilding.getBuildingLevel()` (`ICommonBuilding`); цільовий — `IWorkOrder.getTargetLevel()` / `getCurrentLevel()`. Ратушу колонії дає `colony.getCommonBuildingManager().getTownHall()` / `hasTownHall()`.

## 4. Кілька колоній на гравця

Ключ конфігу є, але він не знімає серверну заборону.

- `ServerConfiguration.allowInfiniteColonies`, ключ `allowinfinitecolonies`, default `false` (`minecolonies-server.toml`).
- Єдине використання прапорця: клієнтське вікно `WindowTownHallDeleteAbandonColony` показує кнопку abandon, якщо прапорець увімкнений. На створення колонії він не впливає.
- Серверна перевірка — `CreateColonyMessage.onExecute`:

```java
IColony owned = IColonyManager.getInstance().getIColonyByOwner(world, sender);
if (owned == null) {
    IColony created = IColonyManager.getInstance().createColony(world, townHall, sender, colonyName, pack);
    // ...
} else {
    MessageUtils.format(WARNING_COLONY_FOUNDING_FAILED).sendTo(sender);
}
```

`getIColonyByOwner` на сервері дивиться всі колонії всіх вимірів і повертає першу, де `permissions.getOwner()` збігається з UUID гравця. Друга колонія тому не створюється, навіть з `allowinfinitecolonies=true`. Обхід — Mixin на `CreateColonyMessage.onExecute` (і, якщо треба клієнтський UX, на вікно ратуші).

## 5. Щастя

Окремого setter немає. Колонія читає середнє:

```java
double colonyHappiness = colony.getOverallHappiness();
```

Реалізація в `Colony.getOverallHappiness`: якщо жителів 0 — повертає `5.5`, інакше середнє `citizen.getCitizenHappinessHandler().getHappiness(colony, citizen)`.

Житель:

```java
ICitizenHappinessHandler handler = citizen.getCitizenHappinessHandler();
double value = handler.getHappiness(colony, citizen);
handler.addModifier(new StaticHappinessModifier(id, weight, new StaticHappinessSupplier(factor)));
handler.resetModifier(modifierId); // лише ITimeBasedHappinessModifier.reset()
```

`IHappinessModifier.getFactor` — множник (нижче 1 негативний, вище 1 позитивний), не абсолютне щастя. `StaticHappinessSupplier` зберігає `double`, публічного setter немає: зміна йде через новий модифікатор (`addModifier` замінює запис з тим самим id) або NBT (`deserializeNBT`).

## 6. Жителі: стать, вік, робота, охоронець

```java
List<ICitizenData> citizens = colony.getCitizenManager().getCitizens();
for (ICitizenData citizen : citizens) {
    int id = citizen.getId();
    boolean female = citizen.isFemale();          // ICitizen: true = жінка, false = чоловік
    boolean child = citizen.isChild();            // дитина / дорослий, числового віку немає
    IJob<?> job = citizen.getJob();               // null — без роботи
    boolean guard = job != null && job.isGuard();
    IBuilding work = citizen.getWorkBuilding();
}
```

- Статі-enum немає, лише `ICitizen.isFemale()`.
- Числового віку в `ICitizen` / `ICitizenData` немає. Дитина — `isChild()` / `setIsChild(boolean)`.
- Робота: `getJob()` / `setJob(IJob)`. Ідентифікатор — `job.getJobRegistryEntry()` (`ModJobs`, наприклад `ModJobs.knight`).
- Охоронець: `IJob.isGuard()` за замовчуванням `false`. `true` лише в `AbstractJobGuard`. Підкласи: `JobKnight`, `JobRanger`, `JobDruid`, `JobCavalry`.

## 7. Межі клейму (чанки)

Повний набір клеймів виміру — не `IColony.getLoadedChunks()` (це запаковані `long` завантажених чанків, `ChunkPos.asLong`).

```java
Map<ChunkPos, IChunkClaimData> claims =
    IColonyManager.getInstance().getClaimData(colony.getDimension());
int id = colony.getID();
List<ChunkPos> owned = new ArrayList<>();
for (Map.Entry<ChunkPos, IChunkClaimData> entry : claims.entrySet()) {
    IChunkClaimData data = entry.getValue();
    if (data.getOwningColony() == id || data.getStaticClaimColonies().contains(id)) {
        owned.add(entry.getKey());
    }
}
```

- `IChunkClaimData.getOwningColony()` — id колонії-власника чанка.
- `getStaticClaimColonies()` — статичні клейми (нединамічні).
- `getAllClaimingBuildings()` — `Map<colonyId, Set<BlockPos>>` будівель, що тримають клейм.
- Точковий запит: `getClaimData(dimension, chunkPos)`.
- Радіус клейму будівлі: `IBuilding.getClaimRadius(buildingLevel)`.
