package com.mrzeyner.colonynations.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between a spur total and coin counts. Denomination values come from the caller
 * (Numismatics {@code Coin} on the Minecraft side) so this class stays free of mod types.
 */
public final class CoinChange {
    public record Denomination(String id, long spurValue) {
        public Denomination {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("denomination id");
            }
            if (spurValue <= 0) {
                throw new IllegalArgumentException("denomination value");
            }
        }
    }

    public record CountedCoin(String id, long count) {
        public CountedCoin {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("coin id");
            }
            if (count < 0) {
                throw new IllegalArgumentException("coin count");
            }
        }
    }

    /**
     * @param coins largest denominations first, zero counts omitted
     * @param remainderSpurs spurs that no supplied denomination can make
     */
    public record Exchange(List<CountedCoin> coins, long remainderSpurs) {
        public Exchange {
            coins = List.copyOf(coins);
            if (remainderSpurs < 0) {
                throw new IllegalArgumentException("remainder");
            }
        }
    }

    private CoinChange() {
    }

    public static long toSpurs(List<Denomination> denominations, List<CountedCoin> coins) {
        Map<String, Long> values = valuesById(denominations);
        long total = 0L;
        for (CountedCoin coin : coins) {
            Long value = values.get(coin.id());
            if (value == null) {
                throw new IllegalArgumentException("unknown coin " + coin.id());
            }
            total = Math.addExact(total, Math.multiplyExact(value, coin.count()));
        }
        return total;
    }

    /**
     * Pays {@code spurs} using the largest denominations first. A denomination of 1 spur
     * makes the exchange exact.
     */
    public static Exchange exchange(List<Denomination> denominations, long spurs) {
        if (spurs < 0) {
            throw new IllegalArgumentException("spurs");
        }
        List<Denomination> ordered = new ArrayList<>(denominations);
        ordered.sort(Comparator.comparingLong(Denomination::spurValue).reversed());
        List<CountedCoin> coins = new ArrayList<>();
        long remaining = spurs;
        for (Denomination denomination : ordered) {
            if (remaining < denomination.spurValue()) {
                continue;
            }
            long count = remaining / denomination.spurValue();
            remaining -= count * denomination.spurValue();
            if (count > 0) {
                coins.add(new CountedCoin(denomination.id(), count));
            }
        }
        return new Exchange(coins, remaining);
    }

    private static Map<String, Long> valuesById(List<Denomination> denominations) {
        Map<String, Long> values = new HashMap<>();
        for (Denomination denomination : denominations) {
            values.putIfAbsent(denomination.id(), denomination.spurValue());
        }
        return values;
    }
}
