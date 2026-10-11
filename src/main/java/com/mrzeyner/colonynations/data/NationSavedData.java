package com.mrzeyner.colonynations.data;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.nation.NationCodec;
import com.mrzeyner.colonynations.nation.NationRegistry;
import com.mrzeyner.colonynations.territory.TerritoryMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Overworld saved data for {@link NationRegistry} and its state land.
 * Both are stored as the versioned bytes from {@link NationCodec}.
 * An unreadable payload is kept as-is so a save does not erase it.
 */
public final class NationSavedData extends SavedData {
    public static final String DATA_ID = "colony_nations";
    private static final String PAYLOAD = "Payload";

    private final NationRegistry registry;
    private final TerritoryMap territory;
    private final byte[] preservedPayload;
    private final int unreadVersion;

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
        if (loaded.unreadable()) {
            ColonyNations.LOGGER.error(loaded.warning());
            return new NationSavedData(loaded.registry(), loaded.territory(), loaded.preserved(), loaded.version());
        }
        return new NationSavedData(loaded.registry(), loaded.territory(), null, loaded.version());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        byte[] payload = preservedPayload != null
                ? preservedPayload
                : NationCodec.write(registry, territory);
        tag.putByteArray(PAYLOAD, payload.clone());
        return tag;
    }
}
