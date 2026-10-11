package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.List;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.data.PendingNotice;
import com.mrzeyner.colonynations.economy.Tax;
import com.mrzeyner.colonynations.economy.TaxNotices;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Collects one day of population tax into each nation's treasury.
 */
public final class NationTaxes {
    private NationTaxes() {
    }

    public static void collect(MinecraftServer server) {
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        int maxRate = Config.MAX_TAX_RATE.get();
        boolean dirty = false;
        List<PendingNotice> notices = new ArrayList<>(data.notices());
        boolean noticesChanged = false;
        for (Nation nation : List.copyOf(data.registry().all())) {
            if (nation.cities().isEmpty()) {
                continue;
            }
            int rate = Tax.bounded(nation.taxRate(), maxRate);
            List<Integer> adults = new ArrayList<>();
            for (CityRef city : nation.cities()) {
                IColony colony = MineColoniesCities.findByCity(city);
                // TODO(T-war): tax from an occupied city is paid to the occupier.
                adults.add(colony == null ? 0 : ColonyPopulation.adults(colony));
            }
            Tax.Added bill = Tax.bill(adults, rate);
            if (bill.overflow()) {
                ColonyNations.LOGGER.warn(
                        "Tax income for nation {} did not fit in a long; clamped to {}",
                        nation.name(),
                        Long.MAX_VALUE);
            }
            long treasury = nation.treasury();
            if (bill.value() > 0L) {
                Tax.Added credited = Tax.add(treasury, bill.value());
                if (credited.overflow()) {
                    ColonyNations.LOGGER.warn(
                            "Treasury of nation {} overflowed while collecting taxes; clamped to {}",
                            nation.name(),
                            Long.MAX_VALUE);
                }
                treasury = credited.value();
                data.registry().setTreasury(nation.id(), treasury);
                dirty = true;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(nation.owner());
            if (owner != null) {
                Messages.info(owner.createCommandSourceStack(), TaxNotices.ONE_DAY, bill.value(), treasury);
                continue;
            }
            notices = fold(notices, nation, bill.value(), treasury);
            noticesChanged = true;
        }
        if (noticesChanged) {
            data.replaceNotices(notices);
        } else if (dirty) {
            data.setDirty();
        }
    }

    private static List<PendingNotice> fold(List<PendingNotice> notices, Nation nation, long collected, long treasury) {
        List<TaxNotices.Line> previous = new ArrayList<>();
        List<PendingNotice> kept = new ArrayList<>();
        for (PendingNotice notice : notices) {
            if (notice.owner().equals(nation.owner()) && TaxNotices.isTax(notice.key())) {
                previous.add(new TaxNotices.Line(notice.key(), notice.args()));
            } else {
                kept.add(notice);
            }
        }
        TaxNotices.Line line = TaxNotices.accrue(previous, collected, treasury);
        kept.add(new PendingNotice(nation.owner(), false, line.key(), line.args()));
        return kept;
    }
}
