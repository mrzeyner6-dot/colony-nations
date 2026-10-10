package com.mrzeyner.colonynations.compat.numismatics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mrzeyner.colonynations.economy.CoinChange;

import dev.ithundxr.createnumismatics.content.backend.Coin;
import dev.ithundxr.createnumismatics.content.coins.CoinItem;

import net.minecraft.world.item.ItemStack;

/**
 * Reads spur values from the Numismatics {@link Coin} enum and turns a payout back into item stacks.
 */
public final class NumismaticsCoins {
    private NumismaticsCoins() {
    }

    public static List<CoinChange.Denomination> denominations() {
        List<CoinChange.Denomination> denominations = new ArrayList<>();
        for (Coin coin : Coin.values()) {
            denominations.add(new CoinChange.Denomination(coin.getName(), coin.value));
        }
        return List.copyOf(denominations);
    }

    public static boolean isCoin(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof CoinItem;
    }

    public static long stackSpurs(ItemStack stack) {
        if (!isCoin(stack)) {
            return 0L;
        }
        Coin coin = ((CoinItem) stack.getItem()).coin;
        return Math.multiplyExact((long) coin.value, stack.getCount());
    }

    public static List<ItemStack> stacksFor(List<CoinChange.CountedCoin> coins) {
        Map<String, Coin> byName = new HashMap<>();
        for (Coin coin : Coin.values()) {
            byName.put(coin.getName(), coin);
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (CoinChange.CountedCoin counted : coins) {
            Coin coin = byName.get(counted.id());
            if (coin == null) {
                throw new IllegalArgumentException("unknown coin " + counted.id());
            }
            if (counted.count() == 0) {
                continue;
            }
            int maxStack = Math.max(1, coin.asStack(1).getMaxStackSize());
            long remaining = counted.count();
            while (remaining > 0) {
                int size = (int) Math.min(maxStack, remaining);
                stacks.add(coin.asStack(size));
                remaining -= size;
            }
        }
        return stacks;
    }
}
