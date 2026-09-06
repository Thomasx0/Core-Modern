package su.terrafirmagreg.core.mixins.common.tfc.solar;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dries007.tfc.mixin.accessor.GameRulesAccessor;
import net.dries007.tfc.mixin.accessor.GameRulesTypeAccessor;
import net.dries007.tfc.util.ReentrantListener;
import net.dries007.tfc.util.calendar.Calendar;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.calendar.ServerCalendar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.server.ServerLifecycleHooks;

import su.terrafirmagreg.core.compat.tfc.solar.CalendarExtension;
import su.terrafirmagreg.core.compat.tfc.solar.ServerCalendarExtension;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarDebug;

@Mixin(value = ServerCalendar.class, remap = false)
public abstract class ServerCalendarMixin implements ServerCalendarExtension {
    @Unique
    private static final ReentrantListener TFG_DO_DAYLIGHT_CYCLE = new ReentrantListener(ServerCalendarMixin::tfg$forceDoDaylightCycleOff);

    @Unique
    private static void tfg$forceDoDaylightCycleOff() {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            TFG_DO_DAYLIGHT_CYCLE.runWithoutTriggeringCallbacks(() -> server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server));
        }
        Calendars.SERVER.setDoDaylightCycle();
    }

    @Shadow
    void sendUpdatePacket() {
    }

    @Shadow
    private void setDoDaylightCycleWithNoCallback(boolean value) {
    }

    @Shadow
    void checkIfInTheFuture(ServerLevel level) {
    }

    @Inject(method = "overrideDoDaylightCycleCallback", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tfg$overrideDoDaylightCycleCallback(CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        ci.cancel();
        final GameRulesTypeAccessor type = (GameRulesTypeAccessor) GameRulesAccessor.accessor$getGameRuleTypes().get(GameRules.RULE_DAYLIGHT);
        type.accessor$setCallback(type.accessor$getCallback().andThen((server, t) -> TFG_DO_DAYLIGHT_CYCLE.onListenerUpdate()));
    }

    @Inject(method = "onServerStart", at = @At("TAIL"), remap = false)
    private void tfg$onServerStart(CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        ((CalendarMixin) (Object) this).tfg$setDoDaylightCycle(false);
        setDoDaylightCycleWithNoCallback(false);
        tfg$updateDayTime(ServerLifecycleHooks.getCurrentServer().overworld());
    }

    @Inject(method = "onOverworldTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfg$onOverworldTick(ServerLevel level, CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        ci.cancel();
        final CalendarMixin calendar = (CalendarMixin) (Object) this;
        if (calendar.tfg$getArePlayersLoggedOn()) {
            calendar.tfg$advanceCalendarTick();
            if ((calendar.tfg$getCalendarTicksField() & 0x100) == 0) {
                checkIfInTheFuture(level);
            }
        }
        tfg$updateDayTime(level);
    }

    @Inject(method = "setMonthLength", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfg$setMonthLength(int newMonthLength, CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        ci.cancel();
        tfg$applyMonthLength(newMonthLength);
    }

    @Inject(method = "setTimeFromCalendarTime", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfg$setTimeFromCalendarTime(long calendarTimeToSetTo, CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        ci.cancel();
        final CalendarMixin calendar = (CalendarMixin) (Object) this;
        final CalendarExtension extension = (CalendarExtension) this;
        final long timeJump = calendarTimeToSetTo - calendar.tfg$getCalendarTicksField();
        calendar.tfg$setCalendarTicksField(calendarTimeToSetTo);
        calendar.tfg$addPlayerTicks(extension.tfg$getCalendarTickRate() == 0 ? 0 : (long) (timeJump / extension.tfg$getCalendarTickRate()));
        tfg$updateDayTime(ServerLifecycleHooks.getCurrentServer().overworld());
        tfg$publishCalendarToClients();
    }

    @Inject(method = "setPlayersLoggedOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfg$setPlayersLoggedOn(boolean arePlayersLoggedOn, CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        ci.cancel();
        final boolean alwaysRun = !net.dries007.tfc.config.TFCConfig.SERVER.enableTimeStopWhenServerEmpty.get();
        ((CalendarMixin) (Object) this).tfg$setArePlayersLoggedOn(arePlayersLoggedOn || alwaysRun);
        tfg$publishCalendarToClients();
    }

    @Inject(method = "setDoDaylightCycle", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfg$setDoDaylightCycle(CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        ci.cancel();
        ((CalendarMixin) (Object) this).tfg$setDoDaylightCycle(false);
        setDoDaylightCycleWithNoCallback(false);
        tfg$publishCalendarToClients();
    }

    private void tfg$updateDayTime(ServerLevel level) {
        level.setDayTime(((CalendarMixin) (Object) this).tfg$getCalendarTicksField() + 18_000L);
    }

    @Override
    public void tfg$skipForwardBy(long calendarTicksToSkip) {
        final CalendarMixin calendar = (CalendarMixin) (Object) this;
        final CalendarExtension extension = (CalendarExtension) this;
        final long before = calendar.tfg$getCalendarTicksField();
        SolarCalendarDebug.log("tfg$skipForwardBy: before={}, skip={}, tickRate={}",
                before, calendarTicksToSkip, extension.tfg$getCalendarTickRate());
        calendar.tfg$addCalendarTicksField(calendarTicksToSkip);
        calendar.tfg$addPlayerTicks(extension.tfg$getCalendarTickRate() == 0 ? 0 : (long) (calendarTicksToSkip / extension.tfg$getCalendarTickRate()));
        tfg$updateDayTime(ServerLifecycleHooks.getCurrentServer().overworld());
        tfg$publishCalendarToClients();
        SolarCalendarDebug.log("tfg$skipForwardBy: after={}, client={}",
                calendar.tfg$getCalendarTicksField(), Calendars.CLIENT.getCalendarTicks());
    }

    @Override
    public void tfg$setMonthLength(int newMonthLength) {
        tfg$applyMonthLength(newMonthLength);
    }

    @Override
    public void tfg$publishCalendarToClients() {
        sendUpdatePacket();
        tfg$mirrorToLocalClient();
    }

    @Unique
    private void tfg$applyMonthLength(int newMonthLength) {
        final CalendarMixin calendar = (CalendarMixin) (Object) this;
        final long calendarTicks = calendar.tfg$getCalendarTicksField();
        final int oldDaysInMonth = calendar.tfg$getDaysInMonth();
        final long baseMonths = calendarTicks / ((long) oldDaysInMonth * ICalendar.TICKS_IN_DAY);
        final float baseFractionOfMonth = Calendars.SERVER.getCalendarFractionOfMonth();
        final long baseDayTime = calendarTicks % ICalendar.TICKS_IN_DAY;

        calendar.tfg$setDaysInMonth(newMonthLength);
        calendar.tfg$setCalendarTicksField(
                baseMonths * (long) newMonthLength * ICalendar.TICKS_IN_DAY
                        + (long) (baseFractionOfMonth * newMonthLength) * ICalendar.TICKS_IN_HOUR
                        + baseDayTime);
        tfg$updateDayTime(ServerLifecycleHooks.getCurrentServer().overworld());
        tfg$publishCalendarToClients();
    }

    @Unique
    private void tfg$mirrorToLocalClient() {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        Calendars.CLIENT.resetTo((Calendar) (Object) this);
        SolarCalendarDebug.log("mirrored server calendar to client: server={}, client={}",
                ((CalendarMixin) (Object) this).tfg$getCalendarTicksField(), Calendars.CLIENT.getCalendarTicks());
    }
}
