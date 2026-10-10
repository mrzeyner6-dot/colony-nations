package com.mrzeyner.colonynations.nation;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A nation. Immutable; the registry replaces instances when state changes.
 */
public final class Nation {
    public static final int MIN_NAME_LENGTH = 3;
    public static final int MAX_NAME_LENGTH = 24;

    private final UUID id;
    private final String name;
    private final UUID owner;
    private final int color;
    private final Integer capitalColonyId;
    private final Set<Integer> colonyIds;
    private final long treasury;
    private final long createdGameTime;

    public Nation(
            UUID id,
            String name,
            UUID owner,
            int color,
            Integer capitalColonyId,
            Collection<Integer> colonyIds,
            long treasury,
            long createdGameTime) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.color = color;
        this.colonyIds = Set.copyOf(colonyIds);
        if (capitalColonyId != null && !this.colonyIds.contains(capitalColonyId)) {
            throw new IllegalArgumentException("capital is not one of the nation's colonies");
        }
        this.capitalColonyId = capitalColonyId;
        this.treasury = treasury;
        this.createdGameTime = createdGameTime;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID owner() {
        return owner;
    }

    public int color() {
        return color;
    }

    /**
     * Capital colony id, or null when the nation has no cities yet.
     */
    public Integer capitalColonyId() {
        return capitalColonyId;
    }

    public Set<Integer> colonyIds() {
        return colonyIds;
    }

    public long treasury() {
        return treasury;
    }

    public long createdGameTime() {
        return createdGameTime;
    }

    public Nation withName(String name) {
        return new Nation(id, name, owner, color, capitalColonyId, colonyIds, treasury, createdGameTime);
    }

    public Nation withColor(int color) {
        return new Nation(id, name, owner, color, capitalColonyId, colonyIds, treasury, createdGameTime);
    }

    public Nation withTreasury(long treasury) {
        return new Nation(id, name, owner, color, capitalColonyId, colonyIds, treasury, createdGameTime);
    }

    public Nation withCapital(int colonyId) {
        if (!colonyIds.contains(colonyId)) {
            throw new NationException(NationException.Reason.UNKNOWN_COLONY);
        }
        return new Nation(id, name, owner, color, colonyId, colonyIds, treasury, createdGameTime);
    }

    /**
     * Adds a colony. The first colony becomes the capital.
     */
    public Nation addColony(int colonyId) {
        if (colonyIds.contains(colonyId)) {
            return this;
        }
        Set<Integer> next = new LinkedHashSet<>(colonyIds);
        next.add(colonyId);
        Integer capital = capitalColonyId == null ? colonyId : capitalColonyId;
        return new Nation(id, name, owner, color, capital, next, treasury, createdGameTime);
    }

    /**
     * Removes a colony. If it was the capital, the colony with the smallest id becomes the capital.
     */
    public Nation removeColony(int colonyId) {
        if (!colonyIds.contains(colonyId)) {
            throw new NationException(NationException.Reason.UNKNOWN_COLONY);
        }
        Set<Integer> next = new LinkedHashSet<>(colonyIds);
        next.remove(colonyId);
        Integer capital = capitalColonyId;
        if (Objects.equals(capital, colonyId)) {
            capital = next.stream().min(Comparator.naturalOrder()).orElse(null);
        }
        return new Nation(id, name, owner, color, capital, next, treasury, createdGameTime);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Nation nation)) {
            return false;
        }
        return color == nation.color
                && treasury == nation.treasury
                && createdGameTime == nation.createdGameTime
                && id.equals(nation.id)
                && name.equals(nation.name)
                && owner.equals(nation.owner)
                && Objects.equals(capitalColonyId, nation.capitalColonyId)
                && colonyIds.equals(nation.colonyIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, owner, color, capitalColonyId, colonyIds, treasury, createdGameTime);
    }
}
