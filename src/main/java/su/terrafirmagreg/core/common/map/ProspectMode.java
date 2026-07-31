package su.terrafirmagreg.core.common.map;

import net.minecraft.core.BlockPos;

/** How ore data is shown on the map for a prospected cluster. */
public enum ProspectMode {
    /** Bare-hand ore block click — names only, {@code scanRange = 0}. */
    HAND,
    /** Copper / bronze propicks — ore names without block counts. */
    NAMES_ONLY,
    /** Normal+ propicks, hammer, GT electric prospector — names with block counts. */
    WITH_COUNTS;

    /** Ore blocks within this radius are grouped into one map marker cluster. */
    public static final int CLUSTER_RADIUS = 12;
    public static final int CLUSTER_RADIUS_SQ = CLUSTER_RADIUS * CLUSTER_RADIUS;

    /** Hand-prospect clicks within this radius merge into one marker. */
    public static final int HAND_MERGE_RADIUS_SQ = CLUSTER_RADIUS_SQ;

    public static int distSq(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean inBox(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }
}
