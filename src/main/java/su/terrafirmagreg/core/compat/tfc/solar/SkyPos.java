package su.terrafirmagreg.core.compat.tfc.solar;

import net.minecraft.util.Mth;

public record SkyPos(float zenith, float azimuth) {
    public static final SkyPos ZERO = of(0, 0);

    public static SkyPos of(double zenith, double azimuth) {
        return new SkyPos((float) zenith, (float) azimuth);
    }

    @Override
    public String toString() {
        return "Position[zenith=%.1f°, azimuth=%.1f°]".formatted(Mth.RAD_TO_DEG * zenith, Mth.RAD_TO_DEG * azimuth);
    }
}
