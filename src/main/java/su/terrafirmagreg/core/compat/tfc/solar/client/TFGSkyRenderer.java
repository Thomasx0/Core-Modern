package su.terrafirmagreg.core.compat.tfc.solar.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;

import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import su.terrafirmagreg.core.compat.tfc.solar.ClientSolarCalculatorBridge;
import su.terrafirmagreg.core.compat.tfc.solar.SkyPos;
import su.terrafirmagreg.core.config.TFGConfig;

public final class TFGSkyRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation MOON_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final ResourceLocation SUN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");

    private static VertexBuffer starBuffer;
    private static VertexBuffer skyBuffer;
    private static VertexBuffer darkBuffer;
    private static boolean initialized;
    private static List<Star> cachedStars = List.of();
    private static List<Star> cachedBrightStars = List.of();
    private static List<Constellation> cachedConstellations = List.of();
    private static List<SkyPlanet> cachedPlanets = List.of();

    private TFGSkyRenderer() {
    }

    public static void updateStars(List<Star> stars) {
        if (stars == null || stars.isEmpty()) {
            LOGGER.warn("[TFG Stars] Empty star catalog, using fallback (120 stars)");
            cachedStars = generateFallbackStars(120);
        } else {
            LOGGER.info("[TFG Stars] Loaded {} stars", stars.size());
            cachedStars = List.copyOf(stars);
        }
        rebuildBrightStarCache();
        rebuildStarBuffer();
    }

    static List<Constellation> getCachedConstellations() {
        return cachedConstellations;
    }

    private static void rebuildBrightStarCache() {
        if (!useFancyBrightStars()) {
            cachedBrightStars = List.of();
            return;
        }
        final double magnitudeLimit = TFGConfig.CLIENT.fancyStarMagnitudeLimit.get();
        cachedBrightStars = cachedStars.stream()
                .filter(star -> star.magnitude() < magnitudeLimit)
                .toList();
    }

    private static boolean useFancyBrightStars() {
        return TFGConfig.CLIENT.useFancyStarTwinkle.get()
                && TFGConfig.CLIENT.starTwinkleFrequency.get() > 0;
    }

    public static void rebuildStarBuffer() {
        rebuildBrightStarCache();
        if (starBuffer != null) {
            starBuffer.close();
        }
        starBuffer = createStarBuffer(cachedStars);
    }

    public static void updateConstellations(List<Constellation> constellations) {
        if (constellations == null || constellations.isEmpty()) {
            LOGGER.warn("[TFG Constellations] Empty constellation catalog");
            cachedConstellations = List.of();
        } else {
            final int edgeCount = constellations.stream().mapToInt(c -> c.edges().size()).sum();
            LOGGER.info("[TFG Constellations] Loaded {} constellations ({} edges)", constellations.size(), edgeCount);
            cachedConstellations = List.copyOf(constellations);
        }
        ConstellationHighlightTracker.resize(cachedConstellations);
    }

    public static void updatePlanets(List<SkyPlanet> planets) {
        if (planets == null || planets.isEmpty()) {
            LOGGER.warn("[TFG Planets] Empty planet catalog");
            cachedPlanets = List.of();
        } else {
            LOGGER.info("[TFG Planets] Loaded {} planets", planets.size());
            cachedPlanets = List.copyOf(planets);
        }
    }

    public static void tickConstellationHighlights(ClientLevel level, Player player, Camera camera) {
        ConstellationHighlightTracker.tick(level, player, camera);
    }

    public static boolean renderSky(
            ClientLevel level,
            PoseStack poseStack,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean isFoggy,
            Runnable skyFogSetup) {
        skyFogSetup.run();
        if (isFoggy) {
            return true;
        }
        final FogType fogType = camera.getFluidInCamera();
        if (fogType == FogType.POWDER_SNOW || fogType == FogType.LAVA || doesMobEffectBlockSky(camera)) {
            return true;
        }

        ensureInitialized();

        final Vec3 skyColor = level.getSkyColor(camera.getPosition(), partialTick);
        final SkyPos sunPos = ClientSolarCalculatorBridge.getSunPosition(level, camera.getBlockPosition());
        final float[] sunriseColor = ShaderPackDetection.shouldDelegateCelestialBodiesToShaders()
                ? null
                : TFGSkyVisuals.getSunriseColor(level, camera.getBlockPosition(), partialTick);

        FogRenderer.levelFogColor();
        final Tesselator tesselator = Tesselator.getInstance();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        final ShaderInstance shader = GameRenderer.getPositionShader();
        if (shader == null) {
            return false;
        }
        skyBuffer.bind();
        skyBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
        VertexBuffer.unbind();
        RenderSystem.enableBlend();

        if (sunriseColor != null) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            final Matrix4f sunrisePose = rotateTo(poseStack, SkyPos.of(-Mth.HALF_PI, sunPos.azimuth() + Mth.PI));
            final BufferBuilder buffer = tesselator.getBuilder();
            buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            buffer.vertex(sunrisePose, 0.0F, 100.0F, 0.0F).color(sunriseColor[0], sunriseColor[1], sunriseColor[2], sunriseColor[3]).endVertex();
            for (int i = 0; i <= 16; i++) {
                final float angle = i * Mth.TWO_PI / 16.0F;
                final float sin = Mth.sin(angle);
                final float cos = Mth.cos(angle);
                buffer.vertex(sunrisePose, sin * 120.0F, cos * 120.0F, -cos * 40.0F * sunriseColor[3])
                        .color(sunriseColor[0], sunriseColor[1], sunriseColor[2], 0.0F).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        }

        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        final float rainAlpha = 1.0F - level.getRainLevel(partialTick);
        final float starAlpha = TFGSkyVisuals.getStarBrightness(level, camera.getBlockPosition(), partialTick);
        final float baseNightAlpha = starAlpha * rainAlpha;
        final float nightAlpha = useFancyBrightStars()
                ? baseNightAlpha
                : applyStarTwinkle(baseNightAlpha, level, partialTick, camera.getBlockPosition());

        // Below the world the camera looks at the underside of the celestial sphere.
        // Fancy twinkle sprites then appear as a static "particle cluster" under the ground.
        final boolean underWorld = camera.getPosition().y < level.getMinBuildHeight();

        if (!underWorld && nightAlpha > 0.0F && starBuffer != null) {
            final SkyPos starPos = ClientSolarCalculatorBridge.getStarPosition(level, camera.getBlockPosition());
            RenderSystem.setShaderColor(nightAlpha, nightAlpha, nightAlpha, nightAlpha);
            FogRenderer.setupNoFog();
            final Matrix4f stars = rotateSkyTo(poseStack, starPos);
            final ShaderInstance starShader = GameRenderer.getPositionColorShader();
            if (starShader != null) {
                starBuffer.bind();
                starBuffer.drawWithShader(stars, projectionMatrix, starShader);
                VertexBuffer.unbind();

                if (useFancyBrightStars()) {
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    FancyBrightStarRenderer.render(
                            stars,
                            starPos,
                            cachedBrightStars,
                            baseNightAlpha,
                            level.getGameTime(),
                            StarMagnitudeScale.from(cachedStars));
                }

                if (TFGConfig.CLIENT.drawConstellations.get()
                        && camera.getEntity() instanceof Player player) {
                    ConstellationHighlightTracker.renderHighlighted(stars, projectionMatrix, nightAlpha, player);
                }

                if (TFGConfig.CLIENT.drawPlanets.get() && !cachedPlanets.isEmpty()) {
                    renderPlanets(level, tesselator, poseStack, partialTick, rainAlpha, starAlpha);
                }
            }
            skyFogSetup.run();
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.disableBlend();

        final double distanceAboveHorizon = camera.getEntity().getEyePosition(partialTick).y - level.getSeaLevel();
        if (distanceAboveHorizon < 0.0) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 12.0F, 0.0F);
            RenderSystem.setShader(GameRenderer::getPositionShader);
            final ShaderInstance darkShader = GameRenderer.getPositionShader();
            if (darkShader != null) {
                darkBuffer.bind();
                darkBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, darkShader);
                VertexBuffer.unbind();
            }
            poseStack.popPose();
        }

        if (!ShaderPackDetection.shouldDelegateCelestialBodiesToShaders()) {
            renderSunAndMoon(
                    level,
                    tesselator,
                    poseStack,
                    camera,
                    skyColor,
                    sunPos,
                    rainAlpha,
                    starAlpha);
        }

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        return true;
    }

    private static void renderSunAndMoon(
            ClientLevel level,
            Tesselator tesselator,
            PoseStack poseStack,
            Camera camera,
            Vec3 skyColor,
            SkyPos sunPos,
            float rainAlpha,
            float starAlpha) {
        final Matrix4f sun = rotateTo(poseStack, sunPos);
        final SkyPos moonPos = ClientSolarCalculatorBridge.getMoonPosition(level, camera.getBlockPosition());
        final Matrix4f moon = rotateTo(poseStack, moonPos);

        final long calendarTicks = Calendars.CLIENT.getCalendarTicks();
        final long moonOrbitTicks = ClientSolarCalculatorBridge.getMoonOrbitTicks();
        final float sunSize = TFGMoonVisuals.getSunSize();
        final float moonSize = TFGMoonVisuals.getMoonSize(calendarTicks, moonOrbitTicks);
        final int moonPhase = ClientSolarCalculatorBridge.getMoonPhase();

        final float[] coverColors = new float[] { (float) skyColor.x, (float) skyColor.y, (float) skyColor.z };

        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1, 1, 1, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        drawCoverQuad(buffer, sun, coverColors, TFGMoonVisuals.getSunCoverSize());
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(1f, 1f, 1f, rainAlpha);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SUN_LOCATION);
        buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        drawTexturedQuad(buffer, sun, sunSize);
        BufferUploader.drawWithShader(buffer.end());

        final float moonAlpha = (0.2f + 0.8f * starAlpha) * rainAlpha;

        if (moonPhase != 4) {
            final int moonU = moonPhase % 4;
            final int moonV = moonPhase / 4 % 2;

            RenderSystem.setShaderColor(1f, 1f, 1f, moonAlpha);
            RenderSystem.setShaderTexture(0, MOON_LOCATION);
            buffer = tesselator.getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            drawMoonQuad(buffer, moon, moonSize, moonU, moonV);
            BufferUploader.drawWithShader(buffer.end());
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        skyBuffer = createSkyDiscBuffer(16.0F);
        darkBuffer = createSkyDiscBuffer(-16.0F);
        if (starBuffer == null) {
            cachedStars = generateFallbackStars(120);
            starBuffer = createStarBuffer(cachedStars);
        }
    }

    private static VertexBuffer createSkyDiscBuffer(float depth) {
        final Tesselator tesselator = Tesselator.getInstance();
        final BufferBuilder builder = tesselator.getBuilder();
        final BufferBuilder.RenderedBuffer rendered = buildSkyDisc(builder, depth);
        final VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(rendered);
        VertexBuffer.unbind();
        return buffer;
    }

    private static BufferBuilder.RenderedBuffer buildSkyDisc(BufferBuilder builder, float depth) {
        final float radiusX = Math.signum(depth) * 512.0F;
        final float radiusZ = 512.0F;
        RenderSystem.setShader(GameRenderer::getPositionShader);
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        builder.vertex(0.0D, depth, 0.0D).endVertex();
        for (int angle = -180; angle <= 180; angle += 45) {
            final float radians = angle * Mth.DEG_TO_RAD;
            builder.vertex(radiusX * Mth.cos(radians), depth, radiusZ * Mth.sin(radians)).endVertex();
        }
        return builder.end();
    }

    private static VertexBuffer createStarBuffer(List<Star> stars) {
        final float maxMagnitude = TFGConfig.CLIENT.maxStarMagnitude.get().floatValue();
        final double starSize = TFGConfig.CLIENT.starSize.get();
        final boolean starColors = TFGConfig.CLIENT.starColors.get();
        final StarMagnitudeScale magnitudeScale = StarMagnitudeScale.from(stars);
        final boolean excludeBrightStars = useFancyBrightStars();
        final double brightMagnitudeLimit = TFGConfig.CLIENT.fancyStarMagnitudeLimit.get();

        final Tesselator tesselator = Tesselator.getInstance();
        final BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        final RandomSource random = RandomSource.create(10842L);
        for (Star star : stars) {
            if (star.magnitude() > maxMagnitude) {
                continue;
            }
            if (excludeBrightStars && star.magnitude() < brightMagnitudeLimit) {
                continue;
            }
            drawStar(builder, star, random, magnitudeScale, starSize, starColors);
        }
        final VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        return buffer;
    }

    static void appendConstellationEdge(
            BufferBuilder buffer,
            Matrix4f pose,
            Constellation.Edge edge,
            float lineWidth,
            int red,
            int green,
            int blue,
            int alpha) {
        final Vector3f start = toSpherePosition(edge.ascension1(), edge.declination1(), 99.5F);
        final Vector3f end = toSpherePosition(edge.ascension2(), edge.declination2(), 99.5F);
        final Vector3f direction = new Vector3f(end).sub(start);
        if (direction.lengthSquared() < 1.0E-6F) {
            return;
        }
        direction.normalize();

        Vector3f perpendicular = new Vector3f(start).cross(direction);
        if (perpendicular.lengthSquared() < 1.0E-6F) {
            perpendicular = new Vector3f(0.0F, 1.0F, 0.0F).cross(direction);
        }
        perpendicular.normalize().mul(lineWidth);

        putColorVertex(buffer, pose, new Vector3f(start).sub(perpendicular), red, green, blue, alpha);
        putColorVertex(buffer, pose, new Vector3f(start).add(perpendicular), red, green, blue, alpha);
        putColorVertex(buffer, pose, new Vector3f(end).add(perpendicular), red, green, blue, alpha);
        putColorVertex(buffer, pose, new Vector3f(end).sub(perpendicular), red, green, blue, alpha);
    }

    static Vector3f toSpherePosition(double ascension, double declination, float radius) {
        final float x = (float) (Math.cos(declination) * Math.cos(ascension) * radius);
        final float y = (float) (Math.cos(declination) * Math.sin(ascension) * radius);
        final float z = (float) (Math.sin(declination) * radius);
        return new Vector3f(x, y, z);
    }

    private static void putColorVertex(BufferBuilder buffer, Matrix4f pose, Vector3f position, int red, int green, int blue, int alpha) {
        buffer.vertex(pose, position.x(), position.y(), position.z()).color(red, green, blue, alpha).endVertex();
    }

    private static List<Star> generateFallbackStars(int count) {
        final Random random = new Random(42L);
        final List<Star> stars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stars.add(new Star(
                    random.nextDouble() * Mth.TWO_PI,
                    random.nextDouble() * Math.PI - Mth.HALF_PI,
                    0xFFFFFF,
                    random.nextDouble() * 5.0));
        }
        return stars;
    }

    private static void drawStar(
            BufferBuilder buffer,
            Star star,
            RandomSource random,
            StarMagnitudeScale magnitudeScale,
            double starSize,
            boolean starColors) {
        final StarAppearance appearance = computeStarAppearance(star.magnitude(), magnitudeScale, starSize);
        if (appearance.size() <= 0.0) {
            return;
        }

        final int alpha = appearance.alpha();
        final int red;
        final int green;
        final int blue;
        if (starColors) {
            final int color = star.color();
            red = (color >> 16) & 0xFF;
            green = (color >> 8) & 0xFF;
            blue = color & 0xFF;
        } else {
            red = 255;
            green = 255;
            blue = 255;
        }

        double x = Math.cos(star.declination()) * Math.cos(star.ascension());
        double y = Math.cos(star.declination()) * Math.sin(star.ascension());
        double z = Math.sin(star.declination());
        final double length = Math.sqrt(x * x + y * y + z * z);
        if (length > 0.0) {
            x /= length;
            y /= length;
            z /= length;
        }
        final float size = (float) appearance.size();
        final Vector3f pos = new Vector3f((float) (x * 100.0), (float) (y * 100.0), (float) (z * 100.0));
        final Quaternionf q = new Quaternionf()
                .rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), pos)
                .rotateZ(random.nextFloat() * Mth.TWO_PI);

        putStarVertex(buffer, pos, new Vector3f(size, -size, 0).rotate(q), red, green, blue, alpha);
        putStarVertex(buffer, pos, new Vector3f(size, size, 0).rotate(q), red, green, blue, alpha);
        putStarVertex(buffer, pos, new Vector3f(-size, size, 0).rotate(q), red, green, blue, alpha);
        putStarVertex(buffer, pos, new Vector3f(-size, -size, 0).rotate(q), red, green, blue, alpha);
    }

    static StarAppearance computeStarAppearance(
            double magnitude,
            StarMagnitudeScale magnitudeScale,
            double starSize) {
        final double logMagnitude = Math.log10(magnitude + magnitudeScale.shift());
        final double range = magnitudeScale.logMax() - magnitudeScale.logMin();
        final double normalizedMagnitude = range > 0.0
                ? (logMagnitude - magnitudeScale.logMin()) / range
                : 0.0;
        final double size = (0.6 - normalizedMagnitude * 0.5) * starSize;
        return new StarAppearance(size, (int) ((1.0 - normalizedMagnitude) * 255.0));
    }

    private static float applyStarTwinkle(float nightAlpha, ClientLevel level, float partialTick, BlockPos cameraPos) {
        final int frequency = TFGConfig.CLIENT.starTwinkleFrequency.get();
        if (frequency <= 0 || nightAlpha <= 0.0F) {
            return nightAlpha;
        }
        final float time = level.getGameTime() + partialTick;
        final float wave = 0.92F + 0.08F * Mth.sin((float) (time * Mth.TWO_PI / frequency));
        final float noise = 0.03F * Mth.sin(time * 0.11F + cameraPos.hashCode() * 0.01F);
        return nightAlpha * Mth.clamp(wave + noise, 0.0F, 1.0F);
    }

    static record StarAppearance(double size, int alpha) {
    }

    private static void putStarVertex(BufferBuilder buffer, Vector3f pos, Vector3f offset, int red, int green, int blue, int alpha) {
        final Vector3f vertex = new Vector3f(pos).add(offset);
        buffer.vertex(vertex.x(), vertex.y(), vertex.z()).color(red, green, blue, alpha).endVertex();
    }

    static record StarMagnitudeScale(double shift, double logMin, double logMax) {
        private static StarMagnitudeScale from(List<Star> stars) {
            final double minMagnitude = stars.stream().mapToDouble(Star::magnitude).min().orElse(0.0);
            double maxMagnitude = stars.stream().mapToDouble(Star::magnitude).max().orElse(1.0);
            final double shift = Math.abs(minMagnitude);
            maxMagnitude += shift;
            return new StarMagnitudeScale(shift, 0.0, Math.log10(maxMagnitude));
        }
    }

    private static void drawCoverQuad(BufferBuilder buffer, Matrix4f pose, float[] color, float halfSize) {
        buffer.vertex(pose, -halfSize, 100.0F, -halfSize).color(color[0], color[1], color[2], 1.0f).endVertex();
        buffer.vertex(pose, halfSize, 100.0F, -halfSize).color(color[0], color[1], color[2], 1.0f).endVertex();
        buffer.vertex(pose, halfSize, 100.0F, halfSize).color(color[0], color[1], color[2], 1.0f).endVertex();
        buffer.vertex(pose, -halfSize, 100.0F, halfSize).color(color[0], color[1], color[2], 1.0f).endVertex();
    }

    private static void drawTexturedQuad(BufferBuilder buffer, Matrix4f pose, float size) {
        buffer.vertex(pose, -size, 100.0F, -size).uv(0.0F, 0.0F).endVertex();
        buffer.vertex(pose, size, 100.0F, -size).uv(1.0F, 0.0F).endVertex();
        buffer.vertex(pose, size, 100.0F, size).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(pose, -size, 100.0F, size).uv(0.0F, 1.0F).endVertex();
    }

    private static void drawMoonQuad(BufferBuilder buffer, Matrix4f pose, float size, int moonU, int moonV) {
        buffer.vertex(pose, -size, 100.0F, -size).uv(moonU / 4.0F, (moonV + 1) / 2.0F).endVertex();
        buffer.vertex(pose, size, 100.0F, -size).uv((moonU + 1) / 4.0F, (moonV + 1) / 2.0F).endVertex();
        buffer.vertex(pose, size, 100.0F, size).uv((moonU + 1) / 4.0F, moonV / 2.0F).endVertex();
        buffer.vertex(pose, -size, 100.0F, size).uv(moonU / 4.0F, moonV / 2.0F).endVertex();
    }

    private static void renderPlanets(
            ClientLevel level,
            Tesselator tesselator,
            PoseStack poseStack,
            float partialTick,
            float rainAlpha,
            float starAlpha) {
        final float alpha = Mth.clamp(
                (0.2f + 0.8f * starAlpha) * rainAlpha * TFGConfig.CLIENT.planetBrightness.get().floatValue(),
                0.0f,
                1.0f);
        if (alpha <= 0.0f) {
            return;
        }

        final float skyRotation = TFGPlanetVisuals.getSkyRotationDegrees(level, partialTick);
        final Vector2f sunVector = TFGPlanetVisuals.getSunVector();
        final Map<String, SkyPlanet> catalog = new HashMap<>(cachedPlanets.size());
        for (SkyPlanet planet : cachedPlanets) {
            catalog.put(planet.name(), planet);
        }
        final Map<String, Vector2f> planetVectors = new HashMap<>(cachedPlanets.size());
        final BufferBuilder buffer = tesselator.getBuilder();

        for (SkyPlanet planet : cachedPlanets) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees((float) TFGPlanetVisuals.getInclination(planet, catalog)));

            final Vector2f planetVector = TFGPlanetVisuals.getPlanetVector(
                    level, planet, catalog, planetVectors, sunVector, partialTick);

            final float angleToSun;
            if (planet.isMoon()) {
                final Vector2f parentVector = planet.parentBody()
                        .map(catalog::get)
                        .map(parent -> planetVectors.get(parent.name()))
                        .orElse(sunVector);
                angleToSun = (float) TFGPlanetVisuals.getAngleToSun(sunVector, parentVector);
            } else {
                angleToSun = (float) TFGPlanetVisuals.getAngleToSun(sunVector, planetVector);
            }
            poseStack.mulPose(Axis.XP.rotationDegrees(skyRotation + angleToSun));

            if (planet.isMoon()) {
                poseStack.mulPose(Axis.YP.rotationDegrees((float) planet.eclipticPlaneDegrees()));
                planet.parentBody()
                        .map(catalog::get)
                        .map(parent -> planetVectors.get(parent.name()))
                        .ifPresent(parentVector -> poseStack.mulPose(Axis.XP.rotationDegrees(
                                (float) TFGPlanetVisuals.getAngleToParent(parentVector, planetVector))));
            }

            final float size = TFGPlanetVisuals.getPlanetScale(planet, planetVector);
            if (size <= 0.0f) {
                poseStack.popPose();
                continue;
            }

            final Matrix4f planetPose = poseStack.last().pose();

            RenderSystem.enableBlend();
            if (planet.isMoon()) {
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO);
            } else {
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO);
            }

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, planet.texture());
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            drawTexturedQuad(buffer, planetPose, size);
            BufferUploader.drawWithShader(buffer.end());
            poseStack.popPose();
        }
    }

    private static Matrix4f rotateTo(PoseStack stack, SkyPos pos) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotation(pos.azimuth()));
        stack.mulPose(Axis.XN.rotation(pos.zenith()));
        final Matrix4f pose = new Matrix4f(stack.last().pose());
        stack.popPose();
        return pose;
    }

    static Matrix4f createSkyRotationMatrix(SkyPos starPos) {
        final PoseStack poseStack = new PoseStack();
        return rotateSkyTo(poseStack, starPos);
    }

    private static Matrix4f rotateSkyTo(PoseStack stack, SkyPos pos) {
        stack.pushPose();
        stack.mulPose(Axis.XN.rotation(pos.zenith()));
        stack.mulPose(Axis.YN.rotation(pos.azimuth()));
        final Matrix4f pose = new Matrix4f(stack.last().pose());
        stack.popPose();
        return pose;
    }

    private static boolean doesMobEffectBlockSky(Camera camera) {
        return camera.getEntity() instanceof LivingEntity entity
                && (entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS));
    }
}
