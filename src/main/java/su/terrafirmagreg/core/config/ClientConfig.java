package su.terrafirmagreg.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client Config Not synced with server, only loaded on the client. Only use this for aesthetic and rendering options!
 */
@SuppressWarnings("ClassCanBeRecord")
public final class ClientConfig {
    public final ForgeConfigSpec.IntValue PRECISE_ORE_PROSPECTOR_PARTICLE_CHANCE;
    public final ForgeConfigSpec.BooleanValue disableCustomSkyWithActiveShaders;
    public final ForgeConfigSpec.DoubleValue starSize;
    public final ForgeConfigSpec.DoubleValue maxStarMagnitude;
    public final ForgeConfigSpec.BooleanValue starColors;
    public final ForgeConfigSpec.DoubleValue starBrightness;
    public final ForgeConfigSpec.IntValue starTwinkleFrequency;
    public final ForgeConfigSpec.BooleanValue useFancyStarTwinkle;
    public final ForgeConfigSpec.DoubleValue fancyStarMagnitudeLimit;
    public final ForgeConfigSpec.BooleanValue drawConstellations;
    public final ForgeConfigSpec.DoubleValue constellationRed;
    public final ForgeConfigSpec.DoubleValue constellationGreen;
    public final ForgeConfigSpec.DoubleValue constellationBlue;
    public final ForgeConfigSpec.DoubleValue constellationLineWidth;
    public final ForgeConfigSpec.DoubleValue constellationAlpha;
    public final ForgeConfigSpec.DoubleValue constellationSpyglassAngleThreshold;
    public final ForgeConfigSpec.BooleanValue drawPlanets;
    public final ForgeConfigSpec.DoubleValue planetScale;
    public final ForgeConfigSpec.DoubleValue planetBrightness;
    public final ForgeConfigSpec.BooleanValue drawFallingStars;
    public final ForgeConfigSpec.DoubleValue fallingStarScale;
    public final ForgeConfigSpec.DoubleValue fallingStarBrightness;
    public final ForgeConfigSpec.IntValue fallingStarMaxConcurrent;
    public final ForgeConfigSpec.BooleanValue fallingStarCinematicTrail;
    public final ForgeConfigSpec.BooleanValue fallingStarGlow;
    public final ForgeConfigSpec.DoubleValue sunScale;
    public final ForgeConfigSpec.DoubleValue moonScale;

    ClientConfig(ForgeConfigSpec.Builder builder) {
        builder.push("propick_vein_rendering");
        PRECISE_ORE_PROSPECTOR_PARTICLE_CHANCE = builder
                .comment(
                        "\n\n1 in N chance for the precise xray ore prospector particles to appear per block. Set to 0 to disable. Default: 5")
                .defineInRange("PreciseOreProspectorParticleChance", 5, 0, 1000);
        builder.pop();

        builder.push("solar_calendar");
        disableCustomSkyWithActiveShaders = builder
                .comment("When true, falls back to vanilla sky rendering while a shader pack is active (Iris/Oculus). "
                        + "The TerraFirmaGreg shader pack expects this to stay false so custom stars/planets render with Complementary.")
                .define("disableCustomSkyWithActiveShaders", false);
        starSize = builder
                .comment("Multiplier for custom star quad size.")
                .defineInRange("starSize", 1.0, 0.0, Double.MAX_VALUE);
        maxStarMagnitude = builder
                .comment("Stars with apparent magnitude above this value are not rendered.")
                .defineInRange("maxStarMagnitude", 6.5, 0.0, Double.MAX_VALUE);
        starColors = builder
                .comment("When true, stars use catalog colors; when false, all stars are white.")
                .define("starColors", true);
        starBrightness = builder
                .comment("Multiplier for star and twilight moon opacity based on sun height.")
                .defineInRange("starBrightness", 2.0, 0.0, Double.MAX_VALUE);
        starTwinkleFrequency = builder
                .comment("Star twinkle speed in ticks per half-cycle. 0 disables twinkle.")
                .defineInRange("starTwinkleFrequency", 120, 0, Integer.MAX_VALUE);
        useFancyStarTwinkle = builder
                .comment("When true and starTwinkleFrequency > 0, bright stars use the Almagest sprite twinkle.")
                .define("useFancyStarTwinkle", true);
        fancyStarMagnitudeLimit = builder
                .comment("Stars brighter than this magnitude use fancy sprite twinkle (lower magnitude = brighter).")
                .defineInRange("fancyStarMagnitudeLimit", 3.0, 0.0, Double.MAX_VALUE);
        drawConstellations = builder
                .comment("When true, constellation lines are shown only while holding a spyglass and aiming at them.")
                .define("drawConstellations", true);
        constellationRed = builder
                .comment("Red component of constellation line color (0.0 to 1.0).")
                .defineInRange("constellationRed", 0.3, 0.0, 1.0);
        constellationGreen = builder
                .comment("Green component of constellation line color (0.0 to 1.0).")
                .defineInRange("constellationGreen", 0.35, 0.0, 1.0);
        constellationBlue = builder
                .comment("Blue component of constellation line color (0.0 to 1.0).")
                .defineInRange("constellationBlue", 0.5, 0.0, 1.0);
        constellationLineWidth = builder
                .comment("Half-width of constellation lines on the celestial sphere.")
                .defineInRange("constellationLineWidth", 0.1, 0.0, Double.MAX_VALUE);
        constellationAlpha = builder
                .comment("Opacity of constellation lines when visible through a spyglass.")
                .defineInRange("constellationAlpha", 0.6, 0.0, 1.0);
        constellationSpyglassAngleThreshold = builder
                .comment("Max angle in degrees between view and constellation stars while holding a spyglass.")
                .defineInRange("constellationSpyglassAngleThreshold", 6.0, 0.0, 180.0);
        drawPlanets = builder
                .comment("When true, renders planets from sky/planets.json.")
                .define("drawPlanets", true);
        planetScale = builder
                .comment("Multiplier for custom planet quad size.")
                .defineInRange("planetScale", 1.0, 0.0, Double.MAX_VALUE);
        planetBrightness = builder
                .comment("Multiplier for planet opacity at night and twilight.")
                .defineInRange("planetBrightness", 1.0, 0.0, Double.MAX_VALUE);
        drawFallingStars = builder
                .comment("When true, renders falling stars (meteors) spawned by the server at night.")
                .define("drawFallingStars", true);
        fallingStarScale = builder
                .comment("Multiplier for falling star particle size.")
                .defineInRange("fallingStarScale", 1.0, 0.0, Double.MAX_VALUE);
        fallingStarBrightness = builder
                .comment("Multiplier for falling star particle opacity.")
                .defineInRange("fallingStarBrightness", 1.0, 0.0, Double.MAX_VALUE);
        fallingStarMaxConcurrent = builder
                .comment("Maximum number of falling star particles rendered at once on this client.")
                .defineInRange("fallingStarMaxConcurrent", 24, 1, 200);
        fallingStarCinematicTrail = builder
                .comment("When true, falling stars leave a longer, denser dust trail.")
                .define("fallingStarCinematicTrail", true);
        fallingStarGlow = builder
                .comment("When true, falling stars emit occasional glow particles along their trail.")
                .define("fallingStarGlow", true);
        sunScale = builder
                .comment("Multiplier for the sun disc size in the custom sky renderer.")
                .defineInRange("sunScale", 0.725, 0.0, Double.MAX_VALUE);
        moonScale = builder
                .comment("Multiplier for the moon disc size in the custom sky renderer.")
                .defineInRange("moonScale", 1.0, 0.0, Double.MAX_VALUE);
        builder.pop();
    }
}
