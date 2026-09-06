package su.terrafirmagreg.core.compat.tfc.solar.client;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import su.terrafirmagreg.core.TFGCore;
import su.terrafirmagreg.core.compat.tfc.solar.SkyPos;
import su.terrafirmagreg.core.config.TFGConfig;

final class FancyBrightStarRenderer {
    static final ResourceLocation STAR_TWINKLE_TEXTURE = TFGCore.id("textures/environment/star_twinkle.png");
    private static final int SPRITE_COUNT = 5;
    private static final float SPRITE_HEIGHT = 1.0F / SPRITE_COUNT;

    private FancyBrightStarRenderer() {
    }

    static void render(
            Matrix4f skyPose,
            SkyPos starSkyPos,
            List<Star> brightStars,
            float nightAlpha,
            long gameTime,
            TFGSkyRenderer.StarMagnitudeScale magnitudeScale) {
        final int frequency = TFGConfig.CLIENT.starTwinkleFrequency.get();
        if (frequency <= 0 || nightAlpha <= 0.0F || brightStars.isEmpty()) {
            return;
        }

        final double starSize = TFGConfig.CLIENT.starSize.get();
        final boolean starColors = TFGConfig.CLIENT.starColors.get();
        final int vertexAlpha = (int) (nightAlpha * 255.0F);
        final Matrix4f skyRotationOnly = skyRotationMatrix(starSkyPos);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, STAR_TWINKLE_TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        final BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (Star star : brightStars) {
            final Vector3f celestial = celestialPosition(star);
            // Skip stars below the mathematical horizon so they don't appear as a
            // static "particle cluster" when looking into the void under the world.
            if (skyRotationOnly.transformPosition(new Vector3f(celestial)).y() <= 0.0F) {
                continue;
            }

            final long twinkleSeed = Double.doubleToLongBits(star.ascension()) ^ Double.doubleToLongBits(star.declination());
            final long twinkleOffset = RandomSource.create(twinkleSeed).nextInt(1000) * (long) (frequency + 1);
            final int spriteIndex = computeSpriteIndex(gameTime + twinkleOffset, frequency);
            final float v0 = spriteIndex * SPRITE_HEIGHT;
            final float v1 = v0 + SPRITE_HEIGHT;
            appendTexturedStar(
                    buffer,
                    skyPose,
                    celestial,
                    star,
                    magnitudeScale,
                    starSize,
                    starColors,
                    v0,
                    v1,
                    vertexAlpha);
        }

        BufferUploader.drawWithShader(buffer.end());
    }

    private static Matrix4f skyRotationMatrix(SkyPos starPos) {
        final Matrix4f matrix = new Matrix4f();
        matrix.rotate(Axis.XN.rotation(starPos.zenith()));
        matrix.rotate(Axis.YN.rotation(starPos.azimuth()));
        return matrix;
    }

    private static Vector3f celestialPosition(Star star) {
        double x = Math.cos(star.declination()) * Math.cos(star.ascension());
        double y = Math.cos(star.declination()) * Math.sin(star.ascension());
        double z = Math.sin(star.declination());
        final double length = Math.sqrt(x * x + y * y + z * z);
        if (length > 0.0) {
            x /= length;
            y /= length;
            z /= length;
        }
        return new Vector3f((float) (x * 100.0), (float) (y * 100.0), (float) (z * 100.0));
    }

    private static int computeSpriteIndex(long time, int cycleTime) {
        final int totalCycleTime = cycleTime * (SPRITE_COUNT - 1) * 2;
        final long modTime = time % totalCycleTime;
        int spriteIndex = Math.round(modTime / (float) cycleTime);
        if (spriteIndex >= SPRITE_COUNT) {
            spriteIndex = SPRITE_COUNT - (spriteIndex - SPRITE_COUNT + 1);
        }
        return spriteIndex;
    }

    private static void appendTexturedStar(
            BufferBuilder buffer,
            Matrix4f skyPose,
            Vector3f pos,
            Star star,
            TFGSkyRenderer.StarMagnitudeScale magnitudeScale,
            double starSize,
            boolean starColors,
            float v0,
            float v1,
            int alpha) {
        final TFGSkyRenderer.StarAppearance appearance = TFGSkyRenderer.computeStarAppearance(
                star.magnitude(), magnitudeScale, starSize);
        final float size = (float) appearance.size();
        if (size <= 0.0F) {
            return;
        }

        final long rotationSeed = Double.doubleToLongBits(star.ascension() * 31.0) ^ Double.doubleToLongBits(star.declination() * 17.0);
        final Quaternionf rotation = new Quaternionf()
                .rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), pos)
                .rotateZ(RandomSource.create(rotationSeed).nextFloat() * Mth.TWO_PI);

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

        putTexturedVertex(buffer, skyPose, new Vector3f(pos).add(new Vector3f(size, -size, 0).rotate(rotation)), 0.0F, v1, red, green, blue, alpha);
        putTexturedVertex(buffer, skyPose, new Vector3f(pos).add(new Vector3f(size, size, 0).rotate(rotation)), 0.0F, v0, red, green, blue, alpha);
        putTexturedVertex(buffer, skyPose, new Vector3f(pos).add(new Vector3f(-size, size, 0).rotate(rotation)), 1.0F, v0, red, green, blue, alpha);
        putTexturedVertex(buffer, skyPose, new Vector3f(pos).add(new Vector3f(-size, -size, 0).rotate(rotation)), 1.0F, v1, red, green, blue, alpha);
    }

    private static void putTexturedVertex(
            BufferBuilder buffer,
            Matrix4f pose,
            Vector3f position,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha) {
        buffer.vertex(pose, position.x(), position.y(), position.z()).uv(u, v).color(red, green, blue, alpha).endVertex();
    }
}
