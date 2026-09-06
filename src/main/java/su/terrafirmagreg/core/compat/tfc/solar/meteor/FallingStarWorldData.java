package su.terrafirmagreg.core.compat.tfc.solar.meteor;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

public final class FallingStarWorldData extends SavedData {
    public static final String DATA_ID = "tfg_falling_stars";

    private long nextShowerDay = -1L;
    private boolean meteorShowerActive;
    private int showerTicksRemaining;
    private double showerRadiantX;
    private double showerRadiantZ;
    private long nightlyDay = -1L;
    private final List<Long> nightlySpawnGameTimes = new ArrayList<>();

    public static FallingStarWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                FallingStarWorldData::load,
                FallingStarWorldData::new,
                DATA_ID);
    }

    public static FallingStarWorldData get(ServerLevel level) {
        return get(level.getServer());
    }

    private static FallingStarWorldData load(CompoundTag tag) {
        final FallingStarWorldData data = new FallingStarWorldData();
        data.nextShowerDay = tag.getLong("nextShowerDay");
        data.meteorShowerActive = tag.getBoolean("meteorShowerActive");
        data.showerTicksRemaining = tag.getInt("showerTicksRemaining");
        data.showerRadiantX = tag.getDouble("showerRadiantX");
        data.showerRadiantZ = tag.getDouble("showerRadiantZ");
        data.nightlyDay = tag.getLong("nightlyDay");
        final ListTag nightly = tag.getList("nightlySpawnGameTimes", Tag.TAG_LONG);
        for (int index = 0; index < nightly.size(); index++) {
            data.nightlySpawnGameTimes.add(((LongTag) nightly.get(index)).getAsLong());
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
        tag.putLong("nextShowerDay", nextShowerDay);
        tag.putBoolean("meteorShowerActive", meteorShowerActive);
        tag.putInt("showerTicksRemaining", showerTicksRemaining);
        tag.putDouble("showerRadiantX", showerRadiantX);
        tag.putDouble("showerRadiantZ", showerRadiantZ);
        tag.putLong("nightlyDay", nightlyDay);
        final ListTag nightly = new ListTag();
        for (Long spawnTime : nightlySpawnGameTimes) {
            nightly.add(net.minecraft.nbt.LongTag.valueOf(spawnTime));
        }
        tag.put("nightlySpawnGameTimes", nightly);
        return tag;
    }

    public void ensureNextShowerScheduled(RandomSource random, long currentDay) {
        if (nextShowerDay < 0L) {
            scheduleNextShower(random, currentDay);
        }
    }

    public void scheduleNextShower(RandomSource random, long currentDay) {
        nextShowerDay = currentDay + 2L + random.nextInt(4);
        setDirty();
    }

    public void startMeteorShower(int durationTicks, double radiantX, double radiantZ) {
        meteorShowerActive = true;
        showerTicksRemaining = durationTicks;
        showerRadiantX = radiantX;
        showerRadiantZ = radiantZ;
        setDirty();
    }

    public int tickMeteorShower() {
        showerTicksRemaining = Math.max(0, showerTicksRemaining - 1);
        setDirty();
        return showerTicksRemaining;
    }

    public void finishMeteorShower(RandomSource random, long currentDay) {
        meteorShowerActive = false;
        showerTicksRemaining = 0;
        showerRadiantX = 0.0;
        showerRadiantZ = 0.0;
        scheduleNextShower(random, currentDay);
        setDirty();
    }

    public void ensureNightlySchedule(
            RandomSource random,
            long currentDay,
            long currentGameTime,
            int minStars,
            int maxStars) {
        if (nightlyDay == currentDay) {
            return;
        }
        nightlyDay = currentDay;
        nightlySpawnGameTimes.clear();
        final int count = minStars + random.nextInt(Math.max(1, maxStars - minStars + 1));
        for (int index = 0; index < count; index++) {
            nightlySpawnGameTimes.add(currentGameTime + 200L + random.nextInt(8_000) + index * 500L);
        }
        nightlySpawnGameTimes.sort(Long::compare);
        setDirty();
    }

    public boolean consumeDueNightlyStar(long currentGameTime) {
        if (nightlySpawnGameTimes.isEmpty()) {
            return false;
        }
        if (nightlySpawnGameTimes.get(0) > currentGameTime) {
            return false;
        }
        nightlySpawnGameTimes.remove(0);
        setDirty();
        return true;
    }

    public void clearNightlySchedule() {
        nightlySpawnGameTimes.clear();
        nightlyDay = -1L;
        setDirty();
    }

    public long getNextShowerDay() {
        return nextShowerDay;
    }

    public boolean isMeteorShowerActive() {
        return meteorShowerActive;
    }

    public double getShowerRadiantX() {
        return showerRadiantX;
    }

    public double getShowerRadiantZ() {
        return showerRadiantZ;
    }

    public int getShowerTicksRemaining() {
        return showerTicksRemaining;
    }

    public int getPendingNightlyCount() {
        return nightlySpawnGameTimes.size();
    }

    public long getNextNightlySpawnGameTime() {
        return nightlySpawnGameTimes.isEmpty() ? -1L : nightlySpawnGameTimes.get(0);
    }
}
