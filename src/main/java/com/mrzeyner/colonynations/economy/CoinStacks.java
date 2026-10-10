package com.mrzeyner.colonynations.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * How many item stacks a coin payout needs, and the largest spur total that fits in a slot budget.
 * Stack sizes come from the caller (the Numismatics coin item on the Minecraft side).
 */
public final class CoinStacks {
    private CoinStacks() {
    }

    public static long stacksNeeded(List<CoinChange.CountedCoin> coins, Map<String, Integer> maxStackById) {
        long stacks = 0L;
        for (CoinChange.CountedCoin coin : coins) {
            if (coin.count() == 0L) {
                continue;
            }
            int maxStack = stackSize(maxStackById, coin.id());
            stacks += ceilDiv(coin.count(), maxStack);
        }
        return stacks;
    }

    /**
     * Largest spur amount, at most {@code treasury}, whose greedy payout fits in {@code freeSlots}
     * and has no remainder.
     */
    public static long maxFitting(
            List<CoinChange.Denomination> denominations,
            Map<String, Integer> maxStackById,
            int freeSlots,
            long treasury) {
        if (freeSlots <= 0 || treasury <= 0 || denominations.isEmpty()) {
            return 0L;
        }
        List<CoinChange.Denomination> ordered = new ArrayList<>(denominations);
        ordered.sort(Comparator.comparingLong(CoinChange.Denomination::spurValue).reversed());
        return best(ordered, maxStackById, 0, freeSlots, treasury);
    }

    private static long best(
            List<CoinChange.Denomination> coins,
            Map<String, Integer> maxStackById,
            int index,
            int slots,
            long cap) {
        if (cap <= 0L || slots <= 0 || index >= coins.size()) {
            return 0L;
        }
        CoinChange.Exchange whole = CoinChange.exchange(coins.subList(index, coins.size()), cap);
        if (whole.remainderSpurs() == 0L && stacksNeeded(whole.coins(), maxStackById) <= slots) {
            return cap;
        }
        CoinChange.Denomination coin = coins.get(index);
        long maxCount = cap / coin.spurValue();
        if (maxCount == 0L) {
            return best(coins, maxStackById, index + 1, slots, cap);
        }
        int stackSize = stackSize(maxStackById, coin.id());
        int maxStacks = (int) Math.min(slots, ceilDiv(maxCount, stackSize));
        long best = 0L;
        for (int stacks = maxStacks; stacks >= 0; stacks--) {
            long count = Math.min(maxCount, (long) stacks * stackSize);
            long used = count == 0L ? 0L : ceilDiv(count, stackSize);
            if (stacks > 0 && used != stacks) {
                continue;
            }
            long spent = count * coin.spurValue();
            long restCap = cap - spent;
            int slotsLeft = slots - (int) used;
            if (index + 1 < coins.size()) {
                restCap = Math.min(restCap, coin.spurValue() - 1L);
            } else {
                restCap = 0L;
            }
            long rest = best(coins, maxStackById, index + 1, slotsLeft, restCap);
            if (spent + rest > best) {
                best = spent + rest;
            }
        }
        return best;
    }

    private static int stackSize(Map<String, Integer> maxStackById, String id) {
        Integer maxStack = maxStackById.get(id);
        if (maxStack == null || maxStack <= 0) {
            throw new IllegalArgumentException("stack size for " + id);
        }
        return maxStack;
    }

    private static long ceilDiv(long count, int maxStack) {
        return (count - 1L) / maxStack + 1L;
    }
}
