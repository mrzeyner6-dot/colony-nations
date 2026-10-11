package com.mrzeyner.colonynations.compat.journeymap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.client.TerritoryClientCache;
import com.mrzeyner.colonynations.nation.FreeCity;
import com.mrzeyner.colonynations.territory.ChunkPolygons;
import com.mrzeyner.colonynations.territory.ChunkRef;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.common.JourneyMapPlugin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Political map. JourneyMap classloads this only when that mod is installed, so a dedicated server
 * never touches these types. Colony Nations does not reference this class itself.
 * Same drawing path as the first map build: static polygon titles, no markers, no hover listener.
 * City land is one polygon set per colony so the title can name that colony. State land, including
 * the city core, stays merged per nation.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class NationMapPlugin implements IClientPlugin {
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final float STATE_FILL = 0.25F;
    private static final float CITY_FILL = 0.40F;
    private static final float STATE_STROKE = 2F;
    private static final float CITY_STROKE = 4F;

    private IClientAPI api;
    private boolean listening;
    private boolean failureLogged;
    private final Map<UUID, List<PolygonOverlay>> shown = new HashMap<>();

    public NationMapPlugin() {
    }

    @Override
    public String getModId() {
        return ColonyNations.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        try {
            this.api = jmClientApi;
            if (!listening) {
                NeoForge.EVENT_BUS.addListener(this::onClientTick);
                listening = true;
            }
        } catch (Exception exception) {
            logOnce(exception);
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        try {
            if (api == null || Minecraft.getInstance().level == null) {
                return;
            }
            String here = Minecraft.getInstance().level.dimension().location().toString();
            if (!here.equals(TerritoryClientCache.dimension())) {
                return;
            }
            if (TerritoryClientCache.consumeReset()) {
                TerritoryClientCache.consumeDirty();
                api.removeAll(ColonyNations.MOD_ID, DisplayType.Polygon);
                shown.clear();
                for (UUID nation : TerritoryClientCache.nations().keySet()) {
                    draw(nation);
                }
                return;
            }
            for (UUID nation : TerritoryClientCache.consumeDirty()) {
                draw(nation);
            }
        } catch (Exception exception) {
            logOnce(exception);
        }
    }

    private void draw(UUID nationId) {
        List<PolygonOverlay> previous = shown.remove(nationId);
        if (previous != null) {
            for (PolygonOverlay overlay : previous) {
                api.remove(overlay);
            }
        }
        TerritoryClientCache.NationView nation = TerritoryClientCache.nations().get(nationId);
        if (nation == null) {
            return;
        }
        String dimension = TerritoryClientCache.dimension();
        boolean free = FreeCity.MAP_ID.equals(nationId);
        String nationName = free
                ? I18n.get("map.colony_nations.free_city")
                : (nation.name() == null ? "" : nation.name());
        Set<ChunkRef> state = new HashSet<>();
        Map<Integer, Set<ChunkRef>> cities = new HashMap<>();
        Map<Integer, String> cityNames = new HashMap<>();
        Map<Integer, Boolean> unrest = new HashMap<>();
        for (Map.Entry<ChunkRef, TerritoryClientCache.Plot> entry : TerritoryClientCache.plots().entrySet()) {
            TerritoryClientCache.Plot plot = entry.getValue();
            if (plot == null || !nationId.equals(plot.nation()) || !dimension.equals(entry.getKey().dimension())) {
                continue;
            }
            if (plot.land() == TerritoryClientCache.Land.CITY) {
                cities.computeIfAbsent(plot.colonyId(), id -> new HashSet<>()).add(entry.getKey());
                String placeName = plot.placeName() == null ? "" : plot.placeName();
                if (!placeName.isEmpty()) {
                    cityNames.put(plot.colonyId(), placeName);
                }
                if (plot.unrest()) {
                    unrest.put(plot.colonyId(), true);
                }
            } else {
                state.add(entry.getKey());
            }
        }
        ResourceKey<Level> level = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
        List<PolygonOverlay> overlays = new ArrayList<>();
        overlays.addAll(polygons(level, nationName, nation.color(), state, false, I18n.get("map.colony_nations.tooltip.state", nationName)));
        for (Map.Entry<Integer, Set<ChunkRef>> city : cities.entrySet()) {
            String placeName = cityNames.getOrDefault(city.getKey(), "");
            String tooltip;
            String title;
            if (free) {
                tooltip = "map.colony_nations.tooltip.free_city";
                title = I18n.get(tooltip, placeName);
            } else if (Boolean.TRUE.equals(unrest.get(city.getKey()))) {
                title = I18n.get("map.colony_nations.tooltip.city_unrest", placeName, nationName);
            } else {
                title = I18n.get("map.colony_nations.tooltip.city", placeName, nationName);
            }
            overlays.addAll(polygons(
                    level,
                    nationName,
                    nation.color(),
                    city.getValue(),
                    true,
                    title));
        }
        if (!overlays.isEmpty()) {
            shown.put(nationId, overlays);
        }
    }

    private List<PolygonOverlay> polygons(
            ResourceKey<Level> level,
            String nationName,
            int color,
            Set<ChunkRef> chunks,
            boolean city,
            String title) {
        List<PolygonOverlay> overlays = new ArrayList<>();
        for (ChunkPolygons.Polygon polygon : ChunkPolygons.of(chunks)) {
            try {
                ShapeProperties shape = new ShapeProperties()
                        .setFillColor(color)
                        .setStrokeColor(color)
                        .setFillOpacity(city ? CITY_FILL : STATE_FILL)
                        .setStrokeOpacity(0.9F)
                        .setStrokeWidth(city ? CITY_STROKE : STATE_STROKE);
                PolygonOverlay overlay = new PolygonOverlay(ColonyNations.MOD_ID, level, shape, ring(polygon.outer()), holes(polygon.holes()));
                overlay.setTitle(title);
                overlay.setOverlayGroupName(nationName);
                overlay.setDisplayOrder(city ? 20 : 10);
                api.show(overlay);
                overlays.add(overlay);
            } catch (Exception exception) {
                logOnce(exception);
            }
        }
        return overlays;
    }

    private void logOnce(Exception exception) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        ColonyNations.LOGGER.warn("Colony Nations map overlay failed once and will stay quiet", exception);
    }

    private static MapPolygon ring(List<ChunkPolygons.Point> points) {
        List<BlockPos> blocks = new ArrayList<>(points.size());
        for (ChunkPolygons.Point point : points) {
            blocks.add(new BlockPos(point.x() * BLOCKS_PER_CHUNK, 0, point.z() * BLOCKS_PER_CHUNK));
        }
        return new MapPolygon(blocks);
    }

    private static List<MapPolygon> holes(List<List<ChunkPolygons.Point>> holes) {
        if (holes.isEmpty()) {
            return null;
        }
        List<MapPolygon> polygons = new ArrayList<>(holes.size());
        for (List<ChunkPolygons.Point> hole : holes) {
            polygons.add(ring(hole));
        }
        return polygons;
    }
}
