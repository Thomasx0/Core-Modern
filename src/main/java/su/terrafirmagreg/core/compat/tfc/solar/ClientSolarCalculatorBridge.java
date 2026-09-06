package su.terrafirmagreg.core.compat.tfc.solar;

import net.dries007.tfc.client.ClientHelpers;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import su.terrafirmagreg.core.compat.tfc.solar.client.TFGMoonVisuals;

public final class ClientSolarCalculatorBridge {
    private ClientSolarCalculatorBridge() {
    }

    public static SkyPos getSunPositionSmooth(Level level, float partialTick) {
        return SolarCalculator.getSunPosition(
                getSmoothLatitudeZ(),
                SolarCalculator.getHemisphereScale(level),
                Calendars.CLIENT.getCalendarFractionOfYear(),
                getSmoothFractionOfDay(partialTick));
    }

    public static SkyPos getMoonPositionSmooth(Level level, float partialTick) {
        final double calendarTicks = getSmoothCalendarTicks(partialTick);
        final long tickFloor = (long) Math.floor(calendarTicks);
        final float blend = (float) (calendarTicks - tickFloor);
        final SkyPos from = SolarCalculator.getMoonPosition(
                getSmoothLatitudeZ(),
                SolarCalculator.getHemisphereScale(level),
                tickFloor,
                getMoonOrbitTicks());
        if (blend <= 0.0F) {
            return from;
        }
        final SkyPos to = SolarCalculator.getMoonPosition(
                getSmoothLatitudeZ(),
                SolarCalculator.getHemisphereScale(level),
                tickFloor + 1L,
                getMoonOrbitTicks());
        return SkyPos.of(
                Mth.lerp(blend, from.zenith(), to.zenith()),
                lerpAngle(from.azimuth(), to.azimuth(), blend));
    }

    private static float getSmoothLatitudeZ() {
        final Player player = ClientHelpers.getPlayer();
        return player != null ? (float) player.getZ() : 0.0F;
    }

    public static float getSmoothFractionOfDay(float partialTick) {
        double ticks = getSmoothCalendarTicks(partialTick) % ICalendar.TICKS_IN_DAY;
        if (ticks < 0.0) {
            ticks += ICalendar.TICKS_IN_DAY;
        }
        return (float) (ticks / (double) ICalendar.TICKS_IN_DAY);
    }

    private static double getSmoothCalendarTicks(float partialTick) {
        return SolarCalendarBackport.getSmoothCalendarTicks(Calendars.CLIENT, partialTick);
    }

    private static float lerpAngle(float from, float to, float blend) {
        float delta = Mth.wrapDegrees((to - from) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        return from + delta * blend;
    }

    public static long getDayTime(LevelAccessor maybeLevel) {
        if (maybeLevel instanceof Level level && level.dimension() == Level.OVERWORLD) {
            final Player player = ClientHelpers.getPlayer();
            if (player != null) {
                return Calendars.CLIENT.getTotalCalendarDays() * ICalendar.TICKS_IN_DAY
                        - (long) TFCConfig.COMMON.defaultCalendarStartDay.get() * ICalendar.TICKS_IN_DAY
                        + SolarCalculator.getSunBasedDayTime(
                                player.blockPosition().getZ(),
                                SolarCalculator.getHemisphereScale(level),
                                Calendars.CLIENT.getCalendarFractionOfYear(),
                                SolarCalendarBackport.getCalendarFractionOfDay(Calendars.CLIENT));
            }
        }
        return maybeLevel.getLevelData().getDayTime();
    }

    public static SkyPos getSunPosition(Level level, BlockPos pos) {
        return SolarCalculator.getSunPosition(
                pos.getZ(),
                SolarCalculator.getHemisphereScale(level),
                Calendars.CLIENT.getCalendarFractionOfYear(),
                SolarCalendarBackport.getCalendarFractionOfDay(Calendars.CLIENT));
    }

    public static int getMoonPhase(ICalendar calendar) {
        return SolarCalculator.getMoonPhase(calendar.getCalendarTicks(), getMoonOrbitTicks(calendar));
    }

    public static int getMoonPhase() {
        return getMoonPhase(Calendars.CLIENT);
    }

    public static SkyPos getMoonPosition(Level level, BlockPos pos) {
        return SolarCalculator.getMoonPosition(
                pos.getZ(),
                SolarCalculator.getHemisphereScale(level),
                Calendars.CLIENT.getCalendarTicks(),
                getMoonOrbitTicks(Calendars.CLIENT));
    }

    public static long getMoonOrbitTicks(ICalendar calendar) {
        return TFGMoonVisuals.getMoonOrbitTicks(calendar);
    }

    public static long getMoonOrbitTicks() {
        return getMoonOrbitTicks(Calendars.CLIENT);
    }

    public static SkyPos getStarPosition(Level level, BlockPos pos) {
        return SolarCalculator.getStarPosition(
                pos.getZ(),
                SolarCalculator.getHemisphereScale(level),
                SolarCalendarBackport.getCalendarFractionOfDay(Calendars.CLIENT),
                Calendars.CLIENT.getCalendarFractionOfYear());
    }
}
