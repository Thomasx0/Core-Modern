package su.terrafirmagreg.core.mixins.common.tfc.solar;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.utils.CalendarSleepHelper;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSleepMixin {
    @Inject(method = "startSleepInBed", at = @At("RETURN"), cancellable = true)
    private void tfg$startSleepInBed(BlockPos pos, CallbackInfoReturnable<Optional<Player.BedSleepingProblem>> cir) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        final Optional<Player.BedSleepingProblem> result = cir.getReturnValue();
        if (result.isEmpty()) {
            if (!CalendarSleepHelper.canSleepNow()) {
                cir.setReturnValue(Optional.of(Player.BedSleepingProblem.NOT_POSSIBLE_NOW));
            }
            return;
        }
        if (result.get() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW && CalendarSleepHelper.canSleepNow()) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
