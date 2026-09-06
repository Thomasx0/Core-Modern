package su.terrafirmagreg.core.compat.tfc.solar;

import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.climate.ClimateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.mixins.common.tfc.solar.OverworldClimateModelAccessor;

public final class SolarCalculator {
    private SolarCalculator() {
    }

    public static int getSunBasedDayTime(float z, float hemisphereScale, float fractionOfYear, float fractionOfDay) {
        final float zenith = getSunPosition(z, hemisphereScale, fractionOfYear, fractionOfDay).zenith();
        if (fractionOfDay < 0.5) {
            if (zenith > Mth.HALF_PI) {
                final float minZenith = getSunPosition(z, hemisphereScale, fractionOfYear, 0f).zenith();
                return (int) Mth.clampedMap(zenith, minZenith, Mth.HALF_PI, 18_000, 24_000);
            } else {
                final float maxZenith = getSunPosition(z, hemisphereScale, fractionOfYear, 0.5f).zenith();
                return (int) Mth.clampedMap(zenith, Mth.HALF_PI, maxZenith, 0, 6_000);
            }
        } else {
            if (zenith < Mth.HALF_PI) {
                final float maxZenith = getSunPosition(z, hemisphereScale, fractionOfYear, 0.5f).zenith();
                return (int) Mth.clampedMap(zenith, maxZenith, Mth.HALF_PI, 6_000, 12_000);
            } else {
                final float minZenith = getSunPosition(z, hemisphereScale, fractionOfYear, 1f).zenith();
                return (int) Mth.clampedMap(zenith, Mth.HALF_PI, minZenith, 12_000, 18_000);
            }
        }
    }

    public static SkyPos getSunPosition(float z, float hemisphereScale, float fractionOfYear, float fractionOfDay) {
        final double latitude = getLatitude(z, hemisphereScale);
        final double declination = 23.44f * Mth.DEG_TO_RAD * Mth.sin(Mth.TWO_PI * (284f / 365f + fractionOfYear));
        final double hourAngle = Mth.TWO_PI * (0.5f - fractionOfDay);

        final double sinL = Math.sin(latitude);
        final double cosL = cosFromSin(latitude, sinL);
        final double sinD = Math.sin(declination);
        final double cosD = cosFromSin(declination, sinD);

        final double solarZenithAngle = Math.acos(Mth.clamp(sinL * sinD + cosL * cosD * Math.cos(hourAngle), -1, 1));

        final double sinZ = Math.sin(solarZenithAngle);
        final double cosZ = cosFromSin(solarZenithAngle, sinZ);

        final double absSolarAzimuthAngle = Math.acos(Mth.clamp((sinD - cosZ * sinL) / (sinZ * cosL), -1, 1));
        final double solarAzimuthAngle = hourAngle < 0 ? absSolarAzimuthAngle : Mth.TWO_PI - absSolarAzimuthAngle;

        return SkyPos.of(solarZenithAngle, solarAzimuthAngle);
    }

    public static float getLatitude(float z, float hemisphereScale) {
        return Helpers.triangle(-Mth.HALF_PI, 0, 1 / (4 * hemisphereScale), z - 0.5f * hemisphereScale);
    }

    public static boolean getInNorthernHemisphere(BlockPos pos, Level level) {
        return getInNorthernHemisphere(pos.getZ(), getHemisphereScale(level));
    }

    public static boolean getInNorthernHemisphere(int z, float hemisphereScale) {
        if (hemisphereScale == 0) {
            return true;
        }
        final int adjustedZ = z - (int) (hemisphereScale / 2);
        final int poleToPoleDistance = (int) (hemisphereScale * 2);
        final int normalizedZ = Mth.positiveModulo(adjustedZ, (poleToPoleDistance * 2));
        return normalizedZ > poleToPoleDistance;
    }

    public static int getMoonPhase(long calendarTick, long lunarOrbitTicks) {
        return (int) Mth.clampedMap((float) ((calendarTick + lunarOrbitTicks / 8) % lunarOrbitTicks) / lunarOrbitTicks, 0, 1, 0, 7);
    }

    private static final double MOON_ORBIT_PHI = Math.tan(Mth.DEG_TO_RAD * (24.33 + 5.14));

    public static SkyPos getMoonPosition(float z, float hemisphereScale, long calendarTick, long lunarOrbitTicks) {
        final double lunarAzimuth = Mth.TWO_PI * (float) (calendarTick % lunarOrbitTicks) / lunarOrbitTicks;
        final double lunarZenith = Mth.HALF_PI - Math.atan2(
                Math.cos(Mth.TWO_PI * (float) ((calendarTick * 0.83) % lunarOrbitTicks) / lunarOrbitTicks) * MOON_ORBIT_PHI,
                1);

        final double observerZenith = Mth.HALF_PI - getLatitude(z, hemisphereScale);
        final double observerAzimuth = Mth.TWO_PI * (1 - getFractionOfDay(calendarTick));

        final double deltaAzimuth = lunarAzimuth - observerAzimuth;

        final double sinPhiD = Math.sin(deltaAzimuth);
        final double cosPhiD = cosFromSin(deltaAzimuth, sinPhiD);
        final double sinThetaP = Math.sin(observerZenith);
        final double cosThetaP = cosFromSin(observerZenith, sinThetaP);
        final double sinThetaM = Math.sin(lunarZenith);
        final double cosThetaM = cosFromSin(lunarZenith, sinThetaM);

        final double lunarX = cosPhiD * cosThetaP * sinThetaM - cosThetaM * sinThetaP;
        final double lunarY = sinThetaM * sinPhiD;
        final double lunarZ = cosThetaM * cosThetaP + cosPhiD * sinThetaM * sinThetaP;

        final double relativeLunarZenith = Math.acos(lunarZ);
        final double relativeLunarAzimuth = Mth.PI - Math.atan2(lunarY, lunarX);

        return SkyPos.of(relativeLunarZenith, relativeLunarAzimuth);
    }

    public static SkyPos getStarPosition(float z, float hemisphereScale, float fractionOfDay, float fractionOfYear) {
        final double starZenith = Mth.HALF_PI - getLatitude(z, hemisphereScale);
        final double starAzimuth = Mth.TWO_PI * Mth.frac(fractionOfYear + fractionOfDay + 0.5f);
        return SkyPos.of(starZenith, starAzimuth);
    }

    public static float getHemisphereScale(Level level) {
        final ClimateModel model = Climate.model(level);
        if (model instanceof OverworldClimateModelAccessor accessor) {
            return accessor.tfg$getTemperatureScale();
        }
        return 20_000f;
    }

    private static float getFractionOfDay(long calendarTick) {
        return (calendarTick % ICalendar.TICKS_IN_DAY) / (float) ICalendar.TICKS_IN_DAY;
    }

    private static double cosFromSin(double angle, double sin) {
        return org.joml.Math.cosFromSin(sin, angle);
    }
}
