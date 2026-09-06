package su.terrafirmagreg.core.compat.tfc.solar.client;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import su.terrafirmagreg.core.compat.tfc.solar.ClientSolarCalculatorBridge;
import su.terrafirmagreg.core.compat.tfc.solar.SkyPos;
import su.terrafirmagreg.core.config.TFGConfig;

final class ConstellationHighlightTracker {
    private static final float FADE_STEP = 0.01F;
    private static float[] highlightAlphas = new float[0];

    private ConstellationHighlightTracker() {
    }

    static void resize(List<Constellation> constellations) {
        highlightAlphas = new float[constellations.size()];
    }

    static void tick(ClientLevel level, Player player, Camera camera) {
        if (highlightAlphas.length == 0) {
            return;
        }
        if (!TFGConfig.CLIENT.drawConstellations.get()) {
            fadeAllOut();
            return;
        }

        if (!isHoldingSpyglass(player)) {
            fadeAllOut();
            return;
        }

        final SkyPos starPos = ClientSolarCalculatorBridge.getStarPosition(level, player.blockPosition());
        final Matrix4f skyMatrix = TFGSkyRenderer.createSkyRotationMatrix(starPos);
        final Vector3f lookDirection = camera.getLookVector();
        final double maxAngle = TFGConfig.CLIENT.constellationSpyglassAngleThreshold.get();
        final List<Constellation> constellations = TFGSkyRenderer.getCachedConstellations();

        for (int index = 0; index < constellations.size(); index++) {
            final boolean inView = isConstellationInView(constellations.get(index), lookDirection, skyMatrix, maxAngle);
            if (inView) {
                highlightAlphas[index] = Mth.clamp(highlightAlphas[index] + FADE_STEP, 0.0F, 1.0F);
            } else {
                highlightAlphas[index] = Mth.clamp(highlightAlphas[index] - FADE_STEP, 0.0F, 1.0F);
            }
        }
    }

    static void renderHighlighted(Matrix4f skyPose, Matrix4f projectionMatrix, float nightAlpha, Player player) {
        if (nightAlpha <= 0.0F || highlightAlphas.length == 0) {
            return;
        }
        if (!isHoldingSpyglass(player)) {
            return;
        }

        final List<Constellation> constellations = TFGSkyRenderer.getCachedConstellations();
        final float lineWidth = TFGConfig.CLIENT.constellationLineWidth.get().floatValue();
        final int red = (int) (TFGConfig.CLIENT.constellationRed.get() * 255.0);
        final int green = (int) (TFGConfig.CLIENT.constellationGreen.get() * 255.0);
        final int blue = (int) (TFGConfig.CLIENT.constellationBlue.get() * 255.0);
        final int baseAlpha = (int) (TFGConfig.CLIENT.constellationAlpha.get() * 255.0);

        final BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        boolean hasGeometry = false;
        for (int index = 0; index < constellations.size(); index++) {
            final float highlight = highlightAlphas[index];
            if (highlight <= 0.0F) {
                continue;
            }
            final int alpha = (int) Mth.clamp((baseAlpha + (255.0F - baseAlpha) * highlight) * nightAlpha, 0.0F, 255.0F);
            if (alpha <= 0) {
                continue;
            }
            for (Constellation.Edge edge : constellations.get(index).edges()) {
                TFGSkyRenderer.appendConstellationEdge(buffer, skyPose, edge, lineWidth, red, green, blue, alpha);
                hasGeometry = true;
            }
        }

        if (!hasGeometry) {
            buffer.end();
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void fadeAllOut() {
        for (int index = 0; index < highlightAlphas.length; index++) {
            highlightAlphas[index] = Mth.clamp(highlightAlphas[index] - FADE_STEP, 0.0F, 1.0F);
        }
    }

    private static boolean isHoldingSpyglass(Player player) {
        return player.getMainHandItem().is(Items.SPYGLASS) || player.getOffhandItem().is(Items.SPYGLASS);
    }

    private static boolean isConstellationInView(
            Constellation constellation,
            Vector3f lookDirection,
            Matrix4f skyMatrix,
            double maxAngle) {
        for (Constellation.Edge edge : constellation.edges()) {
            if (isCelestialInView(lookDirection, skyMatrix, edge.ascension1(), edge.declination1(), maxAngle)
                    || isCelestialInView(lookDirection, skyMatrix, edge.ascension2(), edge.declination2(), maxAngle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCelestialInView(
            Vector3f lookDirection,
            Matrix4f skyMatrix,
            double ascension,
            double declination,
            double maxAngleDegrees) {
        final Vector3f celestial = TFGSkyRenderer.toSpherePosition(ascension, declination, 1.0F);
        final Vector4f rotated = skyMatrix.transform(new Vector4f(celestial, 0.0F));
        final Vector3f worldDirection = new Vector3f(rotated.x(), rotated.y(), rotated.z()).normalize();
        return lookDirection.dot(worldDirection) >= (float) Math.cos(Math.toRadians(maxAngleDegrees));
    }
}
