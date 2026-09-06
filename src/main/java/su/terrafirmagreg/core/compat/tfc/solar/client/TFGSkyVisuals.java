package su.terrafirmagreg.core.compat.tfc.solar.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.compat.tfc.solar.ClientSolarCalculatorBridge;
import su.terrafirmagreg.core.compat.tfc.solar.SkyPos;
import su.terrafirmagreg.core.config.TFGConfig;

public final class TFGSkyVisuals {
    private static final float[] SUNRISE_COLOR = new float[4];

    private TFGSkyVisuals() {
    }

    public static float sunHeight(Level level, BlockPos pos, float partialTick) {
        final SkyPos sunPos = ClientSolarCalculatorBridge.getSunPosition(level, pos);
        return Mth.cos(sunPos.zenith());
    }

    public static double apparentTimeOfDay(Level level, BlockPos pos, float partialTick) {
        return 0.75F + sunHeight(level, pos, partialTick) / 4.0F;
    }

    public static float getStarBrightness(Level level, BlockPos pos, float partialTick) {
        final float apparentTime = (float) apparentTimeOfDay(level, pos, partialTick);
        float brightness = 1.0F - (Mth.cos(apparentTime * Mth.TWO_PI) * 2.0F + 0.25F);
        brightness = Mth.clamp(brightness, 0.0F, 1.0F);
        brightness = brightness * brightness * 0.5F;
        return (float) (brightness * TFGConfig.CLIENT.starBrightness.get());
    }

    public static float[] getSunriseColor(Level level, BlockPos pos, float partialTick) {
        final float height = sunHeight(level, pos, partialTick);
        if (height < -0.4F || height > 0.4F) {
            return null;
        }

        final float blend = (height / 0.4F) * 0.5F + 0.5F;
        float alpha = 1.0F - (1.0F - Mth.sin(blend * (float) Math.PI)) * 0.99F;
        alpha *= alpha;
        SUNRISE_COLOR[0] = blend * 0.3F + 0.7F;
        SUNRISE_COLOR[1] = blend * blend * 0.7F + 0.2F;
        SUNRISE_COLOR[2] = blend * blend * 0.0F + 0.2F;
        SUNRISE_COLOR[3] = alpha;
        return SUNRISE_COLOR;
    }

    public static Vector3f getSunDirection(Level level, BlockPos pos, float partialTick) {
        return directionFromSkyPos(ClientSolarCalculatorBridge.getSunPositionSmooth(level, partialTick));
    }

    /** How much the view direction faces the sun near the horizon — used for fog sunrise tint. */
    public static float computeHorizonSunFacing(Camera camera, Level level, BlockPos pos, float partialTick) {
        return computeHorizonSunFacing(camera.getLookVector(), level, pos, partialTick);
    }

    public static float computeHorizonSunFacing(Vector3f lookDirection, Level level, BlockPos pos, float partialTick) {
        final Vector3f sunDir = getSunDirection(level, pos, partialTick);
        float facing = lookDirection.dot(sunDir);
        if (facing <= 0.0F) {
            return 0.0F;
        }
        final float horizon = 1.0F - Mth.clamp(lookDirection.y() * 1.5F + 0.15F, 0.0F, 1.0F);
        return facing * horizon;
    }

    private static Vector3f directionFromSkyPos(SkyPos skyPos) {
        final Matrix4f rotation = new Matrix4f()
                .rotate(Axis.YP.rotation(skyPos.azimuth()))
                .rotate(Axis.XN.rotation(skyPos.zenith()));
        final Vector4f direction = rotation.transform(new Vector4f(0.0F, 1.0F, 0.0F, 0.0F));
        return new Vector3f(direction.x(), direction.y(), direction.z()).normalize();
    }
}
