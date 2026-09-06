package su.terrafirmagreg.core.mixins.client.tfc.solar;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.compat.tfc.solar.client.ShaderPackDetection;
import su.terrafirmagreg.core.compat.tfc.solar.client.TFGSkyRenderer;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyMixin {
    // After FogRenderer.levelFogColor() so Iris/Oculus enters the SKY rendering phase first.
    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FogRenderer;levelFogColor()V", shift = At.Shift.AFTER), cancellable = true)
    private void tfg$renderSky(
            PoseStack poseStack,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean isFoggy,
            Runnable skyFogSetup,
            CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled() || ShaderPackDetection.shouldUseVanillaSky()) {
            return;
        }
        if (!(camera.getEntity().level() instanceof ClientLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (TFGSkyRenderer.renderSky(level, poseStack, projectionMatrix, partialTick, camera, isFoggy, skyFogSetup)) {
            ci.cancel();
        }
    }
}
