package com.mrzeyner.colonynations.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.NationCodec;
import com.mrzeyner.colonynations.nation.NationRegistry;
import com.mrzeyner.colonynations.territory.TerritoryMap;
import com.mrzeyner.colonynations.trade.TradePolicy;
import com.mrzeyner.colonynations.trade.TradeSales;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Overworld saved data for {@link NationRegistry} and state land.
 * Both are stored as the versioned bytes from {@link NationCodec}.
 * An unreadable payload is kept as-is so a save does not erase it.
 */
public final class NationSavedData extends SavedData {
    public static final String DATA_ID = "colony_nations";
    private static final String PAYLOAD = "Payload";
    private static final String NOTICES = "Notices";
    private static final String LAST_UPKEEP_DAY = "LastUpkeepDay";
    private static final String FREE_CITIES = "FreeCities";
    private static final String OFFERS = "Offers";
    private static final String MENU_HINTS = "MenuHints";
    private static final String FREE_JOIN_DAYS = "FreeJoinDays";
    private static final String TRADE_POLICIES = "TradePolicies";
    private static final String TRADE_SALES = "TradeSales";

    private final NationRegistry registry;
    private final TerritoryMap territory;
    private final List<PendingNotice> notices = new ArrayList<>();
    private final List<FreeCity> freeCities = new ArrayList<>();
    private final List<SecessionOffer> offers = new ArrayList<>();
    private final Set<UUID> menuHints = new HashSet<>();
    private final Map<CityRef, Long> freeJoinDays = new HashMap<>();
    private final Map<UUID, TradePolicy> tradePolicies = new HashMap<>();
    private final Map<UUID, TradeSales.Book> tradeSales = new HashMap<>();
    private final byte[] preservedPayload;
    private final int unreadVersion;
    private boolean legacyGraceApplied;
    private Long lastUpkeepDay;

    public NationSavedData() {
        this(new NationRegistry(), new TerritoryMap(), null, 0);
    }

    public NationSavedData(NationRegistry registry) {
        this(registry, new TerritoryMap(), null, 0);
    }

    private NationSavedData(NationRegistry registry, TerritoryMap territory, byte[] preservedPayload, int unreadVersion) {
        this.registry = registry;
        this.territory = territory == null ? new TerritoryMap() : territory;
        this.preservedPayload = preservedPayload == null ? null : preservedPayload.clone();
        this.unreadVersion = unreadVersion;
    }

    public NationRegistry registry() {
        return registry;
    }

    public TerritoryMap territory() {
        return territory;
    }

    public void addNotice(PendingNotice notice) {
        notices.add(notice);
        setDirty();
    }

    public List<PendingNotice> notices() {
        return List.copyOf(notices);
    }

    public List<FreeCity> freeCities() {
        return List.copyOf(freeCities);
    }

    public void addFreeCity(FreeCity city) {
        freeCities.removeIf(existing -> existing.city().equals(city.city()));
        freeCities.add(city);
        setDirty();
    }

    public void removeFreeCity(com.mrzeyner.colonynations.nation.CityRef city) {
        if (freeCities.removeIf(existing -> existing.city().equals(city))) {
            setDirty();
        }
    }

    public List<SecessionOffer> offers() {
        return List.copyOf(offers);
    }

    /**
     * @return true the first time this player should be told about the nation menu
     */
    public boolean claimMenuHint(UUID player) {
        if (player == null || !menuHints.add(player)) {
            return false;
        }
        setDirty();
        return true;
    }

    /**
     * Game day a free city was last bought, or null when it has never been bought.
     * Kept after the city joins a nation so a quick secession cannot be bought again immediately.
     */
    public Long freeJoinDay(CityRef city) {
        return city == null ? null : freeJoinDays.get(city);
    }

    public void setFreeJoinDay(CityRef city, long day) {
        if (city == null) {
            return;
        }
        freeJoinDays.put(city, day);
        setDirty();
    }

    public void forgetFreeJoin(CityRef city) {
        if (city != null && freeJoinDays.remove(city) != null) {
            setDirty();
        }
    }

    public void addOffer(SecessionOffer offer) {
        offers.removeIf(existing -> existing.city().equals(offer.city()));
        offers.add(offer);
        setDirty();
    }

    public SecessionOffer takeOffer(com.mrzeyner.colonynations.nation.CityRef city) {
        SecessionOffer found = null;
        for (SecessionOffer offer : offers) {
            if (offer.city().equals(city)) {
                found = offer;
                break;
            }
        }
        if (found != null) {
            offers.remove(found);
            setDirty();
        }
        return found;
    }

