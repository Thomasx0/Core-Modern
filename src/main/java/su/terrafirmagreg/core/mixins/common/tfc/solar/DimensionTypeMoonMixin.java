package su.terrafirmagreg.core.mixins.common.tfc.solar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.world.level.dimension.DimensionType;

import su.terrafirmagreg.core.compat.tfc.solar.ClientSolarCalculatorBridge;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;

@Mixin(value = DimensionType.class, priority = 900)
public abstract class DimensionTypeMoonMixin {
    @Inject(method = "moonPhase", at = @At("HEAD"), cancellable = true)
    private void tfg$moonPhase(long dayTime, CallbackInfoReturnable<Integer> cir) {
        if (SolarCalendarBackport.isEnabled()) {
            cir.setReturnValue(ClientSolarCalculatorBridge.getMoonPhase(Calendars.get()));
        }
    }
}
