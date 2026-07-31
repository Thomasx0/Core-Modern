package su.terrafirmagreg.core.mixins.common.gtceu.kjs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gregtechceu.gtceu.integration.kjs.events.GTOreVeinEventJS;

import su.terrafirmagreg.core.common.map.TFGOreVeinDefinitions;

/**
 * KubeJS {@code GTCEuServerEvents.oreVeins} calls {@code event.removeAll()} in the pack, which wipes
 * {@link com.gregtechceu.gtceu.api.registry.GTRegistries#ORE_VEINS}. Re-register TFG veins immediately after.
 */
@Mixin(value = GTOreVeinEventJS.class, remap = false)
public abstract class GTOreVeinEventJSMixin {

    @Inject(method = "removeAll()V", at = @At("RETURN"))
    private void tfg$afterRemoveAll(CallbackInfo ci) {
        TFGOreVeinDefinitions.reregisterAfterKubeJsClear();
    }

    @Inject(method = "removeAll(Ljava/util/function/BiPredicate;)V", at = @At("RETURN"), remap = false)
    private void tfg$afterRemoveAllFiltered(java.util.function.BiPredicate<?, ?> predicate, CallbackInfo ci) {
        TFGOreVeinDefinitions.reregisterAfterKubeJsClear();
    }
}
