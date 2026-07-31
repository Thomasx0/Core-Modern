package su.terrafirmagreg.core.mixins.client.gtceu.map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gregtechceu.gtceu.integration.map.xaeros.worldmap.ore.OreVeinElement;

/**
 * TFG ore markers live on the {@code ore_veins} overlay only - not as Xaero custom waypoints.
 */
@Mixin(value = OreVeinElement.class, remap = false)
public abstract class OreVeinElementMixin {

    @Inject(method = "onMouseSelect", at = @At("HEAD"), cancellable = true)
    private void tfg$skipOreVeinWaypoint(CallbackInfo ci) {
        ci.cancel();
    }
}
