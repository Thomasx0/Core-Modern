/*
 * Unified TFC sleep calendar handling:
 * - Solar backport (TFC 4): all dimensions, fraction-of-day window and wake time.
 * - Legacy (TFC 3 fix): non-overworld only, when solar backport is disabled.
 */
package su.terrafirmagreg.core.utils;

import net.dries007.tfc.common.capabilities.food.TFCFoodData;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarActions;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;

public final class CalendarSleepHelper {

    /** TFC 4 sleep window: 8 pm – 5 am, expressed as fraction of day. */
    private static final float SOLAR_SLEEP_TIME = 20f / 24f;
    private static final float SOLAR_WAKE_TIME = 5f / 24f;

    /** TFC 3 morning after sleep ({@code Level#getDayTime()} where {@code 0 = 6:00 AM}). */
    private static final long LEGACY_WAKE_UP_DAY_TIME = 0L;

    private CalendarSleepHelper() {
    }

    /**
     * Replaces TFC 3 {@code CalendarEventHandler.onPlayerWakeUp} when a dedicated handler owns the time skip.
     */
    public static boolean shouldCancelPlayerWakeUp(PlayerWakeUpEvent event) {
        if (SolarCalendarBackport.isEnabled()) {
            return true;
        }
        return !event.getEntity().getCommandSenderWorld().dimension().equals(Level.OVERWORLD);
    }

    /**
     * Replaces vanilla/TFC bed-time checks while solar calendar backport is active.
     */
    public static boolean canSleepNow() {
        final float time = SolarCalendarBackport.getCalendarFractionOfDay(Calendars.SERVER);
        return time >= SOLAR_SLEEP_TIME || time <= SOLAR_WAKE_TIME;
    }

    /**
     * Advances the TFC calendar after all sleeping players wake up.
     */
    public static void onPlayersFinishedSleeping(ServerLevel level) {
        if (SolarCalendarBackport.isEnabled()) {
            advanceSolarCalendarAfterSleep(level);
        } else {
            advanceLegacyNonOverworldCalendarAfterSleep(level);
        }
    }

    private static void advanceSolarCalendarAfterSleep(ServerLevel level) {
        final float currentFractionOfDay = SolarCalendarBackport.getCalendarFractionOfDay(level);
        final float targetFraction = SOLAR_WAKE_TIME > currentFractionOfDay
                ? SOLAR_WAKE_TIME
                : 1 + SOLAR_WAKE_TIME;
        final int calendarTicksSlept = (int) ((targetFraction - currentFractionOfDay) * ICalendar.TICKS_IN_DAY);

        SolarCalendarActions.skipForwardBy(calendarTicksSlept);

        final float exhaustion = calendarTicksSlept / Math.max(SolarCalendarBackport.getCalendarTickRate(Calendars.SERVER), 1e-6f)
                * TFCFoodData.PASSIVE_EXHAUSTION_PER_TICK
                * TFCConfig.SERVER.passiveExhaustionModifier.get().floatValue();

        applyExhaustion(level, exhaustion);
    }

    /**
     * TFC 3 only syncs calendar time from {@code PlayerWakeUpEvent} using the sleeping dimension's
     * {@code getDayTime()}, which does not change in Nether/Beneath when {@code doDaylightCycle} is off.
     */
    private static void advanceLegacyNonOverworldCalendarAfterSleep(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) {
            return;
        }

        var calendar = Calendars.SERVER;
        long currentDayTime = calendar.getCalendarDayTime();
        if (currentDayTime == LEGACY_WAKE_UP_DAY_TIME) {
            return;
        }

        long jump = LEGACY_WAKE_UP_DAY_TIME - currentDayTime;
        if (jump <= 0) {
            jump += ICalendar.TICKS_IN_DAY;
        }

        calendar.setTimeFromCalendarTime(calendar.getCalendarTicks() + jump);

        float exhaustion = jump * TFCFoodData.PASSIVE_EXHAUSTION_PER_TICK
                * TFCConfig.SERVER.passiveExhaustionModifier.get().floatValue();
        applyExhaustion(level, exhaustion);
    }

    private static void applyExhaustion(ServerLevel level, float exhaustion) {
        for (ServerPlayer player : level.players()) {
            player.causeFoodExhaustion(exhaustion);
        }
    }
}
