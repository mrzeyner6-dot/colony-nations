package com.mrzeyner.colonynations.nation;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A nation. Immutable; the registry replaces instances when state changes.
 */
public final class Nation {
    public static final int MIN_NAME_LENGTH = 3;
    public static final int MAX_NAME_LENGTH = 24;

    /**
     * Spurs per adult per day for a nation that has no stored rate yet (save versions before tax).
     * New nations use the config default, which starts at this same number.
     */
    public static final int DEFAULT_TAX_RATE = 2;

    private static final Comparator<CityRef> CAPITAL_ORDER =
            Comparator.comparingInt(CityRef::colonyId).thenComparing(CityRef::dimension);

    private final UUID id;
    private final String name;
    private final UUID owner;
    private final int color;
    private final CityRef capital;
    private final Set<CityRef> cities;
    private final long treasury;
    private final long createdGameTime;
    private final int taxRate;
    private final Long lastTaxChangeDay;

    public Nation(
            UUID id,
            String name,
            UUID owner,
            int color,
            CityRef capital,
            Collection<CityRef> cities,
            long treasury,
            long createdGameTime,
            int taxRate,
            Long lastTaxChangeDay) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.color = color;
        this.cities = Set.copyOf(cities);
        if (capital != null && !this.cities.contains(capital)) {
            throw new IllegalArgumentException("capital is not one of the nation's colonies");
        }
        this.capital = capital;
        this.treasury = treasury;
        this.createdGameTime = createdGameTime;
        this.taxRate = taxRate;
        this.lastTaxChangeDay = lastTaxChangeDay;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID owner() {
        return owner;
    }

    public int color() {
        return color;
    }

    /**
     * Capital city, or null when the nation has no cities yet.
     */
    public CityRef capital() {
        return capital;
    }

    public Set<CityRef> cities() {
        return cities;
    }

    public long treasury() {
        return treasury;
    }

    public long createdGameTime() {
        return createdGameTime;
    }

    /**
     * Spurs taken from each adult citizen per day.
     */
    public int taxRate() {
        return taxRate;
    }

    /**
     * Game day of the last rate change, or null when the rate has never been changed.
     */
    public Long lastTaxChangeDay() {
        return lastTaxChangeDay;
    }

    public Nation withName(String name) {
        return copy(name, color, capital, cities, treasury, taxRate, lastTaxChangeDay);
    }

    public Nation withColor(int color) {
        return copy(name, color, capital, cities, treasury, taxRate, lastTaxChangeDay);
    }

    public Nation withTreasury(long treasury) {
        return copy(name, color, capital, cities, treasury, taxRate, lastTaxChangeDay);
    }

    public Nation withTax(int taxRate, Long lastTaxChangeDay) {
        return copy(name, color, capital, cities, treasury, taxRate, lastTaxChangeDay);
    }

    public Nation withCapital(CityRef city) {
        if (!cities.contains(city)) {
            throw new NationException(NationException.Reason.UNKNOWN_COLONY);
        }
        return copy(name, color, city, cities, treasury, taxRate, lastTaxChangeDay);
    }

    /**
     * Adds a city. The first city becomes the capital.
     */
    public Nation addCity(CityRef city) {
        Objects.requireNonNull(city, "city");
        if (cities.contains(city)) {
            return this;
        }
        Set<CityRef> next = new LinkedHashSet<>(cities);
        next.add(city);
        CityRef nextCapital = capital == null ? city : capital;
        return copy(name, color, nextCapital, next, treasury, taxRate, lastTaxChangeDay);
    }

    /**
     * Removes a city. If it was the capital, the city with the smallest colony id becomes the capital.
     * Equal ids are ordered by dimension id.
     */
    public Nation removeCity(CityRef city) {
        if (!cities.contains(city)) {
            throw new NationException(NationException.Reason.UNKNOWN_COLONY);
        }
        Set<CityRef> next = new LinkedHashSet<>(cities);
        next.remove(city);
        CityRef nextCapital = capital;
        if (Objects.equals(nextCapital, city)) {
            nextCapital = next.stream().min(CAPITAL_ORDER).orElse(null);
        }
        return copy(name, color, nextCapital, next, treasury, taxRate, lastTaxChangeDay);
    }

    private Nation copy(
            String name,
            int color,
            CityRef capital,
            Collection<CityRef> cities,
            long treasury,
            int taxRate,
            Long lastTaxChangeDay) {
        return new Nation(id, name, owner, color, capital, cities, treasury, createdGameTime, taxRate, lastTaxChangeDay);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Nation nation)) {
            return false;
        }
        return color == nation.color
                && treasury == nation.treasury
                && createdGameTime == nation.createdGameTime
                && taxRate == nation.taxRate
                && id.equals(nation.id)
                && name.equals(nation.name)
                && owner.equals(nation.owner)
                && Objects.equals(capital, nation.capital)
                && cities.equals(nation.cities)
                && Objects.equals(lastTaxChangeDay, nation.lastTaxChangeDay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, owner, color, capital, cities, treasury, createdGameTime, taxRate, lastTaxChangeDay);
    }
}
