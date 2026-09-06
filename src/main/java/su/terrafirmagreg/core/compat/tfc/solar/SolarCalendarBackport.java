package su.terrafirmagreg.core.compat.tfc.solar;

import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.config.TFGConfig;

public final class SolarCalendarBackport {
    private SolarCalendarBackport() {
    }

    public static boolean isEnabled() {
        return TFGConfig.SERVER.enableSolarCalendarBackport.get();
    }

    public static float defaultTickRateForNewWorlds() {
        return 20f / TFGConfig.SERVER.defaultCalendarDayLength.get();
    }

    public static float getCalendarTickRate(Level level) {
        if (!isEnabled()) {
            return 1f;
        }
        return getCalendarTickRate(Calendars.get(level));
    }

    public static float getCalendarTickRate(ICalendar calendar) {
        if (calendar instanceof CalendarExtension extension) {
            return extension.tfg$getCalendarTickRate();
        }
        return 1f;
    }

    public static double getSmoothCalendarTicks(ICalendar calendar, float partialTick) {
        double ticks = calendar.getCalendarTicks();
        if (calendar instanceof CalendarExtension extension) {
            ticks += extension.tfg$getCalendarPartialTick();
            ticks += extension.tfg$getCalendarTickRate() * partialTick;
        }
        return ticks;
    }

    public static float getCalendarFractionOfDay(Level level) {
        return getCalendarFractionOfDay(Calendars.get(level));
    }

    public static float getCalendarFractionOfDay(ICalendar calendar) {
        double ticks = getSmoothCalendarTicks(calendar, 0f);
        ticks %= ICalendar.TICKS_IN_DAY;
        if (ticks < 0) {
            ticks += ICalendar.TICKS_IN_DAY;
        }
        return (float) (ticks / (double) ICalendar.TICKS_IN_DAY);
    }

    public static float getCalendarFractionOfYear(Level level) {
        return Calendars.get(level).getCalendarFractionOfYear();
    }

    public static float getCalendarFractionOfMonth(Level level) {
        return Calendars.get(level).getCalendarFractionOfMonth();
    }
}
