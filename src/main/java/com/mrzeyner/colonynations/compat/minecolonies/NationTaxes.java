package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.List;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.data.PendingNotice;
import com.mrzeyner.colonynations.economy.PopulationTax;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Collects one day of population tax into each nation's treasury.
 */
public final class NationTaxes {
    static final String AWAY_KEY = "command.colony_nations.tax.collected.away";

    private NationTaxes() {
    }

    public static void collect(MinecraftServer server) {
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        boolean dirty = false;
        for (Nation nation : List.copyOf(data.registry().all())) {
            List<Integer> adults = new ArrayList<>();
            for (CityRef city : nation.cities()) {
                // TODO(T-war): tax from an occupied city is paid to the occupier, not this nation.
                adults.add(adults(MineColoniesCities.findByCity(city)));
            }
            PopulationTax.Collection collection = PopulationTax.sum(adults, nation.taxRate());
            if (collection.income() <= 0L) {
                continue;
            }
            PopulationTax.Deposit deposit = PopulationTax.deposit(nation.treasury(), collection.income());
            if (collection.overflow() || deposit.overflow()) {
                ColonyNations.LOGGER.warn(
                        "Tax for nation {} overflowed and was clamped to {}",
                        nation.name(),
                        Long.MAX_VALUE);
            }
            data.registry().setTreasury(nation.id(), deposit.treasury());
            dirty = true;
            tell(server, data, nation, collection.income(), deposit.treasury());
        }
        if (dirty) {
            data.setDirty();
        }
    }

    static int adults(IColony colony) {
        if (colony == null || colony.getCitizenManager() == null) {
            return 0;
        }
        int count = 0;
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            if (citizen != null && !citizen.isChild()) {
                count++;
            }
        }
        return count;
    }

    private static void tell(MinecraftServer server, NationSavedData data, Nation nation, long income, long treasury) {
        ServerPlayer owner = server.getPlayerList().getPlayer(nation.owner());
        if (owner != null) {
            Messages.info(owner.createCommandSourceStack(), "command.colony_nations.tax.collected", income, treasury);
            return;
        }
        PopulationTax.Accrual prior = null;
        for (PendingNotice notice : data.notices()) {
            if (!notice.owner().equals(nation.owner()) || !AWAY_KEY.equals(notice.key()) || notice.args().size() < 2) {
                continue;
            }
            prior = PopulationTax.parseAccrual(notice.args().get(0), notice.args().get(1));
            break;
        }
        PopulationTax.Accrual next = PopulationTax.accrue(prior, income);
        data.replaceNotice(new PendingNotice(
                nation.owner(),
                false,
                AWAY_KEY,
                List.of(Long.toString(next.spurs()), Integer.toString(next.days()), Long.toString(treasury))));
    }
}