    /**
     * Drops any notice with the same owner and translation key, then appends {@code notice}.
     * Offline tax income uses this so several days stay one line.
     */
    public void replaceNotice(PendingNotice notice) {
        notices.removeIf(existing -> existing.owner().equals(notice.owner()) && existing.key().equals(notice.key()));
        notices.add(notice);
        setDirty();
    }

    public List<PendingNotice> takeNotices(UUID owner) {
        List<PendingNotice> mine = new ArrayList<>();
        notices.removeIf(notice -> {
            if (!notice.owner().equals(owner)) {
                return false;
            }
            mine.add(notice);
            return true;
        });
        if (!mine.isEmpty()) {
            setDirty();
        }
        return mine;
    }

    public OptionalLong lastUpkeepDay() {
        return lastUpkeepDay == null ? OptionalLong.empty() : OptionalLong.of(lastUpkeepDay);
    }

    public void setLastUpkeepDay(long day) {
        lastUpkeepDay = day;
        setDirty();
    }

    public boolean unreadable() {
        return preservedPayload != null;
    }

    public int unreadVersion() {
        return unreadVersion;
    }

    public void grantGrace(UUID nationId, CityRef city, long day) {
        if (registry.grantGrace(nationId, city, day)) {
            setDirty();
        }
    }

    /**
     * Saves from before grace days stamp every current city with {@code today}. Runs once per load.
     */
    public void applyLegacyGrace(long today) {
        if (legacyGraceApplied || unreadable() || unreadVersion >= NationCodec.VERSION) {
            legacyGraceApplied = true;
            return;
        }
        legacyGraceApplied = true;
        if (registry.grantMissingGrace(today)) {
            setDirty();
        }
    }

    /**
     * @return true when nation changes must not be applied; the source has already been told why
     */
    public boolean refuseChanges(CommandSourceStack source) {
        if (!unreadable()) {
            return false;
        }
        Messages.error(source, "command.colony_nations.save.unreadable", unreadVersion);
        return true;
    }

    public static final SavedData.Factory<NationSavedData> FACTORY =
            new SavedData.Factory<>(NationSavedData::new, NationSavedData::load, null);

    public static NationSavedData get(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    public static NationSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        byte[] payload = tag.getByteArray(PAYLOAD);
        NationCodec.Loaded loaded = NationCodec.load(payload);
        NationSavedData data;
        if (loaded.unreadable()) {
            ColonyNations.LOGGER.error(loaded.warning());
            data = new NationSavedData(loaded.registry(), loaded.territory(), loaded.preserved(), loaded.version());
        } else {
            data = new NationSavedData(loaded.registry(), loaded.territory(), null, loaded.version());
        }
        data.notices.addAll(readNotices(tag));
        data.freeCities.addAll(readFreeCities(tag));
        data.offers.addAll(readOffers(tag));
        data.menuHints.addAll(readMenuHints(tag));
        data.freeJoinDays.putAll(readFreeJoinDays(tag));
        data.tradePolicies.putAll(readTradePolicies(tag));
        data.tradeSales.putAll(readTradeSales(tag));
        if (tag.contains(LAST_UPKEEP_DAY)) {
            data.lastUpkeepDay = tag.getLong(LAST_UPKEEP_DAY);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        byte[] payload = preservedPayload != null ? preservedPayload : NationCodec.write(registry, territory);
        tag.putByteArray(PAYLOAD, payload.clone());
        tag.put(NOTICES, writeNotices());
        tag.put(FREE_CITIES, writeFreeCities());
        tag.put(OFFERS, writeOffers());
        tag.put(MENU_HINTS, writeMenuHints());
        tag.put(FREE_JOIN_DAYS, writeFreeJoinDays());
        tag.put(TRADE_POLICIES, writeTradePolicies());
        tag.put(TRADE_SALES, writeTradeSales());
        if (lastUpkeepDay != null) {
            tag.putLong(LAST_UPKEEP_DAY, lastUpkeepDay);
        }
        return tag;
    }

    private ListTag writeNotices() {
        ListTag list = new ListTag();
        for (PendingNotice notice : notices) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Owner", notice.owner().toString());
            tag.putBoolean("Alarm", notice.alarm());
            tag.putString("Key", notice.key());
            ListTag args = new ListTag();
            for (String arg : notice.args()) {
                args.add(StringTag.valueOf(arg));
            }
            tag.put("Args", args);
            list.add(tag);
        }
        return list;
    }

