package com.mrzeyner.colonynations.economy;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mrzeyner.colonynations.territory.ChunkRef;

/**
 * Chooses which nation, if any, may be offered a seceding city.
 * Distance is Chebyshev, in chunks, between the city claim and the other nation's land
 * (state or city chunks count). Only the same dimension is compared.
 * A nation qualifies when that distance is within the radius and its tax rate is strictly lower.
 * Ties keep the lower rate, then the nearer land, then the smaller nation id string.
 */
public final class Secession {
    public record Neighbor(UUID nation, int taxRate, Set<ChunkRef> land) {
        public Neighbor {
            Objects.requireNonNull(nation, "nation");
            land = land == null ? Set.of() : Set.copyOf(land);
        }
    }

    public record Choice(UUID nation, int taxRate, int distance) {
        public Choice {
            Objects.requireNonNull(nation, "nation");
        }
    }

    private Secession() {
    }

    /**
     * The offer made on {@code today} closes when the game day reaches this value.
     */
    public static long offerExpires(long today) {
        return today + 1L;
    }

    public static boolean offerOpen(long today, long expiresOnDay) {
        return today < expiresOnDay;
    }

    public static Optional<Choice> choose(int ownTaxRate, Collection<ChunkRef> cityClaim, Collection<Neighbor> others, int radius) {
        Objects.requireNonNull(cityClaim, "cityClaim");
        Objects.requireNonNull(others, "others");
        Choice best = null;
        for (Neighbor other : others) {
            if (other.taxRate() >= ownTaxRate) {
                continue;
            }
            int distance = distance(cityClaim, other.land());
            if (distance < 0 || distance > radius) {
                continue;
            }
            Choice candidate = new Choice(other.nation(), other.taxRate(), distance);
            if (best == null || better(candidate, best)) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * @return the closest Chebyshev distance, or {@code -1} when no pair shares a dimension
     */
    public static int distance(Collection<ChunkRef> claim, Collection<ChunkRef> land) {
        int best = Integer.MAX_VALUE;
        boolean any = false;
        for (ChunkRef from : claim) {
            for (ChunkRef to : land) {
                if (!from.dimension().equals(to.dimension())) {
                    continue;
                }
                long dx = Math.abs((long) from.x() - to.x());
                long dz = Math.abs((long) from.z() - to.z());
                int chebyshev = (int) Math.min(Integer.MAX_VALUE, Math.max(dx, dz));
                if (chebyshev < best) {
                    best = chebyshev;
                    any = true;
                }
            }
        }
        return any ? best : -1;
    }

    private static boolean better(Choice candidate, Choice best) {
        if (candidate.taxRate() != best.taxRate()) {
            return candidate.taxRate() < best.taxRate();
        }
        if (candidate.distance() != best.distance()) {
            return candidate.distance() < best.distance();
        }
        return candidate.nation().toString().compareTo(best.nation().toString()) < 0;
    }
}
