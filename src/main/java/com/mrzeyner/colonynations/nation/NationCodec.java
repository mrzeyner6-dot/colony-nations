package com.mrzeyner.colonynations.nation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.TerritoryMap;

/**
 * Versioned binary form of a {@link NationRegistry}.
 * {@code NationSavedData} stores these bytes inside NBT so the round trip can be tested without Minecraft.
 */
public final class NationCodec {
    public static final int VERSION = 5;
    private static final int VERSION_WITH_NATIONS_ONLY = 2;
    private static final int VERSION_WITH_TERRITORY = 3;
    private static final int VERSION_WITH_TAX = 4;
    private static final int VERSION_WITH_SEPARATISM = 5;

    static final Logger LOGGER = Logger.getLogger(NationCodec.class.getName());

    private static final Comparator<CityRef> CITY_ORDER =
            Comparator.comparing(CityRef::dimension).thenComparingInt(CityRef::colonyId);
    private static final Comparator<ChunkRef> CHUNK_ORDER = Comparator.comparing(ChunkRef::dimension)
            .thenComparingInt(ChunkRef::x)
            .thenComparingInt(ChunkRef::z);

    /**
     * @param warning set when the payload version is outside 2…{@link #VERSION}; the registry is then empty
     * @param preserved original payload bytes when {@code warning} is set, so a later save can write them back
     * @param version payload version that was read
     */
    public record Loaded(NationRegistry registry, TerritoryMap territory, String warning, byte[] preserved, int version) {
        public Loaded {
            territory = territory == null ? new TerritoryMap() : territory;
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
        return writeAt(VERSION, registry, territory);
    }

    /**
     * Version 2 layout: nations only. Used to test migration to {@link #VERSION}.
     */
    static byte[] writeVersion2(NationRegistry registry) {
        return writeAt(VERSION_WITH_NATIONS_ONLY, registry, new TerritoryMap());
    }

    /**
     * Version 3 layout: nations without a tax rate, plus territory.
     */
    static byte[] writeVersion3(NationRegistry registry, TerritoryMap territory) {
        return writeAt(VERSION_WITH_TERRITORY, registry, territory);
    }

    /**
     * Version 4 layout: tax rate, no per-city separatism, plus territory.
     */
    static byte[] writeVersion4(NationRegistry registry, TerritoryMap territory) {
        return writeAt(VERSION_WITH_TAX, registry, territory);
    }

    private static byte[] writeAt(int version, NationRegistry registry, TerritoryMap territory) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(version);
            List<Nation> nations = new ArrayList<>(registry.all());
            nations.sort(Comparator.comparing(nation -> nation.id().toString()));
            out.writeInt(nations.size());
            for (Nation nation : nations) {
                writeNation(out, nation, version);
            }
            if (version >= VERSION_WITH_TERRITORY) {
                writeTerritory(out, territory);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write nations", exception);
        }
        return bytes.toByteArray();
    }

    /**
     * Writes a registry, or the original bytes when {@code loaded} could not be read.
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
            if (version < VERSION_WITH_NATIONS_ONLY || version > VERSION) {
                return new Loaded(
                        new NationRegistry(),
                        new TerritoryMap(),
                        "Unknown nation save version " + version
                                + " (expected " + VERSION
                                + "). The original save was kept and nation changes are blocked.",
                        payload,
                        version);
            }
            int count = in.readInt();
            List<Nation> nations = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                nations.add(readNation(in, version));
            }
            NationRegistry registry = new NationRegistry();
            registry.restore(nations);
            TerritoryMap territory = version >= VERSION_WITH_TERRITORY ? readTerritory(in) : new TerritoryMap();
            return new Loaded(registry, territory, null, null, version);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read nations", exception);
        }
    }

    private static void writeNation(DataOutputStream out, Nation nation, int version) throws IOException {
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
        if (version >= VERSION_WITH_TAX) {
            out.writeInt(nation.taxRate());
            Long lastTaxChangeDay = nation.lastTaxChangeDay();
            out.writeBoolean(lastTaxChangeDay != null);
            if (lastTaxChangeDay != null) {
                out.writeLong(lastTaxChangeDay);
            }
        }
        if (version >= VERSION_WITH_SEPARATISM) {
            List<CityRef> scored = new ArrayList<>();
            for (CityRef city : nation.separatism().keySet()) {
                if (nation.separatism(city) > 0) {
                    scored.add(city);
                }
            }
            scored.sort(CITY_ORDER);
            out.writeInt(scored.size());
            for (CityRef city : scored) {
                writeCity(out, city);
                out.writeInt(nation.separatism(city));
            }
        }
    }

    private static Nation readNation(DataInputStream in, int version) throws IOException {
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
        int taxRate = Nation.DEFAULT_TAX_RATE;
        Long lastTaxChangeDay = null;
        if (version >= VERSION_WITH_TAX) {
            taxRate = in.readInt();
            if (in.readBoolean()) {
                lastTaxChangeDay = in.readLong();
            }
        }
        Map<CityRef, Integer> separatism = new LinkedHashMap<>();
        if (version >= VERSION_WITH_SEPARATISM) {
            int scored = in.readInt();
            for (int i = 0; i < scored; i++) {
                separatism.put(readCity(in), in.readInt());
            }
        }
        return new Nation(
                id,
                name,
                owner,
                color,
                capital,
                cities,
                treasury,
                createdGameTime,
                taxRate,
                lastTaxChangeDay,
                separatism);
    }

    private static void writeCity(DataOutputStream out, CityRef city) throws IOException {
        out.writeUTF(city.dimension());
        out.writeInt(city.colonyId());
    }

    private static CityRef readCity(DataInputStream in) throws IOException {
        return new CityRef(in.readUTF(), in.readInt());
    }

    private static void writeTerritory(DataOutputStream out, TerritoryMap territory) throws IOException {
        List<ChunkRef> chunks = new ArrayList<>(territory.claims().keySet());
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
            territory.claim(chunk, UUID.fromString(in.readUTF()));
        }
        return territory;
    }
}
