package su.terrafirmagreg.core.mixins.client.tfc.solar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.compat.tfc.solar.ClientSolarCalculatorBridge;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;

@Mixin(value = Level.class, priority = 1001)
public abstract class LevelDayTimeMixin {
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void tfg$getSolarAdjustedDayTimeOnClient(CallbackInfoReturnable<Long> cir) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        final Level level = (Level) (Object) this;
        if (level.isClientSide()) {
            cir.setReturnValue(ClientSolarCalculatorBridge.getDayTime(level));
        }
    }
}
