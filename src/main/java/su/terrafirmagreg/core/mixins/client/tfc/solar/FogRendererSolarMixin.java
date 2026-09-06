package su.terrafirmagreg.core.mixins.client.tfc.solar;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.compat.tfc.solar.client.TFGSkyVisuals;

@Mixin(FogRenderer.class)
public abstract class FogRendererSolarMixin {

    @Redirect(method = "setupColor", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;dot(Lorg/joml/Vector3fc;)F"))
    private static float tfg$directionalSunriseFogFacing(Vector3f lookVector, Vector3fc axis) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (SolarCalendarBackport.isEnabled() && level != null && level.dimension() == Level.OVERWORLD) {
            return TFGSkyVisuals.computeHorizonSunFacing(
                    lookVector,
                    level,
                    minecraft.gameRenderer.getMainCamera().getBlockPosition(),
                    minecraft.getPartialTick());
        }
        return lookVector.dot(axis);
    }

    @Redirect(method = "setupColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getSunriseColor(FF)[F"))
    private static float[] tfg$solarSunriseFogColor(
            DimensionSpecialEffects effects,
            float timeOfDay,
            float partialTicks) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (SolarCalendarBackport.isEnabled() && level != null && level.dimension() == Level.OVERWORLD) {
            return TFGSkyVisuals.getSunriseColor(
                    level,
                    minecraft.gameRenderer.getMainCamera().getBlockPosition(),
                    partialTicks);
        }
        return effects.getSunriseColor(timeOfDay, partialTicks);
    }
}
