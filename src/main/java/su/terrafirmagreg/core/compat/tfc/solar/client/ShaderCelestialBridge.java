package su.terrafirmagreg.core.compat.tfc.solar.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.compat.tfc.solar.ClientSolarCalculatorBridge;
import su.terrafirmagreg.core.compat.tfc.solar.SkyPos;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;

public final class ShaderCelestialBridge {
    private static final float CELESTIAL_RADIUS = 100.0F;
    private static final int SKY_ANGLE_STEPS = 1440;
    private static final int LOCAL_SEARCH_RADIUS = 12;
    private static final float STEP_SIZE = 1.0F / SKY_ANGLE_STEPS;

    private static float refinedSkyAngle = 0.25F;
    private static float refinedSunPathRotation = Float.NaN;
    private static int cachedFrameTick = -1;
    private static int cachedPartialKey = -1;
    private static float cachedSkyAngle = 0.25F;

    private ShaderCelestialBridge() {
    }

    public static float getTimeOfDayForShaders(Level level, float partialTick) {
        if (!shouldBridgeCelestial(level)) {
            return -1.0F;
        }
        return getSharedSkyAngle((ClientLevel) level, partialTick);
    }

    public static Vector4f getSunViewPosition(float sunPathRotation, float partialTick) {
        final ClientLevel level = getOverworld();
        if (level == null) {
            return null;
        }
        final float skyAngle = getSharedSkyAngle(level, partialTick);
        return toIrisViewPosition(skyAngle, CELESTIAL_RADIUS, sunPathRotation);
    }

    public static Vector4f getMoonViewPosition(float sunPathRotation, float partialTick) {
        final ClientLevel level = getOverworld();
        if (level == null) {
            return null;
        }
        // Complementary moon uses the same skyAngle/timeAngle chain as the sun (opposite Y).
        // A separate moon-orbit search desyncs shadows from the procedural moon disc.
        final float skyAngle = getSharedSkyAngle(level, partialTick);
        return toIrisViewPosition(skyAngle, -CELESTIAL_RADIUS, sunPathRotation);
    }

    private static float getSharedSkyAngle(ClientLevel level, float partialTick) {
        final int tick = (int) level.getGameTime();
        final int partialKey = (int) (partialTick * 10_000.0F);
        if (tick != cachedFrameTick || partialKey != cachedPartialKey) {
            cachedFrameTick = tick;
            cachedPartialKey = partialKey;
            cachedSkyAngle = computeTimeOfDayForComplementary(
                    ClientSolarCalculatorBridge.getSunPositionSmooth(level, partialTick),
                    getSunPathRotation());
        }
        return cachedSkyAngle;
    }

    static float computeTimeOfDayForComplementary(SkyPos skyPos, float sunPathRotation) {
        if (refinedSunPathRotation != sunPathRotation) {
            refinedSunPathRotation = sunPathRotation;
            refinedSkyAngle = 0.25F;
        }

        final Vector3f target = directionFromSkyPos(skyPos);
        float bestAngle = refinedSkyAngle;
        float bestDot = target.dot(complementarySunDirection(bestAngle, sunPathRotation));

        for (int offset = -LOCAL_SEARCH_RADIUS; offset <= LOCAL_SEARCH_RADIUS; offset++) {
            final float candidate = wrapAngle01(refinedSkyAngle + offset * STEP_SIZE);
            final float dot = target.dot(complementarySunDirection(candidate, sunPathRotation));
            if (dot > bestDot) {
                bestDot = dot;
                bestAngle = candidate;
            }
        }

        if (bestDot < 0.95F) {
            bestAngle = fullSearchTimeOfDay(target, sunPathRotation);
            bestDot = target.dot(complementarySunDirection(bestAngle, sunPathRotation));
        }

        bestAngle = parabolicRefinement(target, bestAngle, sunPathRotation, bestDot);
        refinedSkyAngle = bestAngle;
        return bestAngle;
    }

    private static float fullSearchTimeOfDay(Vector3f target, float sunPathRotation) {
        float bestAngle = 0.0F;
        float bestDot = Float.NEGATIVE_INFINITY;

        for (int step = 0; step <= SKY_ANGLE_STEPS; step++) {
            final float candidate = step * STEP_SIZE;
            final float dot = target.dot(complementarySunDirection(candidate, sunPathRotation));
            if (dot > bestDot) {
                bestDot = dot;
                bestAngle = candidate;
            }
        }
        return bestAngle;
    }

    private static float parabolicRefinement(Vector3f target, float centerAngle, float sunPathRotation, float centerDot) {
        final float previousAngle = wrapAngle01(centerAngle - STEP_SIZE);
        final float nextAngle = wrapAngle01(centerAngle + STEP_SIZE);
        final float previousDot = target.dot(complementarySunDirection(previousAngle, sunPathRotation));
        final float nextDot = target.dot(complementarySunDirection(nextAngle, sunPathRotation));
        final float denominator = previousDot - 2.0F * centerDot + nextDot;
        if (Math.abs(denominator) <= 1.0E-6F) {
            return centerAngle;
        }

        final float offset = 0.5F * (previousDot - nextDot) / denominator * STEP_SIZE;
        return wrapAngle01(centerAngle + Mth.clamp(offset, -STEP_SIZE, STEP_SIZE));
    }

