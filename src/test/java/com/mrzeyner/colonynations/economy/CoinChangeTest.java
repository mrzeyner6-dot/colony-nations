package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CoinChangeTest {
    /**
     * Same ladder as Numismatics {@code Coin} in 1.1.0. The production path reads the enum;
     * these numbers only describe the algorithm under test.
     */
    private static final List<CoinChange.Denomination> LADDER = List.of(
            new CoinChange.Denomination("spur", 1),
            new CoinChange.Denomination("bevel", 8),
            new CoinChange.Denomination("sprocket", 16),
            new CoinChange.Denomination("cog", 64),
            new CoinChange.Denomination("crown", 512),
            new CoinChange.Denomination("sun", 4096));

    @Test
    void toSpursMultipliesCountsByDenomination() {
        long spurs = CoinChange.toSpurs(LADDER, List.of(
                new CoinChange.CountedCoin("cog", 5),
                new CoinChange.CountedCoin("spur", 3)));
        assertEquals(323L, spurs);
    }

    @Test
    void toSpursRejectsUnknownCoinAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> CoinChange.toSpurs(LADDER, List.of(
                new CoinChange.CountedCoin("token", 1))));
        assertThrows(ArithmeticException.class, () -> CoinChange.toSpurs(
                List.of(new CoinChange.Denomination("sun", Long.MAX_VALUE)),
                List.of(new CoinChange.CountedCoin("sun", 2))));
    }

    @Test
    void exchangeUsesLargestDenominations() {
        CoinChange.Exchange exchange = CoinChange.exchange(LADDER, 100);
        assertEquals(List.of(
                new CoinChange.CountedCoin("cog", 1),
                new CoinChange.CountedCoin("sprocket", 2),
                new CoinChange.CountedCoin("spur", 4)), exchange.coins());
        assertEquals(0L, exchange.remainderSpurs());
        assertEquals(100L, CoinChange.toSpurs(LADDER, exchange.coins()));
    }

    @Test
    void exchangeIsExactAcrossTheLadder() {
        long amount = 4096 + 512 + 64 + 16 + 8 + 1;
        CoinChange.Exchange exchange = CoinChange.exchange(LADDER, amount);
        assertEquals(List.of(
                new CoinChange.CountedCoin("sun", 1),
                new CoinChange.CountedCoin("crown", 1),
                new CoinChange.CountedCoin("cog", 1),
                new CoinChange.CountedCoin("sprocket", 1),
                new CoinChange.CountedCoin("bevel", 1),
                new CoinChange.CountedCoin("spur", 1)), exchange.coins());
        assertEquals(amount, CoinChange.toSpurs(LADDER, exchange.coins()));
    }

    @Test
    void exchangeLeavesRemainderWhenNoUnitCoinExists() {
        List<CoinChange.Denomination> coarse = List.of(
                new CoinChange.Denomination("cog", 64),
                new CoinChange.Denomination("bevel", 8));
        CoinChange.Exchange exchange = CoinChange.exchange(coarse, 10);
        assertEquals(List.of(new CoinChange.CountedCoin("bevel", 1)), exchange.coins());
        assertEquals(2L, exchange.remainderSpurs());
    }

    @Test
    void exchangeOfZeroIsEmpty() {
        CoinChange.Exchange exchange = CoinChange.exchange(LADDER, 0);
        assertTrue(exchange.coins().isEmpty());
        assertEquals(0L, exchange.remainderSpurs());
    }

    @Test
    void exchangeRejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> CoinChange.exchange(LADDER, -1));
    }

    @Test
    void denominationRejectsBlankIdAndNonPositiveValue() {
        assertThrows(IllegalArgumentException.class, () -> new CoinChange.Denomination(" ", 1));
        assertThrows(IllegalArgumentException.class, () -> new CoinChange.Denomination("spur", 0));
    }
}
