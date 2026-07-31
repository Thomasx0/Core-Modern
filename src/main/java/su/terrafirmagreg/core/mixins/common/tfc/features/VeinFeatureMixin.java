package su.terrafirmagreg.core.mixins.common.tfc.features;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dries007.tfc.world.feature.vein.IVein;
import net.dries007.tfc.world.feature.vein.IVeinConfig;
import net.dries007.tfc.world.feature.vein.VeinFeature;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;

import su.terrafirmagreg.core.common.map.TFGVeinMetadataHelper;

@Mixin(value = VeinFeature.class, remap = false)
public abstract class VeinFeatureMixin {

    /**
     * {@link VeinFeature} has two {@code place} methods; inject into the protected chunk placement overload, not
     * {@code Feature#place(FeaturePlaceContext)}.
     */
    @Inject(method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/util/RandomSource;IILnet/dries007/tfc/world/feature/vein/IVein;Lnet/dries007/tfc/world/feature/vein/IVeinConfig;)V", at = @At("RETURN"))
    private void tfg$registerVeinInServerCache(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ,
            IVein vein, IVeinConfig veinConfig, CallbackInfo ci) {
        TFGVeinMetadataHelper.registerPlacedVein(level, vein.pos(), veinConfig);
    }
}
