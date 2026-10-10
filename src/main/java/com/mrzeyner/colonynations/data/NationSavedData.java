package com.mrzeyner.colonynations.data;

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

    public static SavedData.Factory<NationSavedData> factory() {
        return new SavedData.Factory<>(NationSavedData::new, NationSavedData::load, null);
    }

    public static NationSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        return new NationSavedData(NationCodec.read(tag.getByteArray(PAYLOAD)));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putByteArray(PAYLOAD, NationCodec.write(registry));
        return tag;
    }
}
