package su.terrafirmagreg.core.compat.tfc.solar.meteor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import su.terrafirmagreg.core.network.TFGNetworkHandler;
import su.terrafirmagreg.core.network.packet.FallingStarSpawnPacket;

public final class FallingStarSpawner {
    private static final double[] SHOWER_BASE_ANGLES = {
            Math.toRadians(28.0),
            Math.toRadians(118.0),
            Math.toRadians(208.0),
            Math.toRadians(298.0)
    };
    private static final double NORMAL_DIRECTION_JITTER = Math.toRadians(18.0);
    private static final double SHOWER_DIRECTION_JITTER = Math.toRadians(5.5);
    private static final int FAST_METEOR_CHANCE = 9;

    private static final float[][] COLORS = {
            { 1.0F, 0.97F, 0.85F },
            { 1.0F, 0.82F, 0.4F },
            { 0.25F, 0.9F, 0.35F },
            { 0.35F, 0.65F, 1.0F },
            { 0.7F, 1.0F, 0.3F },
            { 0.95F, 0.42F, 1.0F },
            { 0.35F, 1.0F, 0.92F }
    };
    private static final int[] COLOR_WEIGHTS = { 60, 20, 8, 8, 4 };

    private FallingStarSpawner() {
    }

    public static double[] pickShowerDirection(RandomSource random) {
        final double angle = SHOWER_BASE_ANGLES[random.nextInt(SHOWER_BASE_ANGLES.length)]
                + (random.nextDouble() - 0.5) * NORMAL_DIRECTION_JITTER;
        return new double[] { Math.cos(angle), Math.sin(angle) };
    }

    public static double[] pickSporadicDirection(RandomSource random) {
        final double angle = random.nextDouble() * Math.PI * 2.0;
        return new double[] { Math.cos(angle), Math.sin(angle) };
    }

    public static boolean spawnStar(ServerLevel level, boolean isShower, double radiantX, double radiantZ) {
        return spawnStar(level, isShower, radiantX, radiantZ, null, -1.0F);
    }

    public static boolean spawnStarFor(ServerPlayer player, boolean isShower, double radiantX, double radiantZ) {
        return spawnStarFor(player, isShower, radiantX, radiantZ, null, -1.0F);
    }

    public static boolean spawnStarFor(
            ServerPlayer player,
            boolean isShower,
            double radiantX,
            double radiantZ,
            float[] forcedColor,
            float forcedScale) {
        final ServerLevel level = player.serverLevel();
        final FallingStarSpawnPacket packet = createPacket(
                level,
                List.of(player),
                isShower,
                radiantX,
                radiantZ,
                forcedColor,
                forcedScale);
        TFGNetworkHandler.sendFallingStar(level, packet);
        return true;
    }

    public static int spawnTrailingPairFor(ServerPlayer player, boolean isShower, double radiantX, double radiantZ) {
        final ServerLevel level = player.serverLevel();
        final List<ServerPlayer> players = List.of(player);
        final FallingStarSpawnPacket lead = createPacket(level, players, isShower, radiantX, radiantZ, null, -1.0F);
        final double horizontalSpeed = Math.sqrt(lead.velX() * lead.velX() + lead.velZ() * lead.velZ());
        final double sideX = horizontalSpeed < 0.001 ? 0.0 : -lead.velZ() / horizontalSpeed;
        final double sideZ = horizontalSpeed < 0.001 ? 0.0 : lead.velX() / horizontalSpeed;
        final double sideSign = level.getRandom().nextBoolean() ? 1.0 : -1.0;
        final FallingStarSpawnPacket trailing = new FallingStarSpawnPacket(
                lead.originX() - lead.velX() * 6.0 + sideX * 1.15 * sideSign,
                lead.originY() - lead.velY() * 6.0 - 1.65,
                lead.originZ() - lead.velZ() * 6.0 + sideZ * 1.15 * sideSign,
                lead.velX() * 0.96 + sideX * 0.025 * sideSign,
                lead.velY() - 0.018,
                lead.velZ() * 0.96 + sideZ * 0.025 * sideSign,
                lead.red(),
                lead.green(),
                lead.blue(),
                Math.max(0.25F, lead.scale() * 0.92F));
        TFGNetworkHandler.sendFallingStar(level, lead);
        TFGNetworkHandler.sendFallingStar(level, trailing);
        return 2;
    }

