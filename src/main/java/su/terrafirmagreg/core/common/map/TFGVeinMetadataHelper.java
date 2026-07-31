package su.terrafirmagreg.core.common.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.block.MaterialBlock;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.IndicatorGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.prospecting.SPacketProspectOre;
import com.gregtechceu.gtceu.integration.map.cache.GridPos;
import com.gregtechceu.gtceu.integration.map.cache.server.ServerCache;
import com.mojang.datafixers.util.Either;

import net.dries007.tfc.util.collections.IWeighted;
import net.dries007.tfc.world.feature.vein.IVeinConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.common.Tags;

import su.terrafirmagreg.core.TFGCore;

/**
 * Bridges TFG/TFC ore veins into GregTech's {@link ServerCache}, so electric prospectors, ore-block clicks and map
 * integration use the standard {@link SPacketProspectOre} pipeline.
 */
public final class TFGVeinMetadataHelper {

    private static final TagKey<Block> ORE_TAG = Tags.Blocks.ORES;

    private TFGVeinMetadataHelper() {
    }

    /**
     * Called from TFC vein worldgen when a vein is successfully placed.
     */
    public static void registerPlacedVein(WorldGenLevel level, BlockPos center, IVeinConfig veinConfig) {
        List<Material> materials = materialsFromVeinConfig(veinConfig);
        ResourceLocation veinId = TFGOreVeinDefinitions.matchVein(materials);
        if (veinId == null) {
            return;
        }
        registerVein(level.getLevel(), center, veinId, materials);
    }

    /**
     * Register prospected clusters in {@link ServerCache} without sending client packets.
     * Map markers for the geological hammer use {@link su.terrafirmagreg.core.network.packet.OreHighlightVeinPacket}.
     */
    public static void registerCluster(ServerLevel level, ServerPlayer player, List<BlockPos> orePositions) {
        List<List<BlockPos>> clusters = clusterByRadius(orePositions, ProspectMode.CLUSTER_RADIUS);
        int added = 0;

        for (List<BlockPos> cluster : clusters) {
            List<Material> materials = orderedMaterials(level, cluster);
            ResourceLocation veinId = TFGOreVeinDefinitions.matchVein(materials);
            if (veinId == null) {
                continue;
            }
            BlockPos center = averageBlockPos(cluster);
            if (registerVein(level, center, veinId, materials) != null) {
                added++;
            }
        }

        if (added > 0) {
            TFGCore.LOGGER.debug("[TFG veins] registerCluster: added {} vein(s) to ServerCache", added);
        }
    }

    /**
     * Scan a chunk for ore blocks and populate {@link ServerCache} for any clusters not yet registered.
     * Lets the GT electric prospector find veins that were generated before this integration existed.
     */
    /** All ore blocks in a chunk (same scan the GT electric prospector GUI uses). */
    public static List<BlockPos> collectOreBlocksInChunk(ServerLevel level, ChunkPos chunkPos) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        List<BlockPos> orePositions = new ArrayList<>();
        BoundingBox box = new BoundingBox(
                chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(), level.getMaxBuildHeight(), chunkPos.getMaxBlockZ());

