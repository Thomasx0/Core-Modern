package su.terrafirmagreg.core.common.map;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.integration.map.cache.server.ServerCache;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.PacketDistributor;

import su.terrafirmagreg.core.network.TFGNetworkHandler;
import su.terrafirmagreg.core.network.packet.OreHighlightVeinPacket;

/**
 * Single server-side path for map vein markers: {@link OreHighlightVeinPacket} with per-ore block counts.
 * Used by TFG prospectors (player-relative scan volume) and GT electric prospectors (exact world block counts
 * around each cached vein center, same data the GT GUI derives from chunk scans).
 */
public final class TFGProspectMapSync {

    private static final TagKey<Block> ORE_TAG = Tags.Blocks.ORES;

    private TFGProspectMapSync() {
    }

    /** Ore block right-click — map marker + single GT chat notification (any item in hand). */
    public static void handProspectOreBlock(ServerPlayer player, ServerLevel level, BlockPos pos, Material material) {
        syncSingleClickedOre(player, level, pos);
        TFGVeinMetadataHelper.notifyHandClickedOre(player, level, pos, material);
    }

    /** GT ore block right-click with any item in hand — mark only the clicked block, no counts. */
    public static void syncSingleClickedOre(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ORE_TAG)) {
            return;
        }
        syncOrePositions(player, level, List.of(pos), ProspectMode.HAND, 0, null, null, false);
    }

    /** TFG propicks and geological hammer — clusters from a directional scan volume. */
    public static void syncOrePositions(ServerPlayer player, ServerLevel level, List<BlockPos> orePositions,
            ProspectMode mode, int scanRange, @Nullable BlockPos scanMin, @Nullable BlockPos scanMax) {
        syncOrePositions(player, level, orePositions, mode, scanRange, scanMin, scanMax, true);
    }

    public static void syncOrePositions(ServerPlayer player, ServerLevel level, List<BlockPos> orePositions,
            ProspectMode mode, int scanRange, @Nullable BlockPos scanMin, @Nullable BlockPos scanMax,
            boolean showParticles) {
        List<List<BlockPos>> clusters = TFGVeinMetadataHelper.clusterByRadius(orePositions);
        List<TFGProspectVeinMarker> markers = new ArrayList<>(clusters.size());

        for (List<BlockPos> cluster : clusters) {
            TFGProspectVeinMarker marker = markerFromCluster(level, cluster, null, scanRange, mode);
            if (marker != null) {
                markers.add(marker);
            }
        }

        send(player, markers, scanMin, scanMax, showParticles);
    }

    /**
     * GT electric prospector — map markers from actual ore blocks in the scanned chunk (matches the GUI counts),
     * not only {@link ServerCache} vein centers that happen to fall inside the chunk.
     */
    public static void syncChunkOres(ServerPlayer player, ChunkPos chunkPos) {
        ServerLevel level = player.serverLevel();
        BlockPos scanMin = new BlockPos(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ());
        BlockPos scanMax = new BlockPos(chunkPos.getMaxBlockX(), level.getMaxBuildHeight(), chunkPos.getMaxBlockZ());
        List<BlockPos> orePositions = TFGVeinMetadataHelper.collectOreBlocksInChunk(level, chunkPos);
        if (orePositions.isEmpty()) {
            send(player, List.of(), scanMin, scanMax, false);
            return;
        }
        syncOrePositions(player, level, orePositions, ProspectMode.WITH_COUNTS, ProspectMode.CLUSTER_RADIUS * 2, scanMin,
                scanMax, false);
    }

    /** Surface rock — same vein set as GT chat ({@link ServerCache#prospectBySurfaceRockMaterial}). */
    public static void syncDiscoveredVeinsBySurfaceRock(ServerPlayer player, BlockPos origin, int blockRadius,
            Material material) {
        ServerLevel level = player.serverLevel();
        syncDiscoveredVeins(player, origin, blockRadius,
                TFGVeinMetadataHelper.filterVeinsBySurfaceRock(level.dimension(), origin, blockRadius, material));
    }

    /** Deposit sample — same vein set as GT chat ({@link ServerCache#prospectByDepositName}). */
    public static void syncDiscoveredVeinsByDeposit(ServerPlayer player, BlockPos origin, int blockRadius,
            String depositName) {
        ServerLevel level = player.serverLevel();
        syncDiscoveredVeins(player, origin, blockRadius,
                TFGVeinMetadataHelper.filterVeinsByDepositName(level.dimension(), origin, blockRadius, depositName));
    }

    /**
     * Surface rock / deposit name — vein names only (same as GT chat), centered on cached vein metadata.
     */
    private static void syncDiscoveredVeins(ServerPlayer player, BlockPos origin, int blockRadius,
            List<GeneratedVeinMetadata> veins) {
        List<TFGProspectVeinMarker> markers = new ArrayList<>();
        for (GeneratedVeinMetadata vein : veins) {
            if (!TFGOreVeinLang.isTfgVein(vein.id())) {
                continue;
            }
            List<Material> materials = vein.definition().veinGenerator().getAllMaterials();
            if (materials.isEmpty()) {
                continue;
            }
            markers.add(new TFGProspectVeinMarker(
                    vein.center(),
                    List.of(new TFGProspectVeinMarker.OreCount(TFGOreVeinLang.displayName(vein.id()), 0)),
                    TFGVeinMetadataHelper.materialIds(materials),
                    0));
        }
        BlockPos scanMin = origin.offset(-blockRadius, -blockRadius, -blockRadius);
        BlockPos scanMax = origin.offset(blockRadius, blockRadius, blockRadius);
        send(player, markers, scanMin, scanMax, false);
    }

    private static @Nullable TFGProspectVeinMarker markerFromCluster(Level level, List<BlockPos> cluster,
            @Nullable BlockPos center, int scanRange, ProspectMode mode) {
        List<TFGProspectVeinMarker.OreCount> ores = switch (mode) {
            case WITH_COUNTS -> TFGVeinMetadataHelper.countOresByBlockName(level, cluster);
            case HAND, NAMES_ONLY -> TFGVeinMetadataHelper.uniqueOreNames(level, cluster);
        };
        if (ores.isEmpty()) {
            return null;
        }
        List<Material> materials = TFGVeinMetadataHelper.orderedMaterials(level, cluster);
        if (materials.isEmpty()) {
            return null;
        }
        BlockPos markerCenter = center != null ? center : TFGVeinMetadataHelper.averageBlockPos(cluster);
        return new TFGProspectVeinMarker(markerCenter, ores, TFGVeinMetadataHelper.materialIds(materials), scanRange);
    }

    private static void send(ServerPlayer player, List<TFGProspectVeinMarker> markers, @Nullable BlockPos scanMin,
            @Nullable BlockPos scanMax, boolean showParticles) {
        TFGNetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OreHighlightVeinPacket(markers, scanMin, scanMax, showParticles));
    }
}
