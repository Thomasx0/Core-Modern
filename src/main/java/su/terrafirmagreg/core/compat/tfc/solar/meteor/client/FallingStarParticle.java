package su.terrafirmagreg.core.compat.tfc.solar.meteor.client;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import su.terrafirmagreg.core.config.TFGConfig;

/**
 * Falling-star particle ported from Celestia {@code StarParticle}.
 * <p>
 * Cinematic mode draws the trail as camera-facing quads attached to this particle
 * (no gravity-affected dust/glow that can pile up under the world).
 * Non-cinematic mode uses a short dust trail only.
 */
@OnlyIn(Dist.CLIENT)
public class FallingStarParticle extends TextureSheetParticle {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final long ACTIVE_COUNTER_STALE_TICKS = 260L;
    private static final float PHOTO_FADE_IN_TICKS = 7.0F;
    private static final float PHOTO_FULL_VISIBILITY_PORTION = 0.64F;
    private static final double PHOTO_LINE_MIN_LENGTH = 78.0;
    private static final double PHOTO_LINE_SPEED_LENGTH = 20.0;

    private static ClientLevel activeWorld;
    private static int activeStars;
    private static long lastActiveStarGameTime;

    private final SpriteSet sprites;
    private final Vector3f trailColor;
    private final boolean cinematicTrail;
    private final ClientLevel countedWorld;
    private boolean countedActive = true;