        BlockPos.betweenClosedStream(box).forEach(pos -> {
            BlockState state = chunk.getBlockState(pos);
            if (state.is(ORE_TAG)) {
                orePositions.add(pos.immutable());
            }
        });
        return orePositions;
    }

    public static void backfillChunk(ServerLevel level, net.minecraft.world.level.ChunkPos chunkPos) {
        List<BlockPos> orePositions = collectOreBlocksInChunk(level, chunkPos);
        if (orePositions.isEmpty()) {
            return;
        }

        List<List<BlockPos>> clusters = clusterByRadius(orePositions, ProspectMode.CLUSTER_RADIUS);
        int added = 0;
        for (List<BlockPos> cluster : clusters) {
            List<Material> materials = orderedMaterials(level, cluster);
            ResourceLocation veinId = TFGOreVeinDefinitions.matchVein(materials);
            if (veinId == null) {
                continue;
            }
            BlockPos center = averageBlockPos(cluster);
            if (registerVein(level, center, veinId, materials) != null) {
                added++;
            }
        }

        if (added > 0) {
            TFGCore.LOGGER.debug("[TFG veins] backfill chunk [{}, {}]: added {} vein(s)", chunkPos.x, chunkPos.z, added);
        }
    }

    /**
     * GT chat notification for bare-hand ore clicks: one {@link SPacketProspectOre} with the closest matching vein,
     * instead of GT's default radius scan that spams one message per vein in range.
     */
    public static void notifyHandClickedOre(ServerPlayer player, ServerLevel level, BlockPos origin, Material material) {
        GeneratedVeinMetadata vein = resolveVeinForHandClick(level, origin, material);
        if (vein != null) {
            GTNetwork.sendToPlayer(player, new SPacketProspectOre(level.dimension(), List.of(vein)));
        }
    }

    @Nullable
    private static GeneratedVeinMetadata resolveVeinForHandClick(ServerLevel level, BlockPos origin, Material material) {
        GeneratedVeinMetadata vein = findClosestVeinWithMaterial(level.dimension(), origin, material, 24);
        if (vein != null) {
            return vein;
        }

        List<Material> materials = orderedMaterials(level, List.of(origin));
        ResourceLocation veinId = TFGOreVeinDefinitions.matchVein(materials);
        if (veinId == null) {
            return null;
        }
        BlockPos center = origin;
        return registerVein(level, center, veinId, materials);
    }

    /** Same filter as {@link ServerCache#prospectBySurfaceRockMaterial}. */
    public static List<GeneratedVeinMetadata> filterVeinsBySurfaceRock(ResourceKey<Level> dim, BlockPos origin,
            int radius, Material material) {
        List<GeneratedVeinMetadata> found = new ArrayList<>();
        for (GeneratedVeinMetadata vein : ServerCache.instance.getNearbyVeins(dim, origin, radius)) {
            for (IndicatorGenerator generator : vein.definition().indicatorGenerators()) {
                if (matchesSurfaceRockIndicator(generator, material)) {
                    found.add(vein);
                    break;
                }
            }
        }
        return found;
    }

    /** Same filter as {@link ServerCache#prospectByDepositName}. */
    public static List<GeneratedVeinMetadata> filterVeinsByDepositName(ResourceKey<Level> dim, BlockPos origin,
            int radius, String depositName) {
        List<GeneratedVeinMetadata> found = new ArrayList<>();
        for (GeneratedVeinMetadata vein : ServerCache.instance.getNearbyVeins(dim, origin, radius)) {
            ResourceLocation key = GTRegistries.ORE_VEINS.getKey(vein.definition());
            if (key != null && key.toString().equals(depositName)) {
                found.add(vein);
            }
        }
        return found;
    }

    private static boolean matchesSurfaceRockIndicator(IndicatorGenerator generator, Material material) {
        Either<BlockState, Material> block = generator.block();
        if (block == null) {
            return false;
        }
        return block.map(
                state -> {
                    MaterialStack stack = ChemicalHelper.getMaterialStack(state.getBlock().asItem());
                    return stack != null && !stack.isEmpty() && stack.material() == material;
                },
                mat -> mat == material);
    }

    @Nullable
    private static GeneratedVeinMetadata findClosestVeinWithMaterial(ResourceKey<Level> dim, BlockPos origin,
            Material material, int radius) {
        GeneratedVeinMetadata best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GeneratedVeinMetadata vein : ServerCache.instance.getNearbyVeins(dim, origin, radius)) {
            if (!vein.definition().veinGenerator().getAllMaterials().contains(material)) {
                continue;
            }
            int dist = dist2(vein.center(), origin);
            if (dist < bestDist) {
                bestDist = dist;
                best = vein;
            }
        }
        return best;
    }

    private static GeneratedVeinMetadata registerVein(ServerLevel level, BlockPos center, ResourceLocation veinId,
            List<Material> materials) {
        GTOreDefinition definition = resolveDefinition(veinId);
        if (definition == null) {
            return null;
        }

        ServerCache.instance.maybeInitWorld(level);
        GeneratedVeinMetadata metadata = new GeneratedVeinMetadata(
                veinId,
                new net.minecraft.world.level.ChunkPos(center),
                center,
                definition);

        int gridX = GridPos.blockToGridCoords(center.getX());
        int gridZ = GridPos.blockToGridCoords(center.getZ());
        ServerCache.instance.addVein(level.dimension(), gridX, gridZ, metadata);
        return metadata;
    }

    /** Must be the canonical instance from {@link GTRegistries#ORE_VEINS} for packets and NBT. */
    private static GTOreDefinition resolveDefinition(ResourceLocation veinId) {
        GTOreDefinition definition = GTRegistries.ORE_VEINS.get(veinId);
        if (definition != null && GTRegistries.ORE_VEINS.containValue(definition)) {
            return definition;
        }
        TFGCore.LOGGER.warn("[TFG veins] No registry entry for ore vein {} — run /reload or rejoin after KubeJS init", veinId);
        return null;
    }

    private static List<Material> materialsFromVeinConfig(IVeinConfig config) {
        Set<Material> materials = new HashSet<>();
        for (IWeighted<BlockState> weighted : config.config().states().values()) {
            for (BlockState state : weighted.values()) {
                Material material = materialOf(state.getBlock());
                if (material != null) {
                    materials.add(material);
                }
            }
        }
        return new ArrayList<>(materials);
    }

    public static List<Material> orderedMaterials(Level level, List<BlockPos> cluster) {
        Map<Material, Integer> counts = new HashMap<>();
        for (BlockPos pos : cluster) {
            Material material = materialOf(level.getBlockState(pos).getBlock());
            if (material != null && !material.isNull()) {
                counts.merge(material, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Material, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Count ore blocks in a cluster by block display name (same grouping as the geological hammer chat).
     */
    public static List<TFGProspectVeinMarker.OreCount> countOresByBlockName(Level level, List<BlockPos> cluster) {
        Map<Component, Integer> counts = new HashMap<>();
        for (BlockPos pos : cluster) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ORE_TAG)) {
                counts.merge(state.getBlock().getName(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Component, Integer>comparingByValue().reversed())
                .map(e -> new TFGProspectVeinMarker.OreCount(e.getKey(), e.getValue()))
                .toList();
    }

    /** Unique ore block names in a cluster, without counts (copper / bronze propicks). */
    public static List<TFGProspectVeinMarker.OreCount> uniqueOreNames(Level level, List<BlockPos> cluster) {
        Map<String, Component> names = new LinkedHashMap<>();
        for (BlockPos pos : cluster) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ORE_TAG)) {
                Component name = state.getBlock().getName();
                names.putIfAbsent(Component.Serializer.toJson(name), name);
            }
        }
        return names.values().stream()
                .map(name -> new TFGProspectVeinMarker.OreCount(name, 0))
                .toList();
    }

    private static Material materialOf(Block block) {
        if (block instanceof MaterialBlock materialBlock && !materialBlock.material.isNull()) {
            return materialBlock.material;
        }
        MaterialStack ms = ChemicalHelper.getMaterialStack(block.asItem());
        return (ms == null || ms.isEmpty()) ? null : ms.material();
    }

    public static List<List<BlockPos>> clusterByRadius(List<BlockPos> orePositions) {
        return clusterByRadius(orePositions, ProspectMode.CLUSTER_RADIUS);
    }

    public static BlockPos averageBlockPos(List<BlockPos> list) {
        long sx = 0, sy = 0, sz = 0;
        for (BlockPos p : list) {
            sx += p.getX();
            sy += p.getY();
            sz += p.getZ();
        }
        int n = list.size();
        return new BlockPos(Math.round(sx / (float) n), Math.round(sy / (float) n), Math.round(sz / (float) n));
    }

    public static List<String> materialIds(List<Material> materials) {
        return materials.stream()
                .map(m -> m.getResourceLocation().toString())
                .toList();
    }

    private static List<List<BlockPos>> clusterByRadius(List<BlockPos> orePositions, int radius) {
        final int r2 = radius * radius;
        List<List<BlockPos>> clusters = new ArrayList<>();
        Set<BlockPos> unassigned = new HashSet<>(orePositions);

        while (!unassigned.isEmpty()) {
            BlockPos seed = unassigned.iterator().next();
            unassigned.remove(seed);

            List<BlockPos> cluster = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(seed);

            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();
                cluster.add(cur);
                List<BlockPos> toAttach = new ArrayList<>();
                for (BlockPos candidate : unassigned) {
                    if (dist2(cur, candidate) <= r2) {
                        toAttach.add(candidate);
                    }
                }
                for (BlockPos cand : toAttach) {
                    unassigned.remove(cand);
                    queue.add(cand);
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    private static int dist2(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
