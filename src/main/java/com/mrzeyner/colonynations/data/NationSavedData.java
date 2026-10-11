package com.mrzeyner.colonynations.data;

import java.util.ArrayList;
import java.util.List;
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
 * Overworld saved data for {@link NationRegistry} and its state land.
 * Both are stored as the versioned bytes from {@link NationCodec}.
 * An unreadable payload is kept as-is so a save does not erase it.
 */
public final class NationSavedData extends SavedData {
    public static final String DATA_ID = "colony_nations";
    private static final String PAYLOAD = "Payload";
    private static final String UPKEEP_DAY = "UpkeepDay";
    private static final String NOTICES = "Notices";

    private final NationRegistry registry;
    private final TerritoryMap territory;
    private final byte[] preservedPayload;
    private final int unreadVersion;
    private final List<StoredNotice> notices = new ArrayList<>();
    private long upkeepDay = -1L;

    public NationSavedData() {
        this(new NationRegistry(), new TerritoryMap(), null, 0);
    }

    private NationSavedData(
            NationRegistry registry,
            TerritoryMap territory,
            byte[] preservedPayload,
            int unreadVersion) {
        this.registry = registry;
        this.territory = territory;
        this.preservedPayload = preservedPayload == null ? null : preservedPayload.clone();
        this.unreadVersion = unreadVersion;
    }

    public NationRegistry registry() {
        return registry;
    }

    public TerritoryMap territory() {
        return territory;
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
    public long upkeepDay() {
        return upkeepDay;
    }

    public void setUpkeepDay(long upkeepDay) {
        this.upkeepDay = upkeepDay;
    }

    public void addNotice(UUID player, boolean event, String key, String... args) {
        notices.add(new StoredNotice(player, event, key, List.of(args)));
    }

    public List<StoredNotice> takeNotices(UUID player) {
        List<StoredNotice> mine = new ArrayList<>();
        notices.removeIf(notice -> {
            if (!notice.player().equals(player)) {
                return false;
            }
            mine.add(notice);
            return true;
        });
        return mine;
    }

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
        data.upkeepDay = tag.contains(UPKEEP_DAY) ? tag.getLong(UPKEEP_DAY) : -1L;
        data.readNotices(tag.getList(NOTICES, Tag.TAG_COMPOUND));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        byte[] payload = preservedPayload != null
                ? preservedPayload
                : NationCodec.write(registry, territory);
        tag.putByteArray(PAYLOAD, payload.clone());
        tag.putLong(UPKEEP_DAY, upkeepDay);
        tag.put(NOTICES, writeNotices());
        return tag;
    }

    private ListTag writeNotices() {
        ListTag list = new ListTag();
        for (StoredNotice notice : notices) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Player", notice.player().toString());
            tag.putBoolean("Event", notice.event());
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

    private void readNotices(ListTag list) {
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag tag)) {
                continue;
            }
            List<String> args = new ArrayList<>();
            for (Tag arg : tag.getList("Args", Tag.TAG_STRING)) {
                if (arg instanceof StringTag text) {
                    args.add(text.getAsString());
                }
            }
            notices.add(new StoredNotice(UUID.fromString(tag.getString("Player")), tag.getBoolean("Event"), tag.getString("Key"), args));
        }
    }
}
