package com.mrzeyner.colonynations.trade;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Picks the Create station a terminal sells to. Distance is Chebyshev, in blocks.
 * Among stations that have a stopped train, the nearest wins; a closer empty station does not.
 */
public final class TerminalStation {
    public enum Kind {
        NONE,
        OWN,
        NATION,
        PRIVATE
    }

    /**
     * One station block the scanner saw. {@code ownerNation} is the nation of the train owner, when they have one.
     */
    public record Sighting(
            int x,
            int y,
            int z,
            String stationName,
            boolean trainPresent,
            String trainName,
            @Nullable UUID owner,
            @Nullable UUID ownerNation) {
        public Sighting {
            stationName = stationName == null ? "" : stationName;
            trainName = trainName == null ? "" : trainName;
        }
    }

    /**
     * @param station     a station block is inside the radius
     * @param train       that station, or the chosen one, has a stopped train
     * @param buyer       nation id, player id, or {@link TerminalTrade#UNOWNED}; null when the terminal must not sell
     * @param ownerNation nation of the train owner, including when the train belongs to the seller and does not buy
     */
    public record Binding(
            boolean station,
            String stationName,
            boolean train,
            String trainName,
            Kind kind,
            @Nullable UUID buyer,
            @Nullable UUID ownerNation) {
        public Binding {
            stationName = stationName == null ? "" : stationName;
            trainName = trainName == null ? "" : trainName;
            if (kind == null) {
                kind = Kind.NONE;
            }
        }

        public static Binding none() {
            return new Binding(false, "", false, "", Kind.NONE, null, null);
        }

        public boolean trades() {
            return (kind == Kind.NATION || kind == Kind.PRIVATE) && buyer != null;
        }

        public String token() {
            return station + "\0" + stationName + "\0" + train + "\0" + trainName + "\0" + kind + "\0" + buyer + "\0" + ownerNation;
        }
    }

    private TerminalStation() {
    }

    public static Binding bind(int originX, int originY, int originZ, @Nullable List<Sighting> sightings, int radius, @Nullable UUID sellerNation) {
        int limit = Math.max(0, radius);
        Sighting empty = null;
        long emptyCheb = Long.MAX_VALUE;
        long emptyEuclid = Long.MAX_VALUE;
        Sighting stopped = null;
        long stoppedCheb = Long.MAX_VALUE;
        long stoppedEuclid = Long.MAX_VALUE;
        if (sightings != null) {
            for (Sighting sighting : sightings) {
                if (sighting == null) {
                    continue;
                }
                long dx = (long) sighting.x() - originX;
                long dy = (long) sighting.y() - originY;
                long dz = (long) sighting.z() - originZ;
                long cheb = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                if (cheb > limit) {
                    continue;
                }
                long euclid = dx * dx + dy * dy + dz * dz;
                if (sighting.trainPresent()) {
                    if (nearer(sighting, cheb, euclid, stopped, stoppedCheb, stoppedEuclid)) {
                        stopped = sighting;
                        stoppedCheb = cheb;
                        stoppedEuclid = euclid;
                    }
                } else if (nearer(sighting, cheb, euclid, empty, emptyCheb, emptyEuclid)) {
                    empty = sighting;
                    emptyCheb = cheb;
                    emptyEuclid = euclid;
                }
            }
        }
        if (stopped != null) {
            return fromTrain(stopped, sellerNation);
        }
        if (empty != null) {
            return new Binding(true, empty.stationName(), false, "", Kind.NONE, null, null);
        }
        return Binding.none();
    }

    private static Binding fromTrain(Sighting sighting, @Nullable UUID sellerNation) {
        UUID nation = sighting.ownerNation();
        if (nation != null && nation.equals(sellerNation)) {
            return new Binding(true, sighting.stationName(), true, sighting.trainName(), Kind.OWN, null, nation);
        }
        if (nation != null) {
            return new Binding(true, sighting.stationName(), true, sighting.trainName(), Kind.NATION, nation, nation);
        }
        UUID buyer = sighting.owner() != null ? sighting.owner() : TerminalTrade.UNOWNED;
        return new Binding(true, sighting.stationName(), true, sighting.trainName(), Kind.PRIVATE, buyer, null);
    }

    private static boolean nearer(Sighting next, long cheb, long euclid, @Nullable Sighting current, long currentCheb, long currentEuclid) {
        if (current == null) {
            return true;
        }
        if (cheb != currentCheb) {
            return cheb < currentCheb;
        }
        if (euclid != currentEuclid) {
            return euclid < currentEuclid;
        }
        if (next.x() != current.x()) {
            return next.x() < current.x();
        }
        if (next.y() != current.y()) {
            return next.y() < current.y();
        }
        return next.z() < current.z();
    }
}
