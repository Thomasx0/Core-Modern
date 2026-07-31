package su.terrafirmagreg.core.client.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.BiomeWeightModifier;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.integration.map.ClientCacheManager;
import com.gregtechceu.gtceu.integration.map.GroupingMapRenderer;
import com.gregtechceu.gtceu.integration.map.cache.client.IClientCache;
import com.gregtechceu.gtceu.integration.map.layer.builtin.OreRenderLayer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import su.terrafirmagreg.core.TFGCore;
import su.terrafirmagreg.core.common.data.TFGParticles;
import su.terrafirmagreg.core.common.map.ProspectMode;
import su.terrafirmagreg.core.common.map.TFGOreVeinLang;
import su.terrafirmagreg.core.common.map.TFGProspectVeinGenerator;
import su.terrafirmagreg.core.common.map.TFGProspectVeinMarker;
import su.terrafirmagreg.core.network.packet.OreHighlightVeinPacket;

/**
 * Client-side Xaero {@code ore_veins} layer: render markers, persist them across sessions, and show prospect particles.
 * Prospect definitions are kept out of GT {@code CLIENT_ORE_VEINS} so EMI does not crash on null height ranges.
 */
@Mod.EventBusSubscriber(modid = TFGCore.MOD_ID, value = Dist.CLIENT)
public final class TFGProspectMapLayer implements IClientCache {

    public static final TFGProspectMapLayer INSTANCE = new TFGProspectMapLayer();

    private static final String FILE_NAME = "ore_prospects";
    private static final long HIGHLIGHT_DURATION_MS = 6_000;

    /** Client-only vein definitions keyed by {@code tfg:prospect_<x>_<y>_<z>}. */
    private static final Map<ResourceLocation, GTOreDefinition> displayVeins = new ConcurrentHashMap<>();

    private final Map<ResourceKey<Level>, Map<String, SavedMarker>> markersByDim = new HashMap<>();
    private final List<Highlight> highlights = new ArrayList<>();

    private TFGProspectMapLayer() {
    }

    public static void register() {
        ClientCacheManager.registerClientCache(INSTANCE, TFGCore.MOD_ID);
        INSTANCE.setupCacheFiles();
    }

    public static void handlePacket(OreHighlightVeinPacket packet) {
        if (packet.markers().isEmpty() && packet.scanMin() == null) {
            return;
        }
        if (packet.showParticles()) {
            INSTANCE.spawnParticles(packet);
        }
        INSTANCE.applyPacket(packet);
    }

    private void spawnParticles(OreHighlightVeinPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long expireTime = System.currentTimeMillis() + HIGHLIGHT_DURATION_MS;
        synchronized (highlights) {
            for (TFGProspectVeinMarker marker : packet.markers()) {
                highlights.add(new Highlight(marker.center(), expireTime));
                mc.level.addParticle(
                        TFGParticles.ORE_PROSPECTOR_VEIN.get(),
                        marker.center().getX() + 0.5,
                        marker.center().getY() + 0.5,
                        marker.center().getZ() + 0.5,
                        0, 0, 0);
            }
        }
    }

    private void applyPacket(OreHighlightVeinPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        GroupingMapRenderer renderer = GroupingMapRenderer.getInstance();
        if (renderer == null) {
            return;
        }

        ResourceKey<Level> dim = mc.level.dimension();
        List<BlockPos> activeCenters = new ArrayList<>(packet.markers().size());

        for (TFGProspectVeinMarker marker : packet.markers()) {
            if (marker.ores().isEmpty() || !canDisplay(marker)) {
                continue;
            }

            BlockPos center = marker.center();
            activeCenters.add(center);

            String existingMarkerId = null;
            if (TFGOreVeinLang.isHandProspect(marker.scanRange())) {
                removeScanMarkersNear(renderer, dim, center, ProspectMode.HAND_MERGE_RADIUS_SQ);
                existingMarkerId = findHandProspectMarkerIdNear(dim, center, ProspectMode.HAND_MERGE_RADIUS_SQ);
                if (existingMarkerId != null) {
                    TFGProspectVeinMarker existing = getMarker(dim, existingMarkerId);
                    if (existing != null) {
                        marker = TFGProspectVeinMarker.mergeHand(existing, marker);
                        center = marker.center();
                    }
                }
            } else {
                removeMarkersNear(renderer, dim, center, ProspectMode.CLUSTER_RADIUS_SQ);
            }

            String markerId = placeMarker(renderer, dim, existingMarkerId, marker);
            if (markerId != null) {
                upsertMarker(dim, markerId, marker);
            }
        }

        if (packet.scanMin() != null && packet.scanMax() != null) {
            pruneMarkersInRegion(renderer, dim, packet.scanMin(), packet.scanMax(), activeCenters);
        }
    }

