package su.terrafirmagreg.core.mixins.client.tfc.solar;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import su.terrafirmagreg.core.compat.tfc.solar.client.ShaderCelestialBridge;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CelestialUniforms", remap = false)
public class CelestialUniformsMixin {
    @Shadow
    @Final
    private float sunPathRotation;

    @Inject(method = "getSunPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfg$solarSunPosition(CallbackInfoReturnable<Vector4f> cir) {
        final Vector4f position = ShaderCelestialBridge.getSunViewPosition(sunPathRotation, ShaderCelestialBridge.getPartialTick());
        if (position != null) {
            cir.setReturnValue(position);
        }
    }

    @Inject(method = "getMoonPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfg$solarMoonPosition(CallbackInfoReturnable<Vector4f> cir) {
        final Vector4f position = ShaderCelestialBridge.getMoonViewPosition(sunPathRotation, ShaderCelestialBridge.getPartialTick());
        if (position != null) {
            cir.setReturnValue(position);
        }
    }
}
