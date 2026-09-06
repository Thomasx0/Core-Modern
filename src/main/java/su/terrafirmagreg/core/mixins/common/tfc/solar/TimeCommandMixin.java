package su.terrafirmagreg.core.mixins.common.tfc.solar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.dries007.tfc.common.commands.TimeCommand;
import net.minecraft.commands.CommandSourceStack;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarDebug;
import su.terrafirmagreg.core.compat.tfc.solar.command.TFGTimeCommand;

@Mixin(value = TimeCommand.class, remap = false)
public class TimeCommandMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tfg$create(CallbackInfoReturnable<LiteralArgumentBuilder<CommandSourceStack>> cir) {
        if (SolarCalendarBackport.isEnabled()) {
            SolarCalendarDebug.log("TimeCommandMixin redirected TFC TimeCommand.create() to TFGTimeCommand");
            cir.setReturnValue(TFGTimeCommand.create());
        } else {
            SolarCalendarDebug.log("TimeCommandMixin skipped: backport disabled");
        }
    }
}
