package com.mrzeyner.colonynations.economy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Picks the nation a breaking city asks to join.
 * The candidate must be within range and charge a lower tax. Ties prefer the lower rate, then the nearer land.
 */
public final class SecessionJoin {
    public record Candidate(UUID nationId, int taxRate, int distance) {
    }

    private static final Comparator<Candidate> ORDER = Comparator.comparingInt(Candidate::taxRate)
            .thenComparingInt(Candidate::distance)
            .thenComparing(candidate -> candidate.nationId().toString());

    private SecessionJoin() {
    }

    public static Optional<Candidate> choose(int leavingTaxRate, int radius, List<Candidate> neighbors) {
        if (neighbors == null || radius < 0) {
            return Optional.empty();
        }
        return neighbors.stream()
                .filter(candidate -> candidate.distance() >= 0 && candidate.distance() <= radius)
                .filter(candidate -> candidate.taxRate() < leavingTaxRate)
                .min(ORDER);
    }
}
