package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CoinStacksTest {
    private static final List<CoinChange.Denomination> LADDER = List.of(
            new CoinChange.Denomination("spur", 1),
            new CoinChange.Denomination("bevel", 8),
            new CoinChange.Denomination("sprocket", 16),
            new CoinChange.Denomination("cog", 64),
            new CoinChange.Denomination("crown", 512),
            new CoinChange.Denomination("sun", 4096));

    private static final Map<String, Integer> STACK_64 = Map.of(
            "spur", 64,
            "bevel", 64,
            "sprocket", 64,
            "cog", 64,
            "crown", 64,
            "sun", 64);

    @Test
    void stacksNeededRoundsUpToTheStackSize() {
        assertEquals(1L, CoinStacks.stacksNeeded(List.of(new CoinChange.CountedCoin("cog", 64)), STACK_64));
        assertEquals(2L, CoinStacks.stacksNeeded(List.of(new CoinChange.CountedCoin("cog", 65)), STACK_64));
        assertEquals(3L, CoinStacks.stacksNeeded(List.of(
                new CoinChange.CountedCoin("cog", 1),
                new CoinChange.CountedCoin("sprocket", 2),
                new CoinChange.CountedCoin("spur", 4)), STACK_64));
        assertEquals(0L, CoinStacks.stacksNeeded(List.of(), STACK_64));
    }

    @Test
    void stacksNeededRejectsAMissingStackSize() {
        assertThrows(IllegalArgumentException.class, () -> CoinStacks.stacksNeeded(
                List.of(new CoinChange.CountedCoin("cog", 1)),
                Map.of()));
    }

    @Test
    void maxFittingStopsAtTheSlotBudget() {
        assertEquals(0L, CoinStacks.maxFitting(LADDER, STACK_64, 0, 100));
        assertEquals(64L, CoinStacks.maxFitting(LADDER, STACK_64, 1, 100));
        assertEquals(96L, CoinStacks.maxFitting(LADDER, STACK_64, 2, 100));
        assertEquals(100L, CoinStacks.maxFitting(LADDER, STACK_64, 3, 100));
        assertEquals(100L, CoinStacks.maxFitting(LADDER, STACK_64, 36, 100));
    }

    @Test
    void maxFittingFillsSlotsWithTheLargestCoin() {
        long oneStack = 64L * 4096L;
        assertEquals(oneStack, CoinStacks.maxFitting(LADDER, STACK_64, 1, oneStack * 10));
        assertEquals(oneStack * 36, CoinStacks.maxFitting(LADDER, STACK_64, 36, oneStack * 100));
    }

    @Test
    void maxFittingPayoutFitsAndHasNoRemainder() {
        long max = CoinStacks.maxFitting(LADDER, STACK_64, 2, 10_000);
        CoinChange.Exchange exchange = CoinChange.exchange(LADDER, max);
        assertEquals(0L, exchange.remainderSpurs());
        assertTrue(CoinStacks.stacksNeeded(exchange.coins(), STACK_64) <= 2);
        assertEquals(max, CoinChange.toSpurs(LADDER, exchange.coins()));
    }
}
