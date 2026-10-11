package com.mrzeyner.colonynations.data;

import java.util.Objects;
import java.util.UUID;

import com.mrzeyner.colonynations.nation.CityRef;

/**
 * A seceding city's request to join another nation. The deadline is a game day number.
 * Stored beside notices, outside the versioned nation payload.
 */
public record SecessionOffer(
        UUID targetNation,
        UUID targetOwner,
        CityRef city,
        UUID fromNation,
        String cityName,
        String fromNationName,
        String targetNationName,
        long expiresOnDay) {
    public SecessionOffer {
        Objects.requireNonNull(targetNation, "targetNation");
        Objects.requireNonNull(targetOwner, "targetOwner");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(fromNation, "fromNation");
        cityName = cityName == null ? "" : cityName;
        fromNationName = fromNationName == null ? "" : fromNationName;
        targetNationName = targetNationName == null ? "" : targetNationName;
    }
}
