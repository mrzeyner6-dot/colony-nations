package com.mrzeyner.colonynations.nation;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory nations. Names are unique ignoring case. One nation per owner.
 * A colony belongs to at most one nation.
 */
public final class NationRegistry {
    private final Map<UUID, Nation> nations = new HashMap<>();
    private final Map<String, UUID> idByName = new HashMap<>();
    private final Map<UUID, UUID> idByOwner = new HashMap<>();
    private final Map<Integer, UUID> idByColony = new HashMap<>();

    public Nation create(String name, UUID owner, int color, long createdGameTime) {
        Objects.requireNonNull(owner, "owner");
        String normalized = normalizeName(name);
        if (idByOwner.containsKey(owner)) {
            throw new NationException(NationException.Reason.OWNER_HAS_NATION);
        }
        if (idByName.containsKey(nameKey(normalized))) {
            throw new NationException(NationException.Reason.NAME_TAKEN);
        }
        Nation nation = new Nation(UUID.randomUUID(), normalized, owner, color, null, Set.of(), 0L, createdGameTime);
        index(nation);
        return nation;
    }

    public void delete(UUID nationId) {
        Nation removed = nations.remove(requireNation(nationId).id());
        idByName.remove(nameKey(removed.name()));
        idByOwner.remove(removed.owner());
        for (int colonyId : removed.colonyIds()) {
            idByColony.remove(colonyId);
        }
    }

    public Nation rename(UUID nationId, String name) {
        Nation current = requireNation(nationId);
        String normalized = normalizeName(name);
        UUID takenBy = idByName.get(nameKey(normalized));
        if (takenBy != null && !takenBy.equals(nationId)) {
            throw new NationException(NationException.Reason.NAME_TAKEN);
        }
        idByName.remove(nameKey(current.name()));
        Nation renamed = current.withName(normalized);
        nations.put(nationId, renamed);
        idByName.put(nameKey(normalized), nationId);
        return renamed;
    }

    public Nation addColony(UUID nationId, int colonyId) {
        Nation current = requireNation(nationId);
        UUID ownerNation = idByColony.get(colonyId);
        if (ownerNation != null && !ownerNation.equals(nationId)) {
            throw new NationException(NationException.Reason.COLONY_TAKEN);
        }
        if (current.colonyIds().contains(colonyId)) {
            return current;
        }
        Nation updated = current.addColony(colonyId);
        nations.put(nationId, updated);
        idByColony.put(colonyId, nationId);
        return updated;
    }

    public Nation removeColony(UUID nationId, int colonyId) {
        Nation current = requireNation(nationId);
        if (!current.colonyIds().contains(colonyId)) {
            throw new NationException(NationException.Reason.UNKNOWN_COLONY);
        }
        Nation updated = current.removeColony(colonyId);
        nations.put(nationId, updated);
        idByColony.remove(colonyId);
        return updated;
    }

    public Nation setColor(UUID nationId, int color) {
        Nation updated = requireNation(nationId).withColor(color);
        nations.put(nationId, updated);
        return updated;
    }

    public Nation setCapital(UUID nationId, int colonyId) {
        Nation updated = requireNation(nationId).withCapital(colonyId);
        nations.put(nationId, updated);
        return updated;
    }

    public Nation setTreasury(UUID nationId, long treasury) {
        Nation updated = requireNation(nationId).withTreasury(treasury);
        nations.put(nationId, updated);
        return updated;
    }

    public Optional<Nation> byId(UUID nationId) {
        return Optional.ofNullable(nations.get(nationId));
    }

    public Optional<Nation> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(nations.get(idByName.get(nameKey(name.trim()))));
    }

    public Optional<Nation> byOwner(UUID owner) {
        return Optional.ofNullable(nations.get(idByOwner.get(owner)));
    }

    public Optional<Nation> byColony(int colonyId) {
        return Optional.ofNullable(nations.get(idByColony.get(colonyId)));
    }

    public Collection<Nation> all() {
        return List.copyOf(nations.values());
    }

    /**
     * Replaces this registry with the saved nations. Used by NBT load.
     */
    public void restore(Collection<Nation> saved) {
        nations.clear();
        idByName.clear();
        idByOwner.clear();
        idByColony.clear();
        for (Nation nation : saved) {
            if (nations.containsKey(nation.id())
                    || idByName.containsKey(nameKey(nation.name()))
                    || idByOwner.containsKey(nation.owner())) {
                throw new IllegalStateException("saved nations violate uniqueness");
            }
            for (int colonyId : nation.colonyIds()) {
                if (idByColony.containsKey(colonyId)) {
                    throw new IllegalStateException("saved nations share a colony");
                }
            }
            index(nation);
        }
    }

    private Nation requireNation(UUID nationId) {
        Nation nation = nations.get(nationId);
        if (nation == null) {
            throw new NationException(NationException.Reason.UNKNOWN_NATION);
        }
        return nation;
    }

    private void index(Nation nation) {
        nations.put(nation.id(), nation);
        idByName.put(nameKey(nation.name()), nation.id());
        idByOwner.put(nation.owner(), nation.id());
        for (int colonyId : nation.colonyIds()) {
            idByColony.put(colonyId, nation.id());
        }
    }

    static String normalizeName(String name) {
        if (name == null) {
            throw new NationException(NationException.Reason.NAME_LENGTH);
        }
        String trimmed = name.trim();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < Nation.MIN_NAME_LENGTH || length > Nation.MAX_NAME_LENGTH) {
            throw new NationException(NationException.Reason.NAME_LENGTH);
        }
        return trimmed;
    }

    private static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
