package su.terrafirmagreg.core.compat.tfc.solar.meteor;

import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.compat.tfc.solar.SkyPos;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalculator;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.compat.tfc.solar.client.TFGMoonVisuals;

public final class FallingStarNightHelper {
    private static final long VANILLA_NIGHT_START = 13_000L;
    private static final long VANILLA_NIGHT_END = 23_000L;

    private FallingStarNightHelper() {
    }

    public static boolean isMeteorNight(Level level) {
        if (level.isRaining()) {
            return false;
        }
        if (!SolarCalendarBackport.isEnabled()) {
            final long time = level.getDayTime() % 24_000L;
            return time >= VANILLA_NIGHT_START && time <= VANILLA_NIGHT_END;
        }
        return getSunZenith(level) > Mth.HALF_PI;
    }

    public static int getNightTick(Level level) {
        if (!SolarCalendarBackport.isEnabled()) {
            final long time = level.getDayTime() % 24_000L;
            if (time < VANILLA_NIGHT_START || time > VANILLA_NIGHT_END) {
                return -1;
            }
            return (int) (time - VANILLA_NIGHT_START);
        }
        final float fractionOfDay = SolarCalendarBackport.getCalendarFractionOfDay(level);
        if (getSunZenith(level) <= Mth.HALF_PI) {
            return -1;
        }
        final double nightPortion = fractionOfDay >= 0.5F
                ? (fractionOfDay - 0.5F) / 0.5F
                : fractionOfDay / 0.5F;
        return Mth.clamp((int) (nightPortion * 10_000.0), 0, 10_000);
    }

    public static long getCalendarDay(Level level) {
        return Calendars.get(level).getCalendarTicks() / ICalendar.TICKS_IN_DAY;
    }

    public static int getMoonPhase(Level level) {
        final ICalendar calendar = Calendars.get(level);
        return SolarCalculator.getMoonPhase(calendar.getCalendarTicks(), TFGMoonVisuals.getMoonOrbitTicks(calendar));
    }

    private static float getSunZenith(Level level) {
        final float fractionOfDay = SolarCalendarBackport.getCalendarFractionOfDay(level);
        final float fractionOfYear = SolarCalendarBackport.getCalendarFractionOfYear(level);
        final SkyPos sunPos = SolarCalculator.getSunPosition(
                0,
                SolarCalculator.getHemisphereScale(level),
                fractionOfYear,
                fractionOfDay);
        return sunPos.zenith();
    }
}
