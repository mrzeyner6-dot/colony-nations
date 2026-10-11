package com.mrzeyner.colonynations.trade;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * One nation's tariffs. A buyer with no row pays {@link #defaultTariff()} and is not embargoed.
 */
public record TradePolicy(int defaultTariff, Map<UUID, Duty> duties) {
    public record Duty(int tariff, boolean embargo) {
    }

    public TradePolicy {
        if (defaultTariff < 0) {
            defaultTariff = 0;
        }
        Map<UUID, Duty> copy = new LinkedHashMap<>();
        if (duties != null) {
            for (Map.Entry<UUID, Duty> entry : duties.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), entry.getValue());
                }
            }
        }
        duties = Map.copyOf(copy);
    }

    public static TradePolicy empty() {
        return new TradePolicy(0, Map.of());
    }

    public boolean isEmpty() {
        return defaultTariff == 0 && duties.isEmpty();
    }

    public Duty duty(@Nullable UUID buyer) {
        if (buyer == null) {
            return new Duty(defaultTariff, false);
        }
        Duty specific = duties.get(buyer);
        if (specific == null) {
            return new Duty(defaultTariff, false);
        }
        return specific;
    }

    public TradePolicy withDefault(int tariff, int maxTariff) {
        return new TradePolicy(TradeTerms.clamp(tariff, maxTariff), duties);
    }

    public TradePolicy bump(@Nullable UUID buyer, int delta, int maxTariff) {
        if (buyer == null || delta == 0) {
            return this;
        }
        Duty current = duty(buyer);
        int next = TradeTerms.clamp(current.tariff() + delta, maxTariff);
        return store(buyer, next, current.embargo(), maxTariff);
    }

    public TradePolicy toggleEmbargo(@Nullable UUID buyer, int maxTariff) {
        if (buyer == null) {
            return this;
        }
        Duty current = duty(buyer);
        return store(buyer, current.tariff(), !current.embargo(), maxTariff);
    }

    private TradePolicy store(UUID buyer, int tariff, boolean embargo, int maxTariff) {
        int clamped = TradeTerms.clamp(tariff, maxTariff);
        Map<UUID, Duty> next = new LinkedHashMap<>(duties);
        if (!embargo && clamped == defaultTariff) {
            next.remove(buyer);
        } else {
            next.put(buyer, new Duty(clamped, embargo));
        }
        return new TradePolicy(defaultTariff, next);
    }
}
