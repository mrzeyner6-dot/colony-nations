package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class TaxNoticesTest {
    @Test
    void firstOfflineDayIsASingleLine() {
        TaxNotices.Line line = TaxNotices.accrue(List.of(), 12L, 40L);
        assertEquals(TaxNotices.ONE_DAY, line.key());
        assertEquals(List.of("12", "40"), line.args());
    }

    @Test
    void laterDaysFoldIntoOneLine() {
        TaxNotices.Line first = TaxNotices.accrue(List.of(), 12L, 40L);
        TaxNotices.Line second = TaxNotices.accrue(List.of(first), 8L, 30L);
        assertEquals(TaxNotices.MANY_DAYS, second.key());
        assertEquals(List.of("20", "2", "30"), second.args());

        TaxNotices.Line third = TaxNotices.accrue(List.of(second), 5L, 35L);
        assertEquals(List.of("25", "3", "35"), third.args());
    }

    @Test
    void severalWaitingLinesAreSummedOnce() {
        TaxNotices.Line older = new TaxNotices.Line(TaxNotices.MANY_DAYS, List.of("10", "2", "10"));
        TaxNotices.Line newer = new TaxNotices.Line(TaxNotices.ONE_DAY, List.of("4", "14"));
        TaxNotices.Line folded = TaxNotices.accrue(List.of(older, newer), 1L, 99L);
        assertEquals(List.of("15", "4", "99"), folded.args());
    }

    @Test
    void brokenArgsDoNotDropToday() {
        TaxNotices.Line broken = new TaxNotices.Line(TaxNotices.MANY_DAYS, List.of("nope", "days"));
        TaxNotices.Line folded = TaxNotices.accrue(List.of(broken), 7L, 7L);
        assertEquals(TaxNotices.MANY_DAYS, folded.key());
        assertEquals(List.of("7", "2", "7"), folded.args());
    }

    @Test
    void spurSumClamps() {
        TaxNotices.Line waiting = new TaxNotices.Line(TaxNotices.ONE_DAY, List.of(Long.toString(Long.MAX_VALUE), "1"));
        TaxNotices.Line folded = TaxNotices.accrue(List.of(waiting), 1L, Long.MAX_VALUE);
        assertEquals(List.of(Long.toString(Long.MAX_VALUE), "2", Long.toString(Long.MAX_VALUE)), folded.args());
    }

    @Test
    void negativeCollectionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TaxNotices.accrue(List.of(), -1L, 0L));
    }
}