    private void restoreMarker(ResourceKey<Level> dim, String markerId, TFGProspectVeinMarker marker) {
        GroupingMapRenderer renderer = GroupingMapRenderer.getInstance();
        if (renderer != null) {
            placeMarker(renderer, dim, markerId, marker);
        }
    }

    private void upsertMarker(ResourceKey<Level> dim, String markerId, TFGProspectVeinMarker marker) {
        markersByDim.computeIfAbsent(dim, d -> new HashMap<>())
                .put(markerId, SavedMarker.create(dim, markerId, marker));
    }

    private void removeMarker(ResourceKey<Level> dim, String markerId) {
        Map<String, SavedMarker> dimMarkers = markersByDim.get(dim);
        if (dimMarkers != null) {
            dimMarkers.remove(markerId);
            if (dimMarkers.isEmpty()) {
                markersByDim.remove(dim);
            }
        }
    }

    @Nullable
    private String findHandProspectMarkerIdNear(ResourceKey<Level> dim, BlockPos pos, int radiusSq) {
        Map<String, SavedMarker> dimMarkers = markersByDim.get(dim);
        if (dimMarkers == null) {
            return null;
        }
        String bestId = null;
        int bestDist = Integer.MAX_VALUE;
        for (var entry : dimMarkers.entrySet()) {
            SavedMarker marker = entry.getValue();
            if (marker.scanRange() != 0) {
                continue;
            }
            int dist = ProspectMode.distSq(marker.center(), pos);
            if (dist <= radiusSq && dist < bestDist) {
                bestId = entry.getKey();
                bestDist = dist;
            }
        }
        return bestId;
    }

    @Nullable
    private TFGProspectVeinMarker getMarker(ResourceKey<Level> dim, String markerId) {
        Map<String, SavedMarker> dimMarkers = markersByDim.get(dim);
        if (dimMarkers == null) {
            return null;
        }
        SavedMarker saved = dimMarkers.get(markerId);
        return saved != null ? saved.toVeinMarker() : null;
    }

    private List<Map.Entry<String, TFGProspectVeinMarker>> snapshotMarkers(ResourceKey<Level> dim) {
        Map<String, SavedMarker> dimMarkers = markersByDim.get(dim);
        if (dimMarkers == null) {
            return List.of();
        }
        List<Map.Entry<String, TFGProspectVeinMarker>> snapshot = new ArrayList<>(dimMarkers.size());
        for (var entry : dimMarkers.entrySet()) {
            snapshot.add(Map.entry(entry.getKey(), entry.getValue().toVeinMarker()));
        }
        return snapshot;
    }

    private boolean canDisplay(TFGProspectVeinMarker marker) {
        return !resolveMaterials(marker.materialIds()).isEmpty();
    }

