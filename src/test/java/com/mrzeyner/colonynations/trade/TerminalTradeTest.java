package com.mrzeyner.colonynations.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerminalTradeTest {
    @Test
    void unsetPriceLeavesCoinsAndCreditAlone() {
        TerminalTrade.Exchange exchange = TerminalTrade.exchange(100, 15, 0, 4, 64, 64);
        assertFalse(exchange.selling());
        assertEquals(0, exchange.treasurySpurs());
        assertEquals(15, exchange.credit());
        assertEquals(0, exchange.packs());
    }

    @Test
    void shortCreditBuysNothingAndKeepsTheRemainder() {
        TerminalTrade.Exchange exchange = TerminalTrade.exchange(10, 5, 25, 4, 64, 64);
        assertTrue(exchange.selling());
        assertEquals(10, exchange.treasurySpurs());
        assertEquals(15, exchange.credit());
        assertEquals(0, exchange.packs());
    }

    @Test
    void shortStockAndFullOutputBuyNothing() {
        assertEquals(0, TerminalTrade.exchange(100, 0, 10, 8, 5, 64).packs());
        assertEquals(0, TerminalTrade.exchange(100, 0, 10, 8, 64, 4).packs());
        assertEquals(100, TerminalTrade.exchange(100, 0, 10, 8, 5, 4).credit());
    }

    @Test
    void leftoverCreditIsSpentOnTheNextVisit() {
        TerminalTrade.Exchange first = TerminalTrade.exchange(50, 0, 40, 8, 64, 64);
        assertEquals(1, first.packs());
        assertEquals(10, first.credit());
        TerminalTrade.Exchange second = TerminalTrade.exchange(30, first.credit(), 40, 8, 56, 56);
        assertEquals(1, second.packs());
        assertEquals(0, second.credit());
        assertEquals(30, second.treasurySpurs());
    }

    @Test
    void onePaymentBuysSeveralPacks() {
        TerminalTrade.Exchange exchange = TerminalTrade.exchange(100, 0, 30, 4, 64, 64);
        assertEquals(3, exchange.packs());
        assertEquals(10, exchange.credit());
    }

    @Test
    void ledgerKeepsYesterdayOnlyAcrossOneDay() {
        TerminalLedger day = TerminalLedger.unset().onDay(4).record(2, 80);
        TerminalLedger next = day.onDay(5);
        assertEquals(0, next.packsToday());
        assertEquals(2, next.packsYesterday());
        assertEquals(80, next.spursYesterday());
        TerminalLedger skipped = day.onDay(7);
        assertEquals(0, skipped.packsYesterday());
        assertEquals(0, skipped.spursToday());
    }
}
