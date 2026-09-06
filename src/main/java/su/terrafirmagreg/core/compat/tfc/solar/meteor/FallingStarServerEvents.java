package su.terrafirmagreg.core.compat.tfc.solar.meteor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.config.TFGConfig;

public final class FallingStarServerEvents {
    private static final int SHOWER_START_MIN_REMAINING_NIGHT_TICKS = 600;

    private FallingStarServerEvents() {
    }

    public static void onWorldTick(ServerLevel level) {
        if (!SolarCalendarBackport.isEnabled() || !TFGConfig.SERVER.enableFallingStars.get()) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        if (!FallingStarNightHelper.isMeteorNight(level)) {
            FallingStarWorldData.get(level).clearNightlySchedule();
            return;
        }

        final RandomSource random = level.getRandom();
        final long currentDay = FallingStarNightHelper.getCalendarDay(level);
        final int nightTick = FallingStarNightHelper.getNightTick(level);
        final FallingStarWorldData data = FallingStarWorldData.get(level);
        final int moonPhase = FallingStarNightHelper.getMoonPhase(level);
        final double moonMultiplier = moonSpawnMultiplier(moonPhase);

        data.ensureNextShowerScheduled(random, currentDay);
        data.ensureNightlySchedule(
                random,
                currentDay,
                level.getGameTime(),
                scaledCount(TFGConfig.SERVER.fallingStarNightlyMin.get(), moonMultiplier),
                scaledCount(TFGConfig.SERVER.fallingStarNightlyMax.get(), moonMultiplier));

        if (data.consumeDueNightlyStar(level.getGameTime())) {
            spawnMoonBiasedSporadicStar(level, random, moonPhase);
        }

        if (data.isMeteorShowerActive()) {
            if (data.tickMeteorShower() <= 0) {
                data.finishMeteorShower(random, currentDay);
            } else if (shouldTrigger(random, TFGConfig.SERVER.fallingStarShowerClusterChance.get())) {
                spawnBurst(level, true, data.getShowerRadiantX(), data.getShowerRadiantZ(),
                        randomBetween(random, TFGConfig.SERVER.fallingStarShowerClusterMin.get(),
                                TFGConfig.SERVER.fallingStarShowerClusterMax.get()));
            } else if (shouldTrigger(random, TFGConfig.SERVER.fallingStarShowerChance.get())) {
                FallingStarSpawner.spawnStar(level, true, data.getShowerRadiantX(), data.getShowerRadiantZ());
            }
            return;
        }

        if (currentDay >= data.getNextShowerDay() && nightTick >= 0 && 10_000 - nightTick > SHOWER_START_MIN_REMAINING_NIGHT_TICKS) {
            final double[] showerDirection = FallingStarSpawner.pickShowerDirection(random);
            data.startMeteorShower(TFGConfig.SERVER.fallingStarShowerDuration.get(), showerDirection[0], showerDirection[1]);
            spawnBurst(level, true, showerDirection[0], showerDirection[1],
                    randomBetween(random, TFGConfig.SERVER.fallingStarBurstMin.get(), TFGConfig.SERVER.fallingStarBurstMax.get()));
            return;
        }

        if (shouldTrigger(random, adjustedChance(TFGConfig.SERVER.fallingStarBurstChance.get(), moonMultiplier))) {
            final double[] direction = FallingStarSpawner.pickSporadicDirection(random);
            spawnBurst(level, true, direction[0], direction[1],
                    randomBetween(random, TFGConfig.SERVER.fallingStarBurstMin.get(), TFGConfig.SERVER.fallingStarBurstMax.get()));
            return;
        }

        if (shouldTrigger(random, adjustedChance(TFGConfig.SERVER.fallingStarClusterChance.get(), moonMultiplier))) {
            final double[] direction = FallingStarSpawner.pickSporadicDirection(random);
            spawnBurst(level, true, direction[0], direction[1],
                    randomBetween(random, TFGConfig.SERVER.fallingStarClusterMin.get(), TFGConfig.SERVER.fallingStarClusterMax.get()));
        }
    }

    private static void spawnBurst(ServerLevel level, boolean isShower, double directionX, double directionZ, int count) {
        for (int index = 0; index < count; index++) {
            FallingStarSpawner.spawnStar(level, isShower, directionX, directionZ);
        }
    }

    private static boolean spawnMoonBiasedSporadicStar(ServerLevel level, RandomSource random, int moonPhase) {
        return FallingStarSpawner.spawnStar(level, false, 0.0, 0.0, FallingStarSpawner.moonBiasedColor(random, moonPhase), -1.0F);
    }

    private static int randomBetween(RandomSource random, int min, int max) {
        return min + random.nextInt(Math.max(1, max - min + 1));
    }

    private static boolean shouldTrigger(RandomSource random, int chance) {
        return chance > 0 && random.nextInt(chance) == 0;
    }

    private static int adjustedChance(int baseChance, double multiplier) {
        return baseChance <= 0 ? 0 : Math.max(1, (int) Math.round(baseChance / multiplier));
    }

    private static int scaledCount(int baseCount, double multiplier) {
        return Math.max(1, (int) Math.round(baseCount * multiplier));
    }

    private static double moonSpawnMultiplier(int moonPhase) {
        return switch (moonPhase) {
            case 0 -> 0.7;
            case 1, 7 -> 0.85;
            case 3, 5 -> 1.15;
            case 4 -> 1.35;
            default -> 1.0;
        };
    }
}
