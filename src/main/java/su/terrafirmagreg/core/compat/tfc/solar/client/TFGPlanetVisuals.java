package su.terrafirmagreg.core.compat.tfc.solar.client;

import java.util.Map;

import org.joml.Vector2f;

import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.minecraft.world.level.LevelAccessor;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.config.TFGConfig;

/**
 * Orbital placement for sky planets, ported from TFCCaelum {@code renderPlanetsSpirograph}.
 */
public final class TFGPlanetVisuals {
    private static final double EARTH_ORBITAL_PERIOD_DAYS = 365.242;
    private static final double EARTH_SEMI_MAJOR_AXIS = 149.598;
    private static final double PLANET_ORBIT_FACTOR = 1.0;
    private static final double MOON_ORBIT_DISTANCE_FACTOR = 50.0;
    private static final double ARCSEC_PER_RADIAN = 206265.0;

    private TFGPlanetVisuals() {
    }

    public static Vector2f getSunVector() {
        return new Vector2f(0.0F, 1.0F).mul((float) (EARTH_SEMI_MAJOR_AXIS * 1.0E6));
    }

    public static float getSkyRotationDegrees(LevelAccessor level, float partialTick) {
        return level.getTimeOfDay(partialTick) * 360.0F;
    }

    public static double getInclination(SkyPlanet planet, Map<String, SkyPlanet> catalog) {
        if (!planet.isMoon()) {
            return planet.eclipticPlaneDegrees();
        }
        return planet.parentBody()
                .map(catalog::get)
                .map(SkyPlanet::eclipticPlaneDegrees)
                .orElse(planet.eclipticPlaneDegrees());
    }

    public static Vector2f getPlanetVector(
            LevelAccessor level,
            SkyPlanet planet,
            Map<String, SkyPlanet> catalog,
            Map<String, Vector2f> cache,
            Vector2f sunVector,
            float partialTick) {
        final Vector2f cached = cache.get(planet.name());
        if (cached != null) {
            return cached;
        }

        final Vector2f parentVector = planet.isMoon()
                ? planet.parentBody()
                        .map(catalog::get)
                        .map(parent -> getPlanetVector(level, parent, catalog, cache, sunVector, partialTick))
                        .orElse(sunVector)
                : sunVector;

        final float semiMajorAxis = (float) getSemiMajorAxisMega(planet);
        final double position = EARTH_ORBITAL_PERIOD_DAYS / planet.orbitalPeriodDays()
                * (getSmoothCalendarTicks(level, partialTick) / (ICalendar.TICKS_IN_DAY * PLANET_ORBIT_FACTOR));
        final Vector2f orbitOffset = planet.isRetrograde()
                ? new Vector2f((float) Math.cos(position), (float) Math.sin(position)).mul(semiMajorAxis)
                : new Vector2f((float) Math.sin(position), (float) Math.cos(position)).mul(semiMajorAxis);
        final Vector2f result = new Vector2f(parentVector).add(orbitOffset);
        cache.put(planet.name(), result);
        return result;
    }

    public static double getAngleToSun(Vector2f sunVector, Vector2f planetVector) {
        return Math.toDegrees(sunVector.angle(planetVector));
    }

    public static double getAngleToParent(Vector2f parentVector, Vector2f planetVector) {
        return Math.toDegrees(parentVector.angle(planetVector));
    }

    public static float getPlanetScale(SkyPlanet planet, Vector2f planetVector) {
        final double distance = Math.abs(new Vector2f().distance(planetVector));
        if (distance <= 0.0) {
            return 0.0F;
        }
        final double apparentDiameterArcSec = ARCSEC_PER_RADIAN * (planet.diameterKm() / distance);
        return (float) (apparentDiameterArcSec * planet.scaleFactor() * TFGConfig.CLIENT.planetScale.get());
    }

    private static double getSemiMajorAxisMega(SkyPlanet planet) {
        final double planetScale = TFGConfig.CLIENT.planetScale.get();
        final double axis = planet.isMoon()
                ? planet.semiMajorAxis() * planetScale
                : planet.semiMajorAxis();
        final double mega = axis * 1.0E6;
        return planet.isMoon() ? mega * MOON_ORBIT_DISTANCE_FACTOR : mega;
    }

    private static double getSmoothCalendarTicks(LevelAccessor level, float partialTick) {
        return SolarCalendarBackport.getSmoothCalendarTicks(Calendars.get(level), partialTick);
    }
}
