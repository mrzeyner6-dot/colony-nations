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
import com.mrzeyner.colonynations.network.ChunkPlot;
import com.mrzeyner.colonynations.network.NationStamp;
import com.mrzeyner.colonynations.territory.ChunkOutlines;
import com.mrzeyner.colonynations.territory.ChunkOutlines.GridChunk;
import com.mrzeyner.colonynations.territory.ChunkOutlines.Point;
import com.mrzeyner.colonynations.territory.ChunkOutlines.Polygon;
import com.mrzeyner.colonynations.territory.ChunkOutlines.Ring;
import com.mrzeyner.colonynations.territory.ChunkRef;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Political map: state land and city land for the dimension the client is in.
 * JourneyMap constructs this class only when that mod is installed.
 * Overlays are replaced per nation, so a claim change does not rebuild every nation.
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public final class ColonyNationsMapPlugin implements IClientPlugin {
    private static final float STATE_FILL = 0.25f;
    private static final float CITY_FILL = 0.40f;
    private static final float STATE_STROKE = 2f;
    private static final float CITY_STROKE = 4f;
    private static final int STATE_ORDER = 1000;
    private static final int CITY_ORDER = 1100;

    private final Map<UUID, List<PolygonOverlay>> shown = new HashMap<>();
    private IClientAPI api;

    public ColonyNationsMapPlugin() {
    }

    @Override
    public String getModId() {
        return ColonyNations.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.api = jmClientApi;
        TerritoryClientCache.listen(this::onTerritory);
        ClientEventRegistry.MAPPING_EVENT.subscribe(ColonyNations.MOD_ID, this::onMapping);
        redrawAll();
    }

    private void onMapping(MappingEvent event) {
        if (event.getStage() == MappingEvent.Stage.MAPPING_STOPPED) {
            if (api != null) {
                api.removeAll(ColonyNations.MOD_ID);
            }
            shown.clear();
            return;
        }
        redrawAll();
    }

    private void onTerritory(Set<UUID> nations, boolean full) {
        try {
            if (full) {
                redrawAll();
            } else {
                for (UUID nation : nations) {
                    redraw(nation);
                }
            }
        } catch (RuntimeException exception) {
            ColonyNations.LOGGER.warn("Territory map update failed", exception);
        }
    }

    private void redrawAll() {
        Set<UUID> nations = new HashSet<>(shown.keySet());
        nations.addAll(TerritoryClientCache.nations().keySet());
        for (UUID nation : nations) {
            redraw(nation);
        }
    }

    private void redraw(UUID nationId) {
        List<PolygonOverlay> previous = shown.remove(nationId);
        if (previous != null && api != null) {
            for (PolygonOverlay overlay : previous) {
                api.remove(overlay);
            }
        }
        NationStamp nation = TerritoryClientCache.nations().get(nationId);
        ResourceKey<Level> dimension = dimension();
        if (nation == null || dimension == null || api == null) {
            return;
        }
        if (!api.playerAccepts(ColonyNations.MOD_ID, DisplayType.Polygon)) {
            return;
        }
        Map<LandGroup, Set<GridChunk>> groups = new HashMap<>();
        for (Map.Entry<ChunkRef, ChunkPlot> entry : TerritoryClientCache.plots().entrySet()) {
            ChunkPlot plot = entry.getValue();
            if (!nationId.equals(plot.nation()) || plot.release()) {
                continue;
            }
            groups.computeIfAbsent(new LandGroup(plot.city(), plot.label()), key -> new HashSet<>())
                    .add(new GridChunk(plot.x(), plot.z()));
        }
        List<PolygonOverlay> created = new ArrayList<>();
        for (Map.Entry<LandGroup, Set<GridChunk>> group : groups.entrySet()) {
            for (Polygon polygon : ChunkOutlines.merge(group.getValue())) {
                PolygonOverlay overlay = overlay(nation, group.getKey(), dimension, polygon);
                try {
                    api.show(overlay);
                    created.add(overlay);
                } catch (Exception exception) {
                    ColonyNations.LOGGER.warn("Could not show territory for {}", nation.name(), exception);
                }
            }
        }
        if (!created.isEmpty()) {
            shown.put(nationId, created);
        }
    }

    private PolygonOverlay overlay(NationStamp nation, LandGroup group, ResourceKey<Level> dimension, Polygon polygon) {
        int color = nation.color();
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(color)
                .setStrokeColor(color)
                .setFillOpacity(group.city() ? CITY_FILL : STATE_FILL)
                .setStrokeOpacity(1f)
                .setStrokeWidth(group.city() ? CITY_STROKE : STATE_STROKE);
        String land = group.city()
                ? Component.translatable("map.colony_nations.land.city", group.label()).getString()
                : Component.translatable("map.colony_nations.land.state").getString();
        List<MapPolygon> holes = new ArrayList<>();
        for (Ring hole : polygon.holes()) {
            holes.add(new MapPolygon(corners(hole.points())));
        }
        PolygonOverlay overlay = new PolygonOverlay(
                ColonyNations.MOD_ID,
                dimension,
                shape,
                new MapPolygon(corners(polygon.outer().points())),
                holes.isEmpty() ? null : holes);
        overlay.setTitle(nation.name() + " — " + land);
        overlay.setLabel(land);
        overlay.setOverlayGroupName(nation.name());
        overlay.setDisplayOrder(group.city() ? CITY_ORDER : STATE_ORDER);
        overlay.setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap);
        return overlay;
    }

    private static List<BlockPos> corners(List<Point> points) {
        List<BlockPos> blocks = new ArrayList<>(points.size());
        for (Point point : points) {
            blocks.add(new BlockPos(point.x() * 16, 0, point.z() * 16));
        }
        return blocks;
    }

    private static ResourceKey<Level> dimension() {
        String id = TerritoryClientCache.dimension();
        if (id == null || id.isBlank()) {
            return null;
        }
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
    }

    private record LandGroup(boolean city, String label) {
    }
}