    public static boolean spawnStar(
            ServerLevel level,
            boolean isShower,
            double radiantX,
            double radiantZ,
            float[] forcedColor,
            float forcedScale) {
        final List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return false;
        }
        final FallingStarSpawnPacket packet = createPacket(level, players, isShower, radiantX, radiantZ, forcedColor, forcedScale);
        TFGNetworkHandler.sendFallingStar(level, packet);
        return true;
    }

    public static int spawnTrailingPair(ServerLevel level, boolean isShower, double radiantX, double radiantZ) {
        final List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return 0;
        }
        final FallingStarSpawnPacket lead = createPacket(level, players, isShower, radiantX, radiantZ, null, -1.0F);
        final double horizontalSpeed = Math.sqrt(lead.velX() * lead.velX() + lead.velZ() * lead.velZ());
        final double sideX = horizontalSpeed < 0.001 ? 0.0 : -lead.velZ() / horizontalSpeed;
        final double sideZ = horizontalSpeed < 0.001 ? 0.0 : lead.velX() / horizontalSpeed;
        final double sideSign = level.getRandom().nextBoolean() ? 1.0 : -1.0;
        final FallingStarSpawnPacket trailing = new FallingStarSpawnPacket(
                lead.originX() - lead.velX() * 6.0 + sideX * 1.15 * sideSign,
                lead.originY() - lead.velY() * 6.0 - 1.65,
                lead.originZ() - lead.velZ() * 6.0 + sideZ * 1.15 * sideSign,
                lead.velX() * 0.96 + sideX * 0.025 * sideSign,
                lead.velY() - 0.018,
                lead.velZ() * 0.96 + sideZ * 0.025 * sideSign,
                lead.red(),
                lead.green(),
                lead.blue(),
                Math.max(0.25F, lead.scale() * 0.92F));
        TFGNetworkHandler.sendFallingStar(level, lead);
        TFGNetworkHandler.sendFallingStar(level, trailing);
        return 2;
    }

    private static FallingStarSpawnPacket createPacket(
            ServerLevel level,
            List<ServerPlayer> players,
            boolean isShower,
            double radiantX,
            double radiantZ,
            float[] forcedColor,
            float forcedScale) {
        final RandomSource random = level.getRandom();
        final ServerPlayer anchor = selectAnchor(level, players, random);
        final Vec3 forward = horizontalLook(anchor);
        final double rightX = -forward.z();
        final double rightZ = forward.x();
        final double angle = isShower ? showerAngle(random, radiantX, radiantZ) : random.nextDouble() * Math.PI * 2.0;
        final double dirX = Math.cos(angle);
        final double dirZ = Math.sin(angle);
        final double perpX = -dirZ;
        final double viewDistance = 86.0 + random.nextDouble() * 46.0;
        final double sideOffset = (random.nextDouble() - 0.5) * 58.0;
        final double passX = anchor.getX() + forward.x() * viewDistance + rightX * sideOffset;
        final double passZ = anchor.getZ() + forward.z() * viewDistance + rightZ * sideOffset;
        final double pathOffset = (random.nextDouble() - 0.5) * 24.0;
        final double startBehind = 54.0 + random.nextDouble() * 28.0;
        final double originX = passX - dirX * startBehind + perpX * pathOffset;
        final double originY = skyHeightNearPlayer(level, anchor.getY(), random);
        final double originZ = passZ - dirZ * startBehind + dirX * pathOffset;

        final boolean naturalScale = forcedScale <= 0.0F;
        final boolean fastMeteor = naturalScale && random.nextInt(FAST_METEOR_CHANCE) == 0;
        final boolean fireball = naturalScale && (fastMeteor || random.nextInt(18) == 0);
        final double speed = fastMeteor
                ? 3.55 + random.nextDouble() * 1.35
                : fireball ? 2.45 + random.nextDouble() * 0.85 : 1.85 + random.nextDouble() * 0.7;
        final double velX = dirX * speed;
        final double velZ = dirZ * speed;
        final double velY = fastMeteor
                ? -0.03 - random.nextDouble() * 0.06
                : fireball ? -0.04 - random.nextDouble() * 0.08 : -0.035 - random.nextDouble() * 0.07;
        final float[] color = forcedColor == null ? pickNaturalColor(random) : forcedColor;
        float baseScale = forcedScale > 0.0F
                ? forcedScale
                : fastMeteor ? 1.85F + random.nextFloat() * 1.15F
                        : fireball ? 1.65F + random.nextFloat() * 0.95F
                                : 1.0F + random.nextFloat() * 0.75F;
        if (naturalScale && random.nextInt(180) == 0) {
            baseScale *= 1.45F + random.nextFloat() * 0.55F;
        }
        final float scale = Mth.clamp(baseScale, 0.25F, 6.0F);
        return new FallingStarSpawnPacket(originX, originY, originZ, velX, velY, velZ, color[0], color[1], color[2], scale);
    }

    private static float[] pickNaturalColor(RandomSource random) {
        if (random.nextInt(160) == 0) {
            return COLORS[5 + random.nextInt(2)].clone();
        }
        int roll = random.nextInt(100);
        int cumulative = 0;
        for (int index = 0; index < COLOR_WEIGHTS.length; index++) {
            cumulative += COLOR_WEIGHTS[index];
            if (roll < cumulative) {
                return COLORS[index].clone();
            }
        }
        return COLORS[0].clone();
    }

    public static float[] moonBiasedColor(RandomSource random, int moonPhase) {
        return switch (moonPhase) {
            case 0 -> random.nextInt(100) < 24 ? COLORS[random.nextBoolean() ? 0 : 1].clone() : null;
            case 1, 7 -> random.nextInt(100) < 20 ? COLORS[0].clone() : null;
            case 3, 5 -> random.nextInt(100) < 20 ? COLORS[3].clone() : null;
            case 4 -> random.nextInt(100) < 35 ? COLORS[random.nextBoolean() ? 3 : 6].clone() : null;
            default -> null;
        };
    }

    private static ServerPlayer selectAnchor(ServerLevel level, List<ServerPlayer> players, RandomSource random) {
        final List<ServerPlayer> outdoorPlayers = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (hasOpenSky(level, player)) {
                outdoorPlayers.add(player);
            }
        }
        final List<ServerPlayer> candidates = outdoorPlayers.isEmpty() ? players : outdoorPlayers;
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static boolean hasOpenSky(ServerLevel level, ServerPlayer player) {
        final int x = (int) Math.floor(player.getX());
        final int z = (int) Math.floor(player.getZ());
        final int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return surfaceY <= player.getY() + 4.0 || level.canSeeSkyFromBelowWater(BlockPos.containing(player.position()));
    }

    private static Vec3 horizontalLook(ServerPlayer player) {
        final Vec3 look = player.getLookAngle();
        final double length = Math.sqrt(look.x() * look.x() + look.z() * look.z());
        return length < 0.001 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(look.x() / length, 0.0, look.z() / length);
    }

    private static double showerAngle(RandomSource random, double directionX, double directionZ) {
        final double length = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (length < 0.001) {
            return random.nextDouble() * Math.PI * 2.0;
        }
        final double baseAngle = Math.atan2(directionZ / length, directionX / length);
        return baseAngle + (random.nextDouble() - 0.5) * SHOWER_DIRECTION_JITTER;
    }

    private static double skyHeightNearPlayer(ServerLevel level, double playerY, RandomSource random) {
        final double maxBuildY = level.getMinBuildHeight() + level.getHeight() - 12.0;
        final double cloudSafeY = 206.0;
        final double naturalY = playerY + 34.0 + random.nextDouble() * 38.0;
        if (playerY + 24.0 >= maxBuildY) {
            return naturalY;
        }
        final double minVisibleY = Math.max(cloudSafeY, playerY + 24.0);
        final double safeMinY = Math.min(minVisibleY, maxBuildY);
        final double aboveCloudY = safeMinY + random.nextDouble() * 38.0;
        return Mth.clamp(Math.max(naturalY, aboveCloudY), safeMinY, maxBuildY);
    }
}
