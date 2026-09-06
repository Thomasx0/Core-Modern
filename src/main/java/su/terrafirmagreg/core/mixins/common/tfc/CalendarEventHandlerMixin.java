package su.terrafirmagreg.core.mixins.common.tfc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dries007.tfc.util.calendar.CalendarEventHandler;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;

import su.terrafirmagreg.core.utils.CalendarSleepHelper;

@Mixin(value = CalendarEventHandler.class, remap = false)
public abstract class CalendarEventHandlerMixin {

    /**
     * TFC 3 {@code onPlayerWakeUp} reads {@code getDayTime()} from the sleeping dimension.
     * {@link CalendarSleepHelper} replaces that path for solar backport and non-overworld legacy sleep.
     */
    @Inject(method = "onPlayerWakeUp", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tfg$onPlayerWakeUp(PlayerWakeUpEvent event, CallbackInfo ci) {
        if (CalendarSleepHelper.shouldCancelPlayerWakeUp(event)) {
            ci.cancel();
        }
    }
}
