package su.terrafirmagreg.core.mixins.common.gtceu.map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.integration.map.cache.server.ServerCache;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.common.map.TFGProspectMapSync;
import su.terrafirmagreg.core.common.map.TFGVeinMetadataHelper;

@Mixin(value = ServerCache.class, remap = false)
public abstract class ServerCacheMixin {

    /**
     * Before the GT prospector reads the (possibly empty) cache, scan the chunk for TFC/GT ores and register any
     * clusters as {@code GeneratedVeinMetadata} in {@link ServerCache}.
     */
    @Inject(method = "prospectAllInChunk", at = @At("HEAD"))
    private void tfg$backfillChunkVeins(ResourceKey<Level> dim, ChunkPos pos, ServerPlayer player, CallbackInfo ci) {
        TFGVeinMetadataHelper.backfillChunk(player.serverLevel(), pos);
    }

    /** After GT sends vein data to the client cache, push the unified map marker packet with block counts. */
    @Inject(method = "prospectAllInChunk", at = @At("RETURN"))
    private void tfg$syncChunkMapMarkers(ResourceKey<Level> dim, ChunkPos pos, ServerPlayer player, CallbackInfo ci) {
        TFGProspectMapSync.syncChunkOres(player, pos);
    }

    /**
     * Ore block right-click always uses hand-prospect behaviour (single block, merged map marker, one GT chat line).
     * {@code prospectByOreMaterial} is only invoked from {@code OreBlock#use}, not from electric prospectors.
     */
    @Inject(method = "prospectByOreMaterial", at = @At("HEAD"), cancellable = true)
    private void tfg$oreBlockProspect(ResourceKey<Level> dim, Material material, BlockPos origin,
            ServerPlayer player, int radius, CallbackInfo ci) {
        TFGProspectMapSync.handProspectOreBlock(player, player.serverLevel(), origin, material);
        ci.cancel();
    }

    @Inject(method = "prospectBySurfaceRockMaterial", at = @At("RETURN"))
    private void tfg$syncSurfaceRockMapMarkers(ResourceKey<Level> dim, Material material, BlockPos pos,
            ServerPlayer player, int radius, CallbackInfo ci) {
        if (radius >= 0) {
            TFGProspectMapSync.syncDiscoveredVeinsBySurfaceRock(player, pos, radius, material);
        }
    }

    @Inject(method = "prospectByDepositName", at = @At("RETURN"))
    private void tfg$syncDepositMapMarkers(ResourceKey<Level> dim, String depositName, BlockPos origin,
            ServerPlayer player, int radius, CallbackInfo ci) {
        if (radius >= 0) {
            TFGProspectMapSync.syncDiscoveredVeinsByDeposit(player, origin, radius, depositName);
        }
    }
}
