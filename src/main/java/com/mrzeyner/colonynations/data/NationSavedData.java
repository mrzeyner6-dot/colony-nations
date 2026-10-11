package com.mrzeyner.colonynations.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.nation.NationCodec;
import com.mrzeyner.colonynations.nation.NationRegistry;
import com.mrzeyner.colonynations.territory.TerritoryMap;

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

    private final NationRegistry registry;
    private final TerritoryMap territory;
    private final List<PendingNotice> notices = new ArrayList<>();
    private final List<FreeCity> freeCities = new ArrayList<>();
    private final List<SecessionOffer> offers = new ArrayList<>();
    private final Set<UUID> menuHints = new HashSet<>();
    private final byte[] preservedPayload;
    private final int unreadVersion;
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
            cities.add(new FreeCity(
                    new com.mrzeyner.colonynations.nation.CityRef(city.getString("Dimension"), city.getInt("ColonyId")),
                    city.getString("Name"),
                    city.getInt("Color")));
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
}
