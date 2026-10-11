package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SecessionJoinTest {
    private static final UUID NEAR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FAR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHEAP = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void lowerTaxInsideTheRadiusWins() {
        SecessionJoin.Candidate chosen = SecessionJoin.choose(4, 8, List.of(
                new SecessionJoin.Candidate(NEAR, 3, 2),
                new SecessionJoin.Candidate(FAR, 1, 9),
                new SecessionJoin.Candidate(CHEAP, 2, 8))).orElseThrow();
        assertEquals(CHEAP, chosen.nationId());
    }

    @Test
    void equalTaxPrefersTheNearerLand() {
        SecessionJoin.Candidate chosen = SecessionJoin.choose(5, 8, List.of(
                new SecessionJoin.Candidate(FAR, 1, 6),
                new SecessionJoin.Candidate(NEAR, 1, 1))).orElseThrow();
        assertEquals(NEAR, chosen.nationId());
    }

    @Test
    void noLowerTaxMeansIndependence() {
        assertTrue(SecessionJoin.choose(2, 8, List.of(new SecessionJoin.Candidate(NEAR, 2, 1))).isEmpty());
        assertTrue(SecessionJoin.choose(4, 8, List.of()).isEmpty());
    }
}
