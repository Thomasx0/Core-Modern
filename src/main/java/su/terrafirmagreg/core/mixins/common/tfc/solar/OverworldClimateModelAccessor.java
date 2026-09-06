package su.terrafirmagreg.core.mixins.common.tfc.solar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.dries007.tfc.util.climate.OverworldClimateModel;

@Mixin(value = OverworldClimateModel.class, remap = false)
public interface OverworldClimateModelAccessor {
    @Accessor("temperatureScale")
    float tfg$getTemperatureScale();
}
