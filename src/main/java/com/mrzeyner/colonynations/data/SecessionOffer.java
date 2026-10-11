package com.mrzeyner.colonynations.data;

import java.util.UUID;

import com.mrzeyner.colonynations.nation.CityRef;

/**
 * A breaking city asking another nation to take it in. {@code offerDay} is the overworld day it was made.
 * The next day, silence becomes independence.
 */
public record SecessionOffer(CityRef city, UUID fromNation, UUID toNation, String cityName, long offerDay) {
    public SecessionOffer {
        cityName = cityName == null ? "" : cityName;
    }
}
