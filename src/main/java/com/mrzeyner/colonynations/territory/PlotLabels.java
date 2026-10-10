package com.mrzeyner.colonynations.territory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.mrzeyner.colonynations.nation.CityRef;

/**
 * Names shown for state land. City chunks are labeled by their colony directly.
 * A core chunk names the nearest city; everything else is plain annexed land.
 */
public final class PlotLabels {
    public enum Kind {
        STATE,
        CORE
    }

    public record Label(Kind kind, CityRef city, String name) {
        public Label {
            if (name == null) {
                name = "";
            }
            if (kind == Kind.STATE) {
                city = null;
                name = "";
            }
        }

        public static Label state() {
            return new Label(Kind.STATE, null, "");
        }

        public static Label core(CityRef city, String name) {
            return new Label(Kind.CORE, city, name);
        }
    }

    private PlotLabels() {
    }

    /**
     * @param claims claim chunks of each city of the nation
     * @param names colony name by city; missing names become empty
     * @param width founding-ring width in chunks
     */
    public static Map<ChunkRef, Label> ofState(
            Set<ChunkRef> state,
            Collection<? extends Collection<ChunkRef>> claims,
            Collection<NearestCity.Anchor> anchors,
            Map<CityRef, String> names,
            int width) {
        if (state.isEmpty()) {
            return Map.of();
        }
        Set<ChunkRef> core = CityCore.among(state, claims, width);
        Map<CityRef, String> colonyNames = names == null ? Map.of() : names;
        Map<ChunkRef, Label> labeled = new HashMap<>();
        for (ChunkRef chunk : state) {
            if (!core.contains(chunk)) {
                labeled.put(chunk, Label.state());
                continue;
            }
            CityRef city = NearestCity.find(chunk, anchors).orElse(null);
            String name = city == null ? "" : colonyNames.getOrDefault(city, "");
            labeled.put(chunk, Label.core(city, name));
        }
        return Map.copyOf(labeled);
    }
}