    @Nullable
    private String placeMarker(GroupingMapRenderer renderer, ResourceKey<Level> dim, @Nullable String markerId,
            TFGProspectVeinMarker marker) {
        if (marker.ores().isEmpty() || !canDisplay(marker)) {
            return null;
        }

        List<Material> materials = resolveMaterials(marker.materialIds());
        BlockPos center = marker.center();
        ResourceLocation veinId = prospectVeinId(center);
        GTOreDefinition definition = new GTOreDefinition(
                ConstantInt.of(16),
                1f,
                1,
                null,
                null,
                null,
                0f,
                null,
                BiomeWeightModifier.EMPTY,
                new TFGProspectVeinGenerator(materials, marker.ores(), marker.scanRange()),
                null);
        displayVeins.put(veinId, definition);

        GeneratedVeinMetadata metadata = new GeneratedVeinMetadata(
                veinId,
                new ChunkPos(center),
                center,
                definition);

        String id = markerId != null ? markerId : OreRenderLayer.getId(metadata);
        String title = TFGOreVeinLang.prospectMarkerTitle(marker.scanRange(), marker.ores()).getString();
        renderer.addMarker(title, dim, metadata, id);
        return id;
    }

    private void removeMarkersNear(GroupingMapRenderer renderer, ResourceKey<Level> dim, BlockPos center,
            int radiusSq) {
        for (Map.Entry<String, TFGProspectVeinMarker> entry : snapshotMarkers(dim)) {
            if (ProspectMode.distSq(entry.getValue().center(), center) <= radiusSq) {
                removeFromMap(renderer, dim, entry.getKey(), entry.getValue().center());
            }
        }
    }

    /** Drop area-scan markers so a bare-hand ore click shows only the clicked ore type. */
    private void removeScanMarkersNear(GroupingMapRenderer renderer, ResourceKey<Level> dim, BlockPos center,
            int radiusSq) {
        for (Map.Entry<String, TFGProspectVeinMarker> entry : snapshotMarkers(dim)) {
            if (!TFGOreVeinLang.isHandProspect(entry.getValue().scanRange())
                    && ProspectMode.distSq(entry.getValue().center(), center) <= radiusSq) {
                removeFromMap(renderer, dim, entry.getKey(), entry.getValue().center());
            }
        }
    }

    private void pruneMarkersInRegion(GroupingMapRenderer renderer, ResourceKey<Level> dim, BlockPos scanMin,
            BlockPos scanMax, List<BlockPos> activeCenters) {
        for (Map.Entry<String, TFGProspectVeinMarker> entry : snapshotMarkers(dim)) {
            BlockPos center = entry.getValue().center();
            if (!ProspectMode.inBox(center, scanMin, scanMax)) {
                continue;
            }
            boolean stillActive = false;
            for (BlockPos active : activeCenters) {
                if (ProspectMode.distSq(center, active) <= ProspectMode.CLUSTER_RADIUS_SQ) {
                    stillActive = true;
                    break;
                }
            }
            if (!stillActive) {
                removeFromMap(renderer, dim, entry.getKey(), center);
            }
        }
    }

    private void removeFromMap(GroupingMapRenderer renderer, ResourceKey<Level> dim, String markerId, BlockPos center) {
        displayVeins.remove(prospectVeinId(center));
        renderer.removeMarker(dim, markerId);
        removeMarker(dim, markerId);
    }

    private static ResourceLocation prospectVeinId(BlockPos center) {
        return TFGCore.id("prospect_"
                + center.getX() + "_" + center.getY() + "_" + center.getZ());
    }

    private static List<Material> resolveMaterials(List<String> materialIds) {
        List<Material> materials = new ArrayList<>(materialIds.size());
        for (String id : materialIds) {
            Material material = GTCEuAPI.materialManager.getMaterial(id);
            if (material != null && !material.isNull()) {
                materials.add(material);
            }
        }
        return materials;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (INSTANCE.highlights) {
            INSTANCE.highlights.removeIf(h -> h.expireTime < now);
        }
    }

    @Override
    public void setupCacheFiles() {
        addSingleFile(FILE_NAME);
    }

    @Override
    public void clear() {
        markersByDim.clear();
        displayVeins.clear();
    }

    @Override
    public Collection<ResourceKey<Level>> getExistingDimensions(String prefix) {
        return List.of();
    }

    @Override
    public CompoundTag saveDimFile(String prefix, ResourceKey<Level> dim) {
        return null;
    }

    @Override
    public CompoundTag saveSingleFile(String name) {
        if (!FILE_NAME.equals(name) || markersByDim.isEmpty()) {
            return null;
        }
        return toNbt();
    }