    protected FallingStarParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            FallingStarParticleOptions options,
            SpriteSet sprites) {
        // Avoid Particle(x,y,z,vx,vy,vz) which randomizes/rescales velocity.
        super(level, x, y, z);
        this.sprites = sprites;
        this.rCol = options.getRed();
        this.gCol = options.getGreen();
        this.bCol = options.getBlue();
        this.alpha = 1.0F;
        this.trailColor = new Vector3f(this.rCol, this.gCol, this.bCol);
        this.cinematicTrail = TFGConfig.CLIENT.fallingStarCinematicTrail.get();
        this.quadSize = options.getScale() * 0.78F * TFGConfig.CLIENT.fallingStarScale.get().floatValue();
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.lifetime = cinematicTrail ? 118 + this.random.nextInt(54) : 30 + this.random.nextInt(26);
        this.xd = velocityX;
        this.yd = velocityY;
        this.zd = velocityZ;
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
        this.countedWorld = level;
        activeStars++;
        lastActiveStarGameTime = level.getGameTime();
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        final double nextX = this.x + this.xd;
        final double nextY = this.y + this.yd;
        final double nextZ = this.z + this.zd;
        if (!cinematicTrail) {
            emitDustTrail(this.x, this.y, this.z, nextX, nextY, nextZ);
        }
        final float progress = (float) this.age / (float) this.lifetime;
        final float fade = 1.0F - progress;
        this.setAlpha(Math.max(0.12F, fade * fade) * TFGConfig.CLIENT.fallingStarBrightness.get().floatValue());
        this.setSpriteFromAge(sprites);
        this.move(nextX - this.x, nextY - this.y, nextZ - this.z);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (!cinematicTrail) {
            super.render(buffer, camera, partialTick);
            return;
        }
        renderCinematicTrail(buffer, camera, partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void remove() {
        super.remove();
        if (countedActive) {
            countedActive = false;
            if (countedWorld == activeWorld) {
                activeStars = Math.max(0, activeStars - 1);
            }
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return FULL_BRIGHT;
    }

    private void emitDustTrail(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        final double dx = toX - fromX;
        final double dy = toY - fromY;
        final double dz = toZ - fromZ;
        final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final int nodes = Math.max(1, Math.min(6, (int) Math.ceil(distance / 0.35)));
        final float progress = (float) this.age / (float) this.lifetime;
        final float trailScale = Math.max(0.06F, this.quadSize * (0.24F * (1.0F - progress) + 0.08F));
        for (int index = 1; index <= nodes; index++) {
            final double nodeProgress = (double) index / (double) nodes;
            this.level.addParticle(
                    new DustParticleOptions(trailColor, trailScale),
                    fromX + dx * nodeProgress,
                    fromY + dy * nodeProgress,
                    fromZ + dz * nodeProgress,
                    0.0,
                    0.0,
                    0.0);
        }
    }

    private void renderCinematicTrail(VertexConsumer buffer, Camera camera, float partialTick) {
        final double speed = Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        if (speed < 1.0E-4) {
            super.render(buffer, camera, partialTick);
            return;
        }

        final double dirX = this.xd / speed;
        final double dirY = this.yd / speed;
        final double dirZ = this.zd / speed;
        final float appear = smoothStep(Math.min(1.0F, (this.age + partialTick) / PHOTO_FADE_IN_TICKS));
        final double fullLineLength = Math.max(PHOTO_LINE_MIN_LENGTH, speed * PHOTO_LINE_SPEED_LENGTH + this.quadSize * 4.6);
        final double lineLength = fullLineLength * (0.18 + appear * 0.82);
        final int samples = Mth.clamp((int) Math.ceil(lineLength / 0.35), 24, 96);
        final float progress = (float) this.age / (float) this.lifetime;
        final float fade = photographicFade(progress);
        final float visibility = fade * appear * TFGConfig.CLIENT.fallingStarBrightness.get().floatValue();
        final float lineScale = Math.max(0.16F, this.quadSize * (0.17F * fade + 0.1F)) * (0.45F + appear * 0.55F);
        final float headScale = Math.max(0.34F, this.quadSize * (0.46F * fade + 0.22F)) * (0.55F + appear * 0.45F);

        final double headX = Mth.lerp(partialTick, this.xo, this.x);
        final double headY = Mth.lerp(partialTick, this.yo, this.y);
        final double headZ = Mth.lerp(partialTick, this.zo, this.z);
        final Vec3 cameraPos = camera.getPosition();
        final Quaternionf rotation = new Quaternionf(camera.rotation());
        final int light = getLightColor(partialTick);

        for (int index = 0; index <= samples; index++) {
            final double t = (double) index / (double) samples;
            final double taper = 1.0 - t;
            final float alpha = (float) (visibility * (0.05 + taper * taper * 0.72));
            final float colorBlend = (float) Mth.clamp((t - 0.08) / 0.38, 0.0, 1.0);
            final float red = Mth.lerp(colorBlend, 1.0F, Math.max(0.38F, this.rCol));
            final float green = Mth.lerp(colorBlend, 1.0F, Math.max(0.45F, this.gCol));
            final float blue = Mth.lerp(colorBlend, 1.0F, Math.max(0.58F, this.bCol));
            final float size = (float) Math.max(0.08, lineScale * (0.38 + taper * 0.76));
            final float renderX = (float) (headX - dirX * lineLength * t - cameraPos.x);
            final float renderY = (float) (headY - dirY * lineLength * t - cameraPos.y);
            final float renderZ = (float) (headZ - dirZ * lineLength * t - cameraPos.z);
            renderBillboard(buffer, rotation, renderX, renderY, renderZ, size, red, green, blue, alpha, light);
        }

        final float headXRender = (float) (headX - cameraPos.x);
        final float headYRender = (float) (headY - cameraPos.y);
        final float headZRender = (float) (headZ - cameraPos.z);
        renderBillboard(
                buffer,
                rotation,
                headXRender,
                headYRender,
                headZRender,
                headScale,
                1.0F,
                1.0F,
                1.0F,
                Math.min(1.0F, visibility * 1.2F),
                light);
    }

    private void renderBillboard(
            VertexConsumer buffer,
            Quaternionf rotation,
            float x,
            float y,
            float z,
            float size,
            float red,
            float green,
            float blue,
            float alpha,
            int light) {
        final float half = size * 0.5F;
        final Vector3f[] corners = new Vector3f[] {
                new Vector3f(-half, -half, 0.0F),
                new Vector3f(-half, half, 0.0F),
                new Vector3f(half, half, 0.0F),
                new Vector3f(half, -half, 0.0F)
        };
        for (Vector3f corner : corners) {
            corner.rotate(rotation);
            corner.add(x, y, z);
        }
        final float u0 = getU0();
        final float u1 = getU1();
        final float v0 = getV0();
        final float v1 = getV1();
        buffer.vertex(corners[0].x(), corners[0].y(), corners[0].z()).uv(u1, v1).color(red, green, blue, alpha).uv2(light).endVertex();
        buffer.vertex(corners[1].x(), corners[1].y(), corners[1].z()).uv(u1, v0).color(red, green, blue, alpha).uv2(light).endVertex();
        buffer.vertex(corners[2].x(), corners[2].y(), corners[2].z()).uv(u0, v0).color(red, green, blue, alpha).uv2(light).endVertex();
        buffer.vertex(corners[3].x(), corners[3].y(), corners[3].z()).uv(u0, v1).color(red, green, blue, alpha).uv2(light).endVertex();
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float photographicFade(float progress) {
        if (progress <= PHOTO_FULL_VISIBILITY_PORTION) {
            return 1.0F;
        }
        final float fadeProgress = Mth.clamp((progress - PHOTO_FULL_VISIBILITY_PORTION) / (1.0F - PHOTO_FULL_VISIBILITY_PORTION), 0.0F, 1.0F);
        return 1.0F - smoothStep(fadeProgress);
    }

    private static boolean canCreateStar(ClientLevel level, int maxConcurrentStars) {
        if (activeWorld != level) {
            activeWorld = level;
            activeStars = 0;
            lastActiveStarGameTime = level.getGameTime();
        }
        if (activeStars >= maxConcurrentStars && level.getGameTime() - lastActiveStarGameTime > ACTIVE_COUNTER_STALE_TICKS) {
            activeStars = 0;
        }
        return activeStars < maxConcurrentStars;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<FallingStarParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(
                FallingStarParticleOptions options,
                ClientLevel level,
                double x,
                double y,
                double z,
                double velocityX,
                double velocityY,
                double velocityZ) {
            if (!TFGConfig.CLIENT.drawFallingStars.get()) {
                return null;
            }
            if (!canCreateStar(level, TFGConfig.CLIENT.fallingStarMaxConcurrent.get())) {
                return null;
            }
            return new FallingStarParticle(level, x, y, z, velocityX, velocityY, velocityZ, options, sprites);
        }
    }
}
