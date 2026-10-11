package com.mrzeyner.colonynations.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.FreeCity;
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
    private static final String OFFERS = "SecessionOffers";

    private final NationRegistry registry;
    private final TerritoryMap territory;
    private final List<FreeCity> freeCities = new ArrayList<>();
    private final List<SecessionOffer> offers = new ArrayList<>();
    private final List<PendingNotice> notices = new ArrayList<>();
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

    public void replaceNotices(List<PendingNotice> next) {
        notices.clear();
        notices.addAll(next);
        setDirty();
    }

    /**
     * Drops offer lines for one colony so an expired request is not shown on the next login.
     */
    public void removeNotices(UUID owner, String key, String colonyId) {
        boolean removed = notices.removeIf(notice -> notice.owner().equals(owner)
                && notice.key().equals(key)
                && notice.args().size() >= 3
                && colonyId.equals(notice.args().get(2)));
        if (removed) {
            setDirty();
        }
    }

    public List<FreeCity> freeCities() {
        return List.copyOf(freeCities);
    }

    public Optional<FreeCity> freeCity(CityRef city) {
        for (FreeCity free : freeCities) {
            if (free.city().equals(city)) {
                return Optional.of(free);
            }
        }
        return Optional.empty();
    }

    public boolean putFreeCity(FreeCity city) {
        freeCities.removeIf(existing -> existing.city().equals(city.city()));
        freeCities.add(city);
        setDirty();
        return true;
    }

    public boolean removeFreeCity(CityRef city) {
        boolean removed = freeCities.removeIf(existing -> existing.city().equals(city));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public List<SecessionOffer> offers() {
        return List.copyOf(offers);
    }

    public Optional<SecessionOffer> offer(CityRef city) {
        for (SecessionOffer offer : offers) {
            if (offer.city().equals(city)) {
                return Optional.of(offer);
            }
        }
        return Optional.empty();
    }

    public void putOffer(SecessionOffer offer) {
        offers.removeIf(existing -> existing.city().equals(offer.city()));
        offers.add(offer);
        setDirty();
    }

    public boolean removeOffer(CityRef city) {
        boolean removed = offers.removeIf(existing -> existing.city().equals(city));
        if (removed) {
            setDirty();
        }
        return removed;
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
            data.freeCities.addAll(loaded.freeCities());
        }
        data.notices.addAll(readNotices(tag));
        data.offers.addAll(readOffers(tag));
        if (tag.contains(LAST_UPKEEP_DAY)) {
            data.lastUpkeepDay = tag.getLong(LAST_UPKEEP_DAY);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        byte[] payload = preservedPayload != null
                ? preservedPayload
                : NationCodec.write(registry, territory, freeCities);
        tag.putByteArray(PAYLOAD, payload.clone());
        tag.put(NOTICES, writeNotices());
        tag.put(OFFERS, writeOffers());
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

    private ListTag writeOffers() {
        ListTag list = new ListTag();
        for (SecessionOffer offer : offers) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Target", offer.targetNation().toString());
            tag.putString("TargetOwner", offer.targetOwner().toString());
            tag.putString("From", offer.fromNation().toString());
            tag.putString("Dimension", offer.city().dimension());
            tag.putInt("ColonyId", offer.city().colonyId());
            tag.putString("CityName", offer.cityName());
            tag.putString("FromName", offer.fromNationName());
            tag.putString("TargetName", offer.targetNationName());
            tag.putLong("Expires", offer.expiresOnDay());
            list.add(tag);
        }
        return list;
    }

    private static List<SecessionOffer> readOffers(CompoundTag tag) {
        List<SecessionOffer> offers = new ArrayList<>();
        ListTag list = tag.getList(OFFERS, Tag.TAG_COMPOUND);
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag offer)) {
                continue;
            }
            offers.add(new SecessionOffer(
                    UUID.fromString(offer.getString("Target")),
                    UUID.fromString(offer.getString("TargetOwner")),
                    new CityRef(offer.getString("Dimension"), offer.getInt("ColonyId")),
                    UUID.fromString(offer.getString("From")),
                    offer.getString("CityName"),
                    offer.getString("FromName"),
                    offer.getString("TargetName"),
                    offer.getLong("Expires")));
        }
        return offers;
    }
}