    @Override
    public void readDimFile(String prefix, ResourceKey<Level> dim, CompoundTag data) {
    }

    @Override
    public void readSingleFile(String name, CompoundTag data) {
        if (!FILE_NAME.equals(name)) {
            return;
        }
        fromNbt(data);
    }

    private CompoundTag toNbt() {
        CompoundTag root = new CompoundTag();
        ListTag markerList = new ListTag();
        for (var dimEntry : markersByDim.entrySet()) {
            String dimId = dimEntry.getKey().location().toString();
            for (var markerEntry : dimEntry.getValue().entrySet()) {
                markerList.add(markerEntry.getValue().toNbt(dimId, markerEntry.getKey()));
            }
        }
        root.put("markers", markerList);
        return root;
    }

    private void fromNbt(CompoundTag root) {
        markersByDim.clear();
        ListTag markerList = root.getList("markers", Tag.TAG_COMPOUND);
        for (Tag tag : markerList) {
            if (!(tag instanceof CompoundTag compound)) {
                continue;
            }
            SavedMarker saved = SavedMarker.fromNbt(compound);
            if (saved == null) {
                continue;
            }
            markersByDim.computeIfAbsent(saved.dim(), d -> new HashMap<>())
                    .put(saved.markerId(), saved);
            restoreMarker(saved.dim(), saved.markerId(), saved.toVeinMarker());
        }
    }

    private record Highlight(BlockPos pos, long expireTime) {
    }

    private record SavedMarker(ResourceKey<Level> dim, String markerId, BlockPos center,
            List<TFGProspectVeinMarker.OreCount> ores, List<String> materialIds, int scanRange) {

        static SavedMarker create(ResourceKey<Level> dimension, String id, TFGProspectVeinMarker marker) {
            return new SavedMarker(dimension, id, marker.center(), marker.ores(), marker.materialIds(),
                    marker.scanRange());
        }

        static SavedMarker fromNbt(CompoundTag tag) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                    new ResourceLocation(tag.getString("dim")));
            String markerId = tag.getString("markerId");
            BlockPos center = BlockPos.of(tag.getLong("center"));
            int scanRange = tag.getInt("scanRange");

            ListTag oreList = tag.getList("ores", Tag.TAG_COMPOUND);
            List<TFGProspectVeinMarker.OreCount> ores = new ArrayList<>(oreList.size());
            for (Tag oreTag : oreList) {
                if (oreTag instanceof CompoundTag oreCompound) {
                    Component name = Component.Serializer.fromJson(oreCompound.getString("name"));
                    if (name != null) {
                        ores.add(new TFGProspectVeinMarker.OreCount(name, oreCompound.getInt("count")));
                    }
                }
            }
            if (ores.isEmpty()) {
                return null;
            }

            ListTag matList = tag.getList("materials", Tag.TAG_STRING);
            List<String> materialIds = new ArrayList<>(matList.size());
            for (Tag matTag : matList) {
                materialIds.add(matTag.getAsString());
            }
            return new SavedMarker(dim, markerId, center, ores, materialIds, scanRange);
        }

        CompoundTag toNbt(String dimId, String id) {
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", dimId);
            tag.putString("markerId", id);
            tag.putLong("center", center.asLong());
            tag.putInt("scanRange", scanRange);

            ListTag oreList = new ListTag();
            for (TFGProspectVeinMarker.OreCount ore : ores) {
                CompoundTag oreTag = new CompoundTag();
                oreTag.putString("name", Component.Serializer.toJson(ore.displayName()));
                oreTag.putInt("count", ore.count());
                oreList.add(oreTag);
            }
            tag.put("ores", oreList);

            ListTag matList = new ListTag();
            for (String materialId : materialIds) {
                matList.add(net.minecraft.nbt.StringTag.valueOf(materialId));
            }
            tag.put("materials", matList);
            return tag;
        }

        TFGProspectVeinMarker toVeinMarker() {
            return new TFGProspectVeinMarker(center, ores, materialIds, scanRange);
        }
    }
}
