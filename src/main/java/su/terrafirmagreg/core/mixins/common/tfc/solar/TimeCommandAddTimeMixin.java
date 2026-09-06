package su.terrafirmagreg.core.mixins.common.tfc.solar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.dries007.tfc.common.commands.TimeCommand;
import net.dries007.tfc.util.calendar.Calendars;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarDebug;

@Mixin(value = TimeCommand.class, remap = false)
public class TimeCommandAddTimeMixin {
    @Inject(method = "addTime", at = @At("HEAD"), remap = false)
    private static void tfg$logVanillaTfcAddTime(long ticksToAdd, CallbackInfoReturnable<Integer> cir) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        SolarCalendarDebug.log(
                "WARNING: vanilla TFC TimeCommand.addTime() invoked with {} ticks (calendar before={}). "
                        + "This means TFG /time was NOT used.",
                ticksToAdd,
                Calendars.SERVER.getCalendarTicks());
    }
}
