package su.terrafirmagreg.core.mixins.client.tfc.solar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dries007.tfc.client.ClientCalendar;
import net.minecraft.client.Minecraft;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.mixins.common.tfc.solar.CalendarMixin;

@Mixin(value = ClientCalendar.class, remap = false)
public abstract class ClientCalendarMixin {
    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfg$onClientTick(CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        ci.cancel();
        if (!Minecraft.getInstance().isPaused()) {
            final CalendarMixin calendar = (CalendarMixin) (Object) this;
            calendar.tfg$addPlayerTicks(1);
            calendar.tfg$advanceCalendarTick();
        }
    }
}
