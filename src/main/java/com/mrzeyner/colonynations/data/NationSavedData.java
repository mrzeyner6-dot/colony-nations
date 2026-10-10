package com.mrzeyner.colonynations.data;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.nation.NationCodec;
import com.mrzeyner.colonynations.nation.NationRegistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Overworld saved data for {@link NationRegistry}.
 * The registry itself is stored as the versioned bytes from {@link NationCodec}.
 */
public final class NationSavedData extends SavedData {
    public static final String DATA_ID = "colony_nations";
    private static final String PAYLOAD = "Payload";

    private final NationRegistry registry;

    public NationSavedData() {
        this(new NationRegistry());
    }

    public NationSavedData(NationRegistry registry) {
        this.registry = registry;
    }

    public NationRegistry registry() {
        return registry;
    }

    public static final SavedData.Factory<NationSavedData> FACTORY =
            new SavedData.Factory<>(NationSavedData::new, NationSavedData::load, null);

    public static NationSavedData get(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    public static NationSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        NationCodec.Loaded loaded = NationCodec.load(tag.getByteArray(PAYLOAD));
        if (loaded.warning() != null) {
            ColonyNations.LOGGER.error(loaded.warning());
        }
        return new NationSavedData(loaded.registry());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putByteArray(PAYLOAD, NationCodec.write(registry));
        return tag;
    }
}
