package com.mrzeyner.colonynations.trade;

/**
 * Which internal inventory an automated insert belongs in.
 * Coins never share the stock, even when the good for sale is itself a coin.
 */
public final class TerminalInsert {
    public enum Target {
        COIN,
        STOCK,
        REJECT
    }

    private TerminalInsert() {
    }

    public static Target route(boolean coin, boolean matchesGoods) {
        if (coin) {
            return Target.COIN;
        }
        return matchesGoods ? Target.STOCK : Target.REJECT;
    }
}