    private static float wrapAngle01(float angle) {
        return Mth.frac(angle);
    }

    static Vector3f complementarySunDirection(float timeOfDay, float sunPathRotation) {
        final float sunAngle = toSunAngleUniform(timeOfDay);
        final float timeAngle = toComplementaryTimeAngle(sunAngle);
        return directionFromComplementaryTimeAngle(timeAngle, sunPathRotation);
    }

    private static float toSunAngleUniform(float skyAngle) {
        if (skyAngle < 0.75F) {
            return skyAngle + 0.25F;
        }
        return skyAngle - 0.75F;
    }

    private static float toComplementaryTimeAngle(float sunAngle) {
        final float tAmin = Mth.frac(sunAngle - 0.033333333F);
        final float tAlin = tAmin < 0.433333333F
                ? tAmin * 1.15384615385F
                : tAmin * 0.882352941176F + 0.117647058824F;
        final float hA = tAlin > 0.5F ? 1.0F : 0.0F;
        final float tAfrc = Mth.frac(tAlin * 2.0F);
        final float tAfrs = tAfrc * tAfrc * (3.0F - 2.0F * tAfrc);
        final float tAmix = hA < 0.5F ? 0.3F : -0.1F;
        return (tAfrc * (1.0F - tAmix) + tAfrs * tAmix + hA) * 0.5F;
    }

    private static Vector3f directionFromComplementaryTimeAngle(float timeAngle, float sunPathRotation) {
        float ang = Mth.frac(timeAngle - 0.25F);
        ang = (float) ((ang + (Math.cos(ang * Math.PI) * -0.5 + 0.5 - ang) / 3.0) * Mth.TWO_PI);

        final float pathRadians = sunPathRotation * Mth.DEG_TO_RAD;
        final float cosPath = Mth.cos(pathRadians);
        final float sinPath = -Mth.sin(pathRadians);
        final float x = -Mth.sin(ang);
        final float y = Mth.cos(ang) * cosPath;
        final float z = Mth.cos(ang) * sinPath;
        return new Vector3f(x, y, z).normalize();
    }

    private static Vector4f toIrisViewPosition(float skyAngle, float radius, float sunPathRotation) {
        final Matrix4f modelView = getGbufferModelView();
        if (modelView == null) {
            return null;
        }

        final Matrix4f celestial = new Matrix4f(modelView);
        celestial.rotate(Axis.YP.rotationDegrees(-90.0F));
        celestial.rotate(Axis.ZP.rotationDegrees(sunPathRotation));
        celestial.rotate(Axis.XP.rotationDegrees(skyAngle * 360.0F));
        return celestial.transform(new Vector4f(0.0F, radius, 0.0F, 0.0F));
    }

    static Vector3f directionFromSkyPos(SkyPos skyPos) {
        final Matrix4f rotation = new Matrix4f()
                .rotate(Axis.YP.rotation(skyPos.azimuth()))
                .rotate(Axis.XN.rotation(skyPos.zenith()));
        final Vector4f direction = rotation.transform(new Vector4f(0.0F, 1.0F, 0.0F, 0.0F));
        return new Vector3f(direction.x(), direction.y(), direction.z()).normalize();
    }

    static float getSunPathRotation() {
        try {
            final Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            final Object pipelineManager = iris.getMethod("getPipelineManager").invoke(null);
            if (pipelineManager == null) {
                return 0.0F;
            }
            final Object pipeline = pipelineManager.getClass().getMethod("getPipelineNullable").invoke(pipelineManager);
            if (pipeline == null) {
                return 0.0F;
            }
            return (float) pipeline.getClass().getMethod("getSunPathRotation").invoke(pipeline);
        } catch (ReflectiveOperationException ignored) {
            return 0.0F;
        }
    }

    private static boolean shouldBridgeCelestial(Level level) {
        return SolarCalendarBackport.isEnabled()
                && ShaderPackDetection.isShaderPackActive()
                && level.isClientSide()
                && level.dimension() == Level.OVERWORLD;
    }

    private static ClientLevel getOverworld() {
        if (!SolarCalendarBackport.isEnabled() || !ShaderPackDetection.isShaderPackActive()) {
            return null;
        }
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        return level;
    }

    public static float getPartialTick() {
        try {
            final Class<?> stateClass = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
            final Object instance = stateClass.getField("INSTANCE").get(null);
            return (float) stateClass.getMethod("getTickDelta").invoke(instance);
        } catch (ReflectiveOperationException ignored) {
            return Minecraft.getInstance().getPartialTick();
        }
    }

    private static Matrix4f getGbufferModelView() {
        try {
            final Class<?> stateClass = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
            final Object instance = stateClass.getField("INSTANCE").get(null);
            final Object matrix = stateClass.getMethod("getGbufferModelView").invoke(instance);
            return matrix instanceof Matrix4f modelView ? new Matrix4f(modelView) : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
