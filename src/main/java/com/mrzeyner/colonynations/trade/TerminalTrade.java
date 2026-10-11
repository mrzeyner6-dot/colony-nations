package com.mrzeyner.colonynations.trade;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Turns coins in the receiver into buyer credit and moves whole packs from stock to output.
 * A price of 0 or a pack of 0 means the terminal is not selling, so the coins stay where they are.
 */
public final class TerminalTrade {
    /**
     * Credit that is not yet tied to a nation. T26 replaces this with the nation that owns the train.
     */
    public static final UUID COUNTER = UUID.nameUUIDFromBytes(
            "colony_nations:terminal_counter".getBytes(StandardCharsets.UTF_8));

    public static final long MAX_PRICE = 1_000_000_000L;

    public record Exchange(boolean selling, long treasurySpurs, long credit, int packs) {
    }

    private TerminalTrade() {
    }

    /**
     * @param coinSpurs    spurs currently in the receiver; taken in full when {@code selling}
     * @param credit       this buyer's leftover from earlier visits
     * @param pricePerPack spur price of one pack; 0 means unset
     * @param packSize     items in one pack; 0 means unset
     * @param stock        matching items on the stock
     * @param outputRoom   how many of those items still fit in the output
     */
    public static Exchange exchange(
            long coinSpurs, long credit, long pricePerPack, int packSize, int stock, int outputRoom) {
        long kept = Math.max(0L, credit);
        if (pricePerPack <= 0L || packSize <= 0) {
            return new Exchange(false, 0L, kept, 0);
        }
        long paid = Math.max(0L, coinSpurs);
        long next = add(kept, paid);
        int packs = 0;
        int leftStock = Math.max(0, stock);
        int leftRoom = Math.max(0, outputRoom);
        while (next >= pricePerPack && leftStock >= packSize && leftRoom >= packSize) {
            next -= pricePerPack;
            leftStock -= packSize;
            leftRoom -= packSize;
            packs++;
        }
        return new Exchange(true, paid, next, packs);
    }

    public static long clampPrice(long price) {
        if (price < 0L) {
            return 0L;
        }
        return Math.min(price, MAX_PRICE);
    }

    public static int clampPack(int pack, int maxStack) {
        int max = Math.max(1, maxStack);
        if (pack < 1) {
            return 1;
        }
        return Math.min(pack, max);
    }

    private static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
