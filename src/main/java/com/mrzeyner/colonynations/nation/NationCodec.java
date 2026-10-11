package com.mrzeyner.colonynations.nation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.TerritoryMap;

/**
 * Versioned binary form of a {@link NationRegistry} plus its state land.
 * {@code NationSavedData} stores these bytes inside NBT so the round trip can be tested without Minecraft.
 * Version 2 is nations only and loads as an empty {@link TerritoryMap}. Older versions stay unreadable.
 */
public final class NationCodec {
    public static final int VERSION = 3;
    private static final int VERSION_WITHOUT_TERRITORY = 2;

    static final Logger LOGGER = Logger.getLogger(NationCodec.class.getName());

    private static final Comparator<CityRef> CITY_ORDER =
            Comparator.comparing(CityRef::dimension).thenComparingInt(CityRef::colonyId);

    private static final Comparator<ChunkRef> CHUNK_ORDER = Comparator.comparing(ChunkRef::dimension)
            .thenComparingInt(ChunkRef::x)
            .thenComparingInt(ChunkRef::z);

    /**
     * @param warning set when the payload version is not readable; the registry and territory are then empty
     * @param preserved original payload bytes when {@code warning} is set, so a later save can write them back
     * @param version payload version that was read
     */
    public record Loaded(
            NationRegistry registry,
            TerritoryMap territory,
            String warning,
            byte[] preserved,
            int version) {
        public Loaded {
            preserved = preserved == null ? null : preserved.clone();
        }

        public boolean unreadable() {
            return warning != null;
        }
    }

    private NationCodec() {
    }

    public static byte[] write(NationRegistry registry) {
        return write(registry, new TerritoryMap());
    }

    public static byte[] write(NationRegistry registry, TerritoryMap territory) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            writeNations(out, registry);
            writeTerritory(out, territory);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write nations", exception);
        }
        return bytes.toByteArray();
    }

    /**
     * Historical version 2 layout (nations, no territory). Tests use this to check migration.
     */
    static byte[] writeVersion2(NationRegistry registry) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION_WITHOUT_TERRITORY);
            writeNations(out, registry);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write nations", exception);
        }
        return bytes.toByteArray();
    }

    /**
     * Writes a registry and its territory, or the original bytes when {@code loaded} could not be read.
     */
    public static byte[] write(Loaded loaded) {
        if (loaded.preserved() != null) {
            return loaded.preserved().clone();
        }
        return write(loaded.registry(), loaded.territory());
    }

    public static NationRegistry read(byte[] payload) {
        Loaded loaded = load(payload);
        if (loaded.warning() != null) {
            LOGGER.severe(loaded.warning());
        }
        return loaded.registry();
    }

    public static Loaded load(byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version != VERSION && version != VERSION_WITHOUT_TERRITORY) {
                return new Loaded(
                        new NationRegistry(),
                        new TerritoryMap(),
                        "Unknown nation save version " + version
                                + " (expected " + VERSION
                                + "). The original save was kept and nation changes are blocked.",
                        payload,
                        version);
            }
            NationRegistry registry = readNations(in);
            TerritoryMap territory = version == VERSION ? readTerritory(in) : new TerritoryMap();
            return new Loaded(registry, territory, null, null, version);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read nations", exception);
        }
    }

    private static void writeNations(DataOutputStream out, NationRegistry registry) throws IOException {
        List<Nation> nations = new ArrayList<>(registry.all());
        nations.sort(Comparator.comparing(nation -> nation.id().toString()));
        out.writeInt(nations.size());
        for (Nation nation : nations) {
            writeNation(out, nation);
        }
    }

    private static NationRegistry readNations(DataInputStream in) throws IOException {
        int count = in.readInt();
        List<Nation> nations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            nations.add(readNation(in));
        }
        NationRegistry registry = new NationRegistry();
        registry.restore(nations);
        return registry;
    }

    private static void writeTerritory(DataOutputStream out, TerritoryMap territory) throws IOException {
        List<ChunkRef> chunks = new ArrayList<>(territory.allChunks());
        chunks.sort(CHUNK_ORDER);
        out.writeInt(chunks.size());
        for (ChunkRef chunk : chunks) {
            out.writeUTF(chunk.dimension());
            out.writeInt(chunk.x());
            out.writeInt(chunk.z());
            out.writeUTF(territory.owner(chunk).orElseThrow().toString());
        }
    }

    private static TerritoryMap readTerritory(DataInputStream in) throws IOException {
        int count = in.readInt();
        TerritoryMap territory = new TerritoryMap();
        for (int i = 0; i < count; i++) {
            ChunkRef chunk = new ChunkRef(in.readUTF(), in.readInt(), in.readInt());
            UUID nationId = UUID.fromString(in.readUTF());
            if (territory.owner(chunk).isPresent()) {
                throw new IOException("chunk claimed twice");
            }
            territory.claim(chunk, nationId);
        }
        return territory;
    }

    private static void writeNation(DataOutputStream out, Nation nation) throws IOException {
        out.writeUTF(nation.id().toString());
        out.writeUTF(nation.name());
        out.writeUTF(nation.owner().toString());
        out.writeInt(nation.color());
        out.writeBoolean(nation.capital() != null);
        if (nation.capital() != null) {
            writeCity(out, nation.capital());
        }
        List<CityRef> cities = new ArrayList<>(nation.cities());
        cities.sort(CITY_ORDER);
        out.writeInt(cities.size());
        for (CityRef city : cities) {
            writeCity(out, city);
        }
        out.writeLong(nation.treasury());
        out.writeLong(nation.createdGameTime());
    }

    private static Nation readNation(DataInputStream in) throws IOException {
        UUID id = UUID.fromString(in.readUTF());
        String name = in.readUTF();
        UUID owner = UUID.fromString(in.readUTF());
        int color = in.readInt();
        CityRef capital = in.readBoolean() ? readCity(in) : null;
        int cityCount = in.readInt();
        List<CityRef> cities = new ArrayList<>(cityCount);
        for (int i = 0; i < cityCount; i++) {
            cities.add(readCity(in));
        }
        long treasury = in.readLong();
        long createdGameTime = in.readLong();
        return new Nation(id, name, owner, color, capital, cities, treasury, createdGameTime);
    }

    private static void writeCity(DataOutputStream out, CityRef city) throws IOException {
        out.writeUTF(city.dimension());
        out.writeInt(city.colonyId());
    }

    private static CityRef readCity(DataInputStream in) throws IOException {
        return new CityRef(in.readUTF(), in.readInt());
    }
}
