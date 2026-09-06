package su.terrafirmagreg.core.compat.tfc.solar.client;

import net.dries007.tfc.util.calendar.ICalendar;
import net.minecraft.util.Mth;

import su.terrafirmagreg.core.config.TFGConfig;

public final class TFGMoonVisuals {
    public static final double SYNODIC_MONTH_DAYS = 29.530588;
    public static final double AVERAGE_CALENDAR_DAYS_PER_MONTH = 30.436875;
    public static final float SUN_BASE_SIZE = 30.0F;
    public static final float MOON_BASE_SIZE = 20.0F;

    private TFGMoonVisuals() {
    }

    public static long getMoonOrbitTicks(ICalendar calendar) {
        final double orbitDays = calendar.getCalendarDaysInMonth()
                * (SYNODIC_MONTH_DAYS / AVERAGE_CALENDAR_DAYS_PER_MONTH);
        return Math.max(1L, Math.round(orbitDays * ICalendar.TICKS_IN_DAY));
    }

    public static double supermoonCycle(long calendarTicks, long orbitTicks) {
        return 1.0 + 0.07 * Math.sin(Mth.TWO_PI * calendarTicks / orbitTicks);
    }

    public static float getSunSize() {
        return SUN_BASE_SIZE * TFGConfig.CLIENT.sunScale.get().floatValue();
    }

    public static float getMoonSize(long calendarTicks, long orbitTicks) {
        return (float) (MOON_BASE_SIZE * supermoonCycle(calendarTicks, orbitTicks)
                * TFGConfig.CLIENT.moonScale.get());
    }

    public static float getSunCoverSize() {
        return getSunSize() * (7.5F / SUN_BASE_SIZE);
    }
}
