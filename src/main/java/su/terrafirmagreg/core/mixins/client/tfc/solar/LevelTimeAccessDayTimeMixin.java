package su.terrafirmagreg.core.mixins.client.tfc.solar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import net.dries007.tfc.client.ClientHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelTimeAccess;

import su.terrafirmagreg.core.compat.tfc.solar.ClientSolarCalculatorBridge;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.compat.tfc.solar.client.ShaderCelestialBridge;
import su.terrafirmagreg.core.compat.tfc.solar.client.ShaderPackDetection;
import su.terrafirmagreg.core.compat.tfc.solar.client.TFGSkyVisuals;

@Mixin(LevelTimeAccess.class)
public interface LevelTimeAccessDayTimeMixin {
    /**
     * Interface default methods cannot use {@code @Inject} in Mixin 0.8.5.
     */
    @Overwrite(remap = true)
    default long dayTime() {
        if (SolarCalendarBackport.isEnabled() && this instanceof LevelAccessor accessor && accessor.isClientSide()) {
            return ClientSolarCalculatorBridge.getDayTime(accessor);
        }
        if (this instanceof LevelAccessor accessor) {
            return accessor.getLevelData().getDayTime();
        }
        throw new IllegalStateException("Unexpected LevelTimeAccess implementor: " + this.getClass());
    }

    /**
     * Used by Oculus {@code CelestialUniforms} for smooth sun/moon angle each frame.
     */
    @Overwrite(remap = true)
    default float getTimeOfDay(float partialTick) {
        if (SolarCalendarBackport.isEnabled() && this instanceof Level level && level.isClientSide()) {
            if (level.dimension() == Level.OVERWORLD) {
                final BlockPos pos = ClientHelpers.getPlayer() != null
                        ? ClientHelpers.getPlayer().blockPosition()
                        : BlockPos.ZERO;
                if (ShaderPackDetection.isShaderPackActive()) {
                    final float skyAngle = ShaderCelestialBridge.getTimeOfDayForShaders(level, partialTick);
                    if (skyAngle >= 0.0F) {
                        return skyAngle;
                    }
                }
                return (float) TFGSkyVisuals.apparentTimeOfDay(level, pos, partialTick);
            }
        }
        if (this instanceof LevelAccessor accessor) {
            return accessor.dimensionType().timeOfDay(this.dayTime());
        }
        throw new IllegalStateException("Unexpected LevelTimeAccess implementor: " + this.getClass());
    }

    @Overwrite(remap = true)
    default int getMoonPhase() {
        if (SolarCalendarBackport.isEnabled() && this instanceof Level level && level.isClientSide()) {
            if (level.dimension() == Level.OVERWORLD) {
                return ClientSolarCalculatorBridge.getMoonPhase();
            }
        }
        if (this instanceof LevelAccessor accessor) {
            return accessor.dimensionType().moonPhase(this.dayTime());
        }
        throw new IllegalStateException("Unexpected LevelTimeAccess implementor: " + this.getClass());
    }
}
