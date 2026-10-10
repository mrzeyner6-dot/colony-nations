package com.mrzeyner.colonynations.compat.journeymap;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.client.TerritoryClientCache;
import com.mrzeyner.colonynations.territory.ChunkPolygons;
import com.mrzeyner.colonynations.territory.ChunkRef;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.IOverlayListener;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.display.Overlay;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.JourneyMapPlugin;

import com.mojang.blaze3d.platform.NativeImage;

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
 * Polygons stay merged per nation. The hover title is rewritten from the chunk under the cursor,
 * because {@link IOverlayListener#onMouseMove} receives that block and the map reads the title each frame.
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
    private final Map<UUID, List<Overlay>> shown = new HashMap<>();

    public NationMapPlugin() {
    }

    @Override
    public String getModId() {
        return ColonyNations.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.api = jmClientApi;
        if (!listening) {
            NeoForge.EVENT_BUS.addListener(this::onClientTick);
            listening = true;
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
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
            api.removeAll(ColonyNations.MOD_ID, DisplayType.Marker);
            shown.clear();
            for (UUID nation : TerritoryClientCache.nations().keySet()) {
                draw(nation);
            }
            return;
        }
        for (UUID nation : TerritoryClientCache.consumeDirty()) {
            draw(nation);
        }
    }

    private void draw(UUID nationId) {
        List<Overlay> previous = shown.remove(nationId);
        if (previous != null) {
            for (Overlay overlay : previous) {
                api.remove(overlay);
            }
        }
        TerritoryClientCache.NationView nation = TerritoryClientCache.nations().get(nationId);
        if (nation == null) {
            return;
        }
        String dimension = TerritoryClientCache.dimension();
        Set<ChunkRef> state = new HashSet<>();
        Set<ChunkRef> city = new HashSet<>();
        Map<Integer, List<ChunkRef>> claims = new HashMap<>();
        Map<Integer, String> claimNames = new HashMap<>();
        for (Map.Entry<ChunkRef, TerritoryClientCache.Plot> entry : TerritoryClientCache.plots().entrySet()) {
            if (!nationId.equals(entry.getValue().nation()) || !dimension.equals(entry.getKey().dimension())) {
                continue;
            }
            TerritoryClientCache.Plot plot = entry.getValue();
            if (plot.land() == TerritoryClientCache.Land.CITY) {
                city.add(entry.getKey());
                claims.computeIfAbsent(plot.colonyId(), key -> new ArrayList<>()).add(entry.getKey());
                if (!plot.placeName().isEmpty()) {
                    claimNames.put(plot.colonyId(), plot.placeName());
                }
            } else {
                state.add(entry.getKey());
            }
        }
        ResourceKey<Level> level = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
        List<Overlay> overlays = new ArrayList<>();
        overlays.addAll(polygons(level, nation, state, false));
        overlays.addAll(polygons(level, nation, city, true));
        for (Map.Entry<Integer, List<ChunkRef>> claim : claims.entrySet()) {
            String placeName = claimNames.getOrDefault(claim.getKey(), "");
            if (placeName.isEmpty() || claim.getValue().isEmpty()) {
                continue;
            }
            Overlay marker = cityMarker(level, nation, placeName, claim.getValue());
            if (marker != null) {
                overlays.add(marker);
            }
        }
        if (!overlays.isEmpty()) {
            shown.put(nationId, overlays);
        }
    }

    private List<PolygonOverlay> polygons(
            ResourceKey<Level> level,
            TerritoryClientCache.NationView nation,
            Set<ChunkRef> chunks,
            boolean city) {
        List<PolygonOverlay> overlays = new ArrayList<>();
        for (ChunkPolygons.Polygon polygon : ChunkPolygons.of(chunks)) {
            ShapeProperties shape = new ShapeProperties()
                    .setFillColor(nation.color())
                    .setStrokeColor(nation.color())
                    .setFillOpacity(city ? CITY_FILL : STATE_FILL)
                    .setStrokeOpacity(0.9F)
                    .setStrokeWidth(city ? CITY_STROKE : STATE_STROKE);
            PolygonOverlay overlay = new PolygonOverlay(ColonyNations.MOD_ID, level, shape, ring(polygon.outer()), holes(polygon.holes()));
            overlay.setTitle(titleAt(null, nation));
            overlay.setOverlayGroupName(nation.name());
            overlay.setDisplayOrder(city ? 20 : 10);
            overlay.setOverlayListener(new IOverlayListener() {
                @Override
                public void onMouseMove(UIState state, Point2D.Double mouse, BlockPos pos) {
                    String title = titleAt(pos, nation);
                    if (!title.equals(overlay.getTitle())) {
                        overlay.setTitle(title);
                    }
                }
            });
            try {
                api.show(overlay);
                overlays.add(overlay);
            } catch (Exception exception) {
                ColonyNations.LOGGER.warn("Could not show territory for {}", nation.name());
            }
        }
        return overlays;
    }

    private Overlay cityMarker(ResourceKey<Level> level, TerritoryClientCache.NationView nation, String placeName, List<ChunkRef> claim) {
        long sumX = 0L;
        long sumZ = 0L;
        for (ChunkRef chunk : claim) {
            sumX += chunk.x();
            sumZ += chunk.z();
        }
        int chunkX = (int) Math.round(sumX / (double) claim.size());
        int chunkZ = (int) Math.round(sumZ / (double) claim.size());
        BlockPos center = new BlockPos(chunkX * BLOCKS_PER_CHUNK + BLOCKS_PER_CHUNK / 2, 0, chunkZ * BLOCKS_PER_CHUNK + BLOCKS_PER_CHUNK / 2);
        MarkerOverlay marker = new MarkerOverlay(ColonyNations.MOD_ID, center, blankIcon());
        marker.setDimension(level);
        marker.setLabel(placeName);
        marker.setTitle(I18n.get("map.colony_nations.tooltip.city", placeName, nation.name()));
        marker.setOverlayGroupName(nation.name());
        marker.setDisplayOrder(30);
        marker.setTextProperties(new TextProperties()
                .setColor(0xFFFFFF)
                .setOpacity(1F)
                .setBackgroundColor(0x000000)
                .setBackgroundOpacity(0.6F)
                .setFontShadow(true));
        try {
            api.show(marker);
            return marker;
        } catch (Exception exception) {
            ColonyNations.LOGGER.warn("Could not label {} for {}", placeName, nation.name());
            return null;
        }
    }

    private static String titleAt(BlockPos pos, TerritoryClientCache.NationView nation) {
        if (pos == null) {
            return I18n.get("map.colony_nations.tooltip.state", nation.name());
        }
        ChunkRef chunk = new ChunkRef(TerritoryClientCache.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        TerritoryClientCache.Plot plot = TerritoryClientCache.plot(chunk);
        if (plot == null || !nation.id().equals(plot.nation())) {
            return I18n.get("map.colony_nations.tooltip.state", nation.name());
        }
        return switch (plot.land()) {
            case CITY -> I18n.get("map.colony_nations.tooltip.city", plot.placeName(), nation.name());
            case CORE -> plot.placeName().isEmpty()
                    ? I18n.get("map.colony_nations.tooltip.state", nation.name())
                    : I18n.get("map.colony_nations.tooltip.core", nation.name(), plot.placeName());
            case STATE -> I18n.get("map.colony_nations.tooltip.state", nation.name());
        };
    }

    private static MapImage blankIcon() {
        NativeImage image = new NativeImage(1, 1, true);
        MapImage icon = new MapImage(image);
        icon.setOpacity(0F);
        icon.setDisplayWidth(1);
        icon.setDisplayHeight(1);
        return icon;
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
