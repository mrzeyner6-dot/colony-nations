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
 * Overworld saved data for {@link NationRegistry} and state land.
 * Both are stored as the versioned bytes from {@link NationCodec}.
 * An unreadable payload is kept as-is so a save does not erase it.
 */
public final class NationSavedData extends SavedData {
    public static final String DATA_ID = "colony_nations";
    private static final String PAYLOAD = "Payload";
    private static final String NOTICES = "Notices";

    private final NationRegistry registry;
    private final TerritoryMap territory;
    private final List<PendingNotice> notices = new ArrayList<>();
    private final byte[] preservedPayload;
    private final int unreadVersion;

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
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        byte[] payload = preservedPayload != null ? preservedPayload : NationCodec.write(registry, territory);
        tag.putByteArray(PAYLOAD, payload.clone());
        tag.put(NOTICES, writeNotices());
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
}
