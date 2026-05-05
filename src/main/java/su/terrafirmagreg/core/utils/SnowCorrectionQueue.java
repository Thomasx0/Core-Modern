package su.terrafirmagreg.core.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import su.terrafirmagreg.core.config.TFGConfig;

/**
 * Two-buffer FIFO (current vs next tick) with {@link #processTick(ServerLevel)} draining at most N chunks per call.
 */
public final class SnowCorrectionQueue {
    private static final int MAX_SITTING_FOR = 100;

    private static final ArrayList<Entry> currentTickChunks = new ArrayList<>();
    private static final ArrayList<Entry> nextTickChunks = new ArrayList<>();
    private static final Set<ChunkPos> pending = new HashSet<>();

    public record Entry(ChunkPos pos, int sittingFor) {
    }

    private SnowCorrectionQueue() {
    }

    public static boolean tryEnqueue(ChunkPos chunkPos) {
        if (pending.contains(chunkPos)) {
            return false;
        }
        pending.add(chunkPos);
        add(new Entry(chunkPos, 0), false);
        return true;
    }

    private static void add(Entry entry, boolean currentTick) {
        if (currentTick)
            currentTickChunks.add(entry);
        else
            nextTickChunks.add(entry);
    }

    static int queueSizeCurrentBuffer() {
        return currentTickChunks.size();
    }

    private static boolean isEmpty() {
        return currentTickChunks.isEmpty();
    }

    private static Entry pop() {
        return currentTickChunks.remove(0);
    }

    private static void shuffle() {
        currentTickChunks.addAll(nextTickChunks);
        nextTickChunks.clear();
    }

    public static void clear() {
        currentTickChunks.clear();
        nextTickChunks.clear();
        pending.clear();
    }

    public static boolean hasCornerNeighborsLoaded(ServerLevel level, ChunkPos chunkPos) {
        return level.hasChunk(chunkPos.x, chunkPos.z)
                && level.hasChunk(chunkPos.x - 1, chunkPos.z - 1)
                && level.hasChunk(chunkPos.x + 1, chunkPos.z - 1)
                && level.hasChunk(chunkPos.x - 1, chunkPos.z + 1)
                && level.hasChunk(chunkPos.x + 1, chunkPos.z + 1);
    }

    /**
     * Drains up to {@link su.terrafirmagreg.core.config.ServerConfig#snowCorrectionQueueMaxChunksPerTick} chunks;
     * skips entries until {@link #hasCornerNeighborsLoaded(ServerLevel, ChunkPos)} succeeds (cross-chunk updates).
     */
    public static void processTick(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        if (isEmpty()) {
            shuffle();
        }

        if (isEmpty()) {
            return;
        }

        int maxPerTick = TFGConfig.SERVER.snowCorrectionQueueMaxChunksPerTick.get();

        for (int i = 0; i < Mth.clamp(queueSizeCurrentBuffer(), 0, maxPerTick); i++) {
            if (currentTickChunks.isEmpty())
                break;

            Entry entry = pop();
            ChunkPos chunkPos = entry.pos();
            int sittingFor = entry.sittingFor();

            if (!hasCornerNeighborsLoaded(level, chunkPos)) {
                if (sittingFor < MAX_SITTING_FOR) {
                    add(new Entry(chunkPos, sittingFor + 1), false);
                    i--;
                } else {
                    pending.remove(chunkPos);
                }
                continue;
            }

            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            SnowCorrection.applyHeavyCorrection(level, chunk, chunk);

            pending.remove(chunkPos);
        }
    }
}
