package su.terrafirmagreg.core.compat.tfc.solar.client;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record SkyPlanet(
        String name,
        ResourceLocation texture,
        double orbitalPeriodDays,
        double semiMajorAxis,
        double eclipticPlaneDegrees,
        double diameterKm,
        double scaleFactor,
        boolean isRetrograde,
        boolean isMoon,
        Optional<String> parentBody) {

    public static final Codec<SkyPlanet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "Unknown").forGetter(SkyPlanet::name),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(SkyPlanet::texture),
            Codec.DOUBLE.fieldOf("orbitalPeriodDays").forGetter(SkyPlanet::orbitalPeriodDays),
            Codec.DOUBLE.fieldOf("semiMajorAxis").forGetter(SkyPlanet::semiMajorAxis),
            Codec.DOUBLE.fieldOf("eclipticPlaneDegrees").forGetter(SkyPlanet::eclipticPlaneDegrees),
            Codec.DOUBLE.fieldOf("diameterKm").forGetter(SkyPlanet::diameterKm),
            Codec.DOUBLE.optionalFieldOf("scaleFactor", 1.0).forGetter(SkyPlanet::scaleFactor),
            Codec.BOOL.optionalFieldOf("isRetrograde", false).forGetter(SkyPlanet::isRetrograde),
            Codec.BOOL.optionalFieldOf("isMoon", false).forGetter(SkyPlanet::isMoon),
            Codec.STRING.optionalFieldOf("parentBody").forGetter(SkyPlanet::parentBody))
            .apply(instance, SkyPlanet::new));

    public static final Codec<PlanetCatalog> CATALOG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("replace", false).forGetter(PlanetCatalog::replace),
            CODEC.listOf().fieldOf("planets").forGetter(PlanetCatalog::planets))
            .apply(instance, PlanetCatalog::new));

    public record PlanetCatalog(boolean replace, List<SkyPlanet> planets) {
    }
}
