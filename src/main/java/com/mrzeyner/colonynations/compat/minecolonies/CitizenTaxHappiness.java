package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.economy.TaxHappiness;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/**
 * Puts {@code colony_nations:tax} on every citizen of a nation's cities.
 * MineColonies only reloads modifier ids from its own fixed set, so this id is dropped on world load
 * and applied again here. Removing the mod leaves an unknown id, which that loader skips.
 */
public final class CitizenTaxHappiness {
    public static final String MODIFIER_ID = "colony_nations:tax";

    /**
     * Same order of magnitude as MineColonies' school modifier. A factor of exactly 1 is ignored by their average.
     */
    private static final double WEIGHT = 1.0;

    private CitizenTaxHappiness() {
    }

    public static void refreshAll(MinecraftServer server) {
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        for (Nation nation : data.registry().all()) {
            apply(nation);
        }
    }

    public static void apply(Nation nation) {
        if (nation == null) {
            return;
        }
        double factor = factor(nation.taxRate());
        for (CityRef city : nation.cities()) {
            apply(MineColoniesCities.findByCity(city), factor);
        }
    }

    public static void apply(IColony colony, double factor) {
        if (colony == null || colony.getCitizenManager() == null) {
            return;
        }
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            if (citizen == null || citizen.getCitizenHappinessHandler() == null) {
                continue;
            }
            citizen.getCitizenHappinessHandler().addModifier(
                    new StaticHappinessModifier(MODIFIER_ID, WEIGHT, new StaticHappinessSupplier(factor)));
        }
    }

    /**
     * Drops the tax modifier. Used when a city leaves the nation.
     * {@code addModifier} is the public call that clears the cached happiness; the id is then removed
     * because the handler has no remove method.
     */
    public static void clear(IColony colony) {
        if (colony == null || colony.getCitizenManager() == null) {
            return;
        }
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            clear(citizen);
        }
    }

    public static void appendInfo(CommandSourceStack source, Nation nation) {
        List<CityRef> cities = sorted(nation);
        if (cities.isEmpty()) {
            Messages.line(source, "command.colony_nations.info.happiness_empty");
            return;
        }
        for (CityRef city : cities) {
            IColony colony = MineColoniesCities.findByCity(city);
            Messages.line(source, "command.colony_nations.info.happiness", label(colony, city), format(colony));
        }
    }

    public static String format(IColony colony) {
        if (colony == null) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f", colony.getOverallHappiness());
    }

    private static void clear(ICitizenData citizen) {
        if (citizen == null || citizen.getCitizenHappinessHandler() == null) {
            return;
        }
        ICitizenHappinessHandler handler = citizen.getCitizenHappinessHandler();
        if (handler instanceof CitizenHappinessHandler concrete) {
            concrete.addModifier(new StaticHappinessModifier(MODIFIER_ID, WEIGHT, new StaticHappinessSupplier(1.0)));
            concrete.happinessFactors.remove(MODIFIER_ID);
            return;
        }
        handler.addModifier(new StaticHappinessModifier(MODIFIER_ID, WEIGHT, new StaticHappinessSupplier(1.0)));
    }

    private static double factor(int taxRate) {
        return TaxHappiness.multiplier(
                taxRate,
                Config.TAX_NEUTRAL_RATE.get(),
                Config.MAX_TAX_RATE.get(),
                Config.TAX_HAPPINESS_BONUS.get(),
                Config.TAX_HAPPINESS_PENALTY.get());
    }

    private static List<CityRef> sorted(Nation nation) {
        List<CityRef> cities = new ArrayList<>(nation.cities());
        cities.sort(Comparator.comparing(CityRef::dimension).thenComparingInt(CityRef::colonyId));
        return cities;
    }

    private static String label(IColony colony, CityRef city) {
        if (colony == null || colony.getName() == null || colony.getName().isBlank()) {
            return "#" + city.colonyId();
        }
        return colony.getName();
    }
}
