package com.mrzeyner.colonynations.trade;

/**
 * Packs and spurs taken in for one nation, split so a sale on the new day does not erase the day
 * the daily notice has not reported yet.
 */
public final class TradeSales {
    public record Day(long day, int packs, long spurs) {
        public static Day unset() {
            return new Day(Long.MIN_VALUE, 0, 0L);
        }

        public Day add(int morePacks, long moreSpurs) {
            int packsNext = packs;
            if (morePacks > 0) {
                packsNext = packs > Integer.MAX_VALUE - morePacks ? Integer.MAX_VALUE : packs + morePacks;
            }
            long spursNext = spurs;
            if (moreSpurs > 0L) {
                try {
                    spursNext = Math.addExact(spurs, moreSpurs);
                } catch (ArithmeticException overflow) {
                    spursNext = Long.MAX_VALUE;
                }
            }
            return new Day(day, packsNext, spursNext);
        }
    }

    public record Book(Day open, Day closed) {
        public Book {
            open = open == null ? Day.unset() : open;
            closed = closed == null ? Day.unset() : closed;
        }

        public static Book empty() {
            return new Book(Day.unset(), Day.unset());
        }

        public boolean isEmpty() {
            return open.day() == Long.MIN_VALUE && closed.day() == Long.MIN_VALUE;
        }
    }

    /**
     * @param report packs and spurs to announce, or null when the day had no pack sales
     */
    public record Flush(Book book, Day report) {
    }

    private TradeSales() {
    }

    public static Book record(Book book, long today, int packs, long spurs) {
        Book current = book == null ? Book.empty() : book;
        Day open = current.open();
        Day closed = current.closed();
        if (open.day() != Long.MIN_VALUE && open.day() != today) {
            if (closed.day() == Long.MIN_VALUE) {
                closed = open;
            }
            open = new Day(today, 0, 0L);
        }
        if (open.day() == Long.MIN_VALUE) {
            open = new Day(today, 0, 0L);
        }
        return new Book(open.add(packs, spurs), closed);
    }

    /**
     * Drops every day before {@code today}. Pack sales are summed into one report.
     */
    public static Flush flush(Book book, long today) {
        Book current = book == null ? Book.empty() : book;
        Day open = current.open();
        Day closed = current.closed();
        int packs = 0;
        long spurs = 0L;
        boolean stale = false;
        if (closed.day() != Long.MIN_VALUE && closed.day() < today) {
            packs = addPacks(packs, closed.packs());
            spurs = addSpurs(spurs, closed.spurs());
            closed = Day.unset();
            stale = true;
        }
        if (open.day() != Long.MIN_VALUE && open.day() < today) {
            packs = addPacks(packs, open.packs());
            spurs = addSpurs(spurs, open.spurs());
            open = Day.unset();
            stale = true;
        }
        Day report = stale && packs > 0 ? new Day(today - 1, packs, spurs) : null;
        return new Flush(new Book(open, closed), report);
    }

    private static int addPacks(int left, int right) {
        if (right <= 0) {
            return left;
        }
        return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }

    private static long addSpurs(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
