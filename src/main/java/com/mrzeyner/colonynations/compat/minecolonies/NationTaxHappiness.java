package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.Locale;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.economy.Tax;
import com.mrzeyner.colonynations.economy.TaxHappiness;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Puts {@link #MODIFIER_ID} on citizens of a nation's cities.
 * MineColonies writes every modifier into citizen NBT, but {@code CitizenHappinessHandler.read}
 * restores only its built-in ids and {@code HappinessConstants.VALID_HAPPINESS_MODIFIERS}.
 * An unknown id is skipped, so removing this mod does not crash, and a restart drops the modifier
 * until {@link #refresh(MinecraftServer)} runs again.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class NationTaxHappiness {
    public static final String MODIFIER_ID = "colony_nations:tax";

    private NationTaxHappiness() {
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        refresh(event.getServer());
    }

    public static void refresh(MinecraftServer server) {
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        for (Nation nation : data.registry().all()) {
            apply(nation);
        }
    }

    public static void refresh(MinecraftServer server, Nation nation) {
        if (nation == null || NationSavedData.get(server).unreadable()) {
            return;
        }
        apply(nation);
    }

    /**
     * Drops the tax modifier. The API has no remove, and a factor of exactly 1 is ignored by
     * MineColonies but would still leave the id in place. A neutral modifier invalidates the
     * cached happiness, then the id is removed from the public map.
     */
    public static void clear(IColony colony) {
        if (colony == null) {
            return;
        }
        for (var citizen : colony.getCitizenManager().getCitizens()) {
            var handler = citizen.getCitizenHappinessHandler();
            if (handler.getModifier(MODIFIER_ID) == null) {
                continue;
            }
            handler.addModifier(new StaticHappinessModifier(MODIFIER_ID, 1.0D, new StaticHappinessSupplier(1.0D)));
            if (handler instanceof CitizenHappinessHandler concrete) {
                concrete.happinessFactors.remove(MODIFIER_ID);
            }
        }
    }

    public static String text(CityRef city) {
        IColony colony = MineColoniesCities.findByCity(city);
        if (colony == null) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f", colony.getOverallHappiness());
    }

    private static void apply(Nation nation) {
        int maxRate = Config.MAX_TAX_RATE.get();
        int rate = Tax.bounded(nation.taxRate(), maxRate);
        double factor = TaxHappiness.factor(
                rate,
                Config.TAX_NEUTRAL_RATE.get(),
                maxRate,
                Config.TAX_HAPPINESS_BONUS.get(),
                Config.TAX_HAPPINESS_PENALTY.get());
        double weight = Config.TAX_HAPPINESS_WEIGHT.get();
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            if (colony == null) {
                continue;
            }
            for (var citizen : colony.getCitizenManager().getCitizens()) {
                citizen.getCitizenHappinessHandler().addModifier(
                        new StaticHappinessModifier(MODIFIER_ID, weight, new StaticHappinessSupplier(factor)));
            }
        }
    }
}