    private static List<PendingNotice> readNotices(CompoundTag tag) {
        List<PendingNotice> notices = new ArrayList<>();
        ListTag list = tag.getList(NOTICES, Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag notice)) {
                continue;
            }
            List<String> args = new ArrayList<>();
            ListTag stored = notice.getList("Args", Tag.TAG_STRING);
            for (Tag arg : stored) {
                args.add(arg.getAsString());
            }
            notices.add(new PendingNotice(
                    UUID.fromString(notice.getString("Owner")),
                    notice.getBoolean("Alarm"),
                    notice.getString("Key"),
                    args));
        }
        return notices;
    }

    private ListTag writeFreeCities() {
        ListTag list = new ListTag();
        for (FreeCity city : freeCities) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", city.city().dimension());
            tag.putInt("ColonyId", city.city().colonyId());
            tag.putString("Name", city.name());
            tag.putInt("Color", city.color());
            if (city.formerNation() != null) {
                tag.putString("Former", city.formerNation().toString());
            }
            list.add(tag);
        }
        return list;
    }

    private static List<FreeCity> readFreeCities(CompoundTag tag) {
        List<FreeCity> cities = new ArrayList<>();
        ListTag list = tag.getList(FREE_CITIES, Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag city)) {
                continue;
            }
            UUID former = null;
            if (city.contains("Former") && !city.getString("Former").isBlank()) {
                try {
                    former = UUID.fromString(city.getString("Former"));
                } catch (IllegalArgumentException ignored) {
                    former = null;
                }
            }
            cities.add(new FreeCity(
                    new CityRef(city.getString("Dimension"), city.getInt("ColonyId")),
                    city.getString("Name"),
                    city.getInt("Color"),
                    former));
        }
        return cities;
    }

    private ListTag writeOffers() {
        ListTag list = new ListTag();
        for (SecessionOffer offer : offers) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", offer.city().dimension());
            tag.putInt("ColonyId", offer.city().colonyId());
            tag.putString("From", offer.fromNation().toString());
            tag.putString("To", offer.toNation().toString());
            tag.putString("Name", offer.cityName());
            tag.putLong("Day", offer.offerDay());
            list.add(tag);
        }
        return list;
    }

    private ListTag writeFreeJoinDays() {
        ListTag list = new ListTag();
        for (Map.Entry<CityRef, Long> entry : freeJoinDays.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", entry.getKey().dimension());
            tag.putInt("ColonyId", entry.getKey().colonyId());
            tag.putLong("Day", entry.getValue());
            list.add(tag);
        }
        return list;
    }

    private static Map<CityRef, Long> readFreeJoinDays(CompoundTag tag) {
        Map<CityRef, Long> days = new HashMap<>();
        ListTag list = tag.getList(FREE_JOIN_DAYS, Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag day)) {
                continue;
            }
            days.put(new CityRef(day.getString("Dimension"), day.getInt("ColonyId")), day.getLong("Day"));
        }
        return days;
    }

    private ListTag writeMenuHints() {
        ListTag list = new ListTag();
        for (UUID player : menuHints) {
            list.add(StringTag.valueOf(player.toString()));
        }
        return list;
    }

    private static Set<UUID> readMenuHints(CompoundTag tag) {
        Set<UUID> hints = new HashSet<>();
        ListTag list = tag.getList(MENU_HINTS, Tag.TAG_STRING);
        for (Tag entry : list) {
            try {
                hints.add(UUID.fromString(entry.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // Skip a damaged id. The player will see the hint once.
            }
        }
        return hints;
    }

    private static List<SecessionOffer> readOffers(CompoundTag tag) {
        List<SecessionOffer> offers = new ArrayList<>();
        ListTag list = tag.getList(OFFERS, Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag offer)) {
                continue;
            }
            offers.add(new SecessionOffer(
                    new com.mrzeyner.colonynations.nation.CityRef(offer.getString("Dimension"), offer.getInt("ColonyId")),
                    UUID.fromString(offer.getString("From")),
                    UUID.fromString(offer.getString("To")),
                    offer.getString("Name"),
                    offer.getLong("Day")));
        }
        return offers;
    }

    public TradePolicy tradePolicy(UUID nationId) {
        if (nationId == null) {
            return TradePolicy.empty();
        }
        return tradePolicies.getOrDefault(nationId, TradePolicy.empty());
    }

    public void setTradePolicy(UUID nationId, TradePolicy policy) {
        if (nationId == null) {
            return;
        }
        if (policy == null || policy.isEmpty()) {
            tradePolicies.remove(nationId);
        } else {
            tradePolicies.put(nationId, policy);
        }
        setDirty();
    }

    public void recordTrade(UUID nationId, long today, int packs, long spurs) {
        if (nationId == null || (packs <= 0 && spurs <= 0L)) {
            return;
        }
        tradeSales.put(nationId, TradeSales.record(tradeSales.get(nationId), today, packs, spurs));
        setDirty();
    }

    public void flushTrade(long today) {
        boolean dirty = false;
        for (UUID nationId : new ArrayList<>(tradeSales.keySet())) {
            TradeSales.Book before = tradeSales.get(nationId);
            TradeSales.Flush flush = TradeSales.flush(before, today);
            if (flush.book().isEmpty()) {
                tradeSales.remove(nationId);
            } else {
                tradeSales.put(nationId, flush.book());
            }
            if (!flush.book().equals(before)) {
                dirty = true;
            }
            if (flush.report() != null) {
                registry.byId(nationId).ifPresent(nation -> notices.add(new PendingNotice(
                        nation.owner(),
                        false,
                        "command.colony_nations.trade.day",
                        java.util.List.of(
                                Integer.toString(flush.report().packs()),
                                Long.toString(flush.report().spurs())))));
                dirty = true;
            }
        }
        if (dirty) {
            setDirty();
        }
    }

    public void clearTrade(UUID nationId) {
        if (nationId == null) {
            return;
        }
        boolean changed = tradePolicies.remove(nationId) != null || tradeSales.remove(nationId) != null;
        if (changed) {
            setDirty();
        }
    }

    private ListTag writeTradePolicies() {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, TradePolicy> entry : tradePolicies.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Nation", entry.getKey().toString());
            tag.putInt("Default", entry.getValue().defaultTariff());
            ListTag duties = new ListTag();
            for (Map.Entry<UUID, TradePolicy.Duty> duty : entry.getValue().duties().entrySet()) {
                CompoundTag row = new CompoundTag();
                row.putString("Buyer", duty.getKey().toString());
                row.putInt("Tariff", duty.getValue().tariff());
                row.putBoolean("Embargo", duty.getValue().embargo());
                duties.add(row);
            }
            tag.put("Duties", duties);
            list.add(tag);
        }
        return list;
    }

    private static Map<UUID, TradePolicy> readTradePolicies(CompoundTag tag) {
        Map<UUID, TradePolicy> policies = new HashMap<>();
        ListTag list = tag.getList(TRADE_POLICIES, Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag policy)) {
                continue;
            }
            try {
                UUID nation = UUID.fromString(policy.getString("Nation"));
                Map<UUID, TradePolicy.Duty> duties = new HashMap<>();
                ListTag rows = policy.getList("Duties", Tag.TAG_COMPOUND);
                for (Tag rowEntry : rows) {
                    if (!(rowEntry instanceof CompoundTag row)) {
                        continue;
                    }
                    duties.put(
                            UUID.fromString(row.getString("Buyer")),
                            new TradePolicy.Duty(row.getInt("Tariff"), row.getBoolean("Embargo")));
                }
                policies.put(nation, new TradePolicy(policy.getInt("Default"), duties));
            } catch (IllegalArgumentException ignored) {
                // Skip a damaged nation id.
            }
        }
        return policies;
    }

    private ListTag writeTradeSales() {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, TradeSales.Book> entry : tradeSales.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Nation", entry.getKey().toString());
            writeDay(tag, "Open", entry.getValue().open());
            writeDay(tag, "Closed", entry.getValue().closed());
            list.add(tag);
        }
        return list;
    }

    private static void writeDay(CompoundTag tag, String prefix, TradeSales.Day day) {
        tag.putLong(prefix + "Day", day.day());
        tag.putInt(prefix + "Packs", day.packs());
        tag.putLong(prefix + "Spurs", day.spurs());
    }

    private static Map<UUID, TradeSales.Book> readTradeSales(CompoundTag tag) {
        Map<UUID, TradeSales.Book> sales = new HashMap<>();
        ListTag list = tag.getList(TRADE_SALES, Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag row)) {
                continue;
            }
            try {
                sales.put(UUID.fromString(row.getString("Nation")), new TradeSales.Book(readDay(row, "Open"), readDay(row, "Closed")));
            } catch (IllegalArgumentException ignored) {
                // Skip a damaged nation id.
            }
        }
        return sales;
    }

    private static TradeSales.Day readDay(CompoundTag tag, String prefix) {
        if (!tag.contains(prefix + "Day")) {
            return TradeSales.Day.unset();
        }
        return new TradeSales.Day(tag.getLong(prefix + "Day"), Math.max(0, tag.getInt(prefix + "Packs")), Math.max(0L, tag.getLong(prefix + "Spurs")));
    }
}
