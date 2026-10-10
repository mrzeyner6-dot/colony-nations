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

/**
 * Versioned binary form of a {@link NationRegistry}.
 * {@code NationSavedData} stores these bytes inside NBT so the round trip can be tested without Minecraft.
 */
public final class NationCodec {
    public static final int VERSION = 1;

    private NationCodec() {
    }

    public static byte[] write(NationRegistry registry) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            List<Nation> nations = new ArrayList<>(registry.all());
            nations.sort(Comparator.comparing(nation -> nation.id().toString()));
            out.writeInt(nations.size());
            for (Nation nation : nations) {
                writeNation(out, nation);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write nations", exception);
        }
        return bytes.toByteArray();
    }

    public static NationRegistry read(byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version != VERSION) {
                throw new IllegalStateException("unsupported nation payload version " + version);
            }
            int count = in.readInt();
            List<Nation> nations = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                nations.add(readNation(in));
            }
            NationRegistry registry = new NationRegistry();
            registry.restore(nations);
            return registry;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read nations", exception);
        }
    }

    private static void writeNation(DataOutputStream out, Nation nation) throws IOException {
        out.writeUTF(nation.id().toString());
        out.writeUTF(nation.name());
        out.writeUTF(nation.owner().toString());
        out.writeInt(nation.color());
        out.writeBoolean(nation.capitalColonyId() != null);
        if (nation.capitalColonyId() != null) {
            out.writeInt(nation.capitalColonyId());
        }
        List<Integer> colonies = new ArrayList<>(nation.colonyIds());
        colonies.sort(Comparator.naturalOrder());
        out.writeInt(colonies.size());
        for (int colonyId : colonies) {
            out.writeInt(colonyId);
        }
        out.writeLong(nation.treasury());
        out.writeLong(nation.createdGameTime());
    }

    private static Nation readNation(DataInputStream in) throws IOException {
        UUID id = UUID.fromString(in.readUTF());
        String name = in.readUTF();
        UUID owner = UUID.fromString(in.readUTF());
        int color = in.readInt();
        Integer capital = in.readBoolean() ? in.readInt() : null;
        int colonyCount = in.readInt();
        List<Integer> colonies = new ArrayList<>(colonyCount);
        for (int i = 0; i < colonyCount; i++) {
            colonies.add(in.readInt());
        }
        long treasury = in.readLong();
        long createdGameTime = in.readLong();
        return new Nation(id, name, owner, color, capital, colonies, treasury, createdGameTime);
    }
}
