package su.terrafirmagreg.core.common.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * One prospected ore cluster sent to the client for Xaero map rendering.
 * Serialized by {@link su.terrafirmagreg.core.network.packet.OreHighlightVeinPacket}.
 */

public record TFGProspectVeinMarker(
        BlockPos center,
        List<OreCount> ores,
        List<String> materialIds,
        int scanRange) {

    /** One ore type in a cluster, with optional block count (hammer / propick chat format). */

    public record OreCount(Component displayName, int count) {
    }

    public boolean isHandProspect() {
        return TFGOreVeinLang.isHandProspect(scanRange);
    }

    public boolean isNamesOnly() {
        return TFGOreVeinLang.isNamesOnlyProspect(scanRange, ores);
    }

    public static TFGProspectVeinMarker mergeHand(TFGProspectVeinMarker existing, TFGProspectVeinMarker added) {
        return new TFGProspectVeinMarker(
                existing.center(),
                mergeOres(existing.ores(), added.ores()),
                mergeMaterialIds(existing.materialIds(), added.materialIds()),
                0);
    }

    private static List<OreCount> mergeOres(List<OreCount> left, List<OreCount> right) {
        Map<String, Component> byKey = new LinkedHashMap<>();
        for (OreCount ore : left) {
            byKey.putIfAbsent(oreKey(ore.displayName()), ore.displayName());
        }

        for (OreCount ore : right) {
            byKey.putIfAbsent(oreKey(ore.displayName()), ore.displayName());
        }

        List<OreCount> merged = new ArrayList<>(byKey.size());
        for (Component name : byKey.values()) {
            merged.add(new OreCount(name, 0));
        }

        return merged;
    }

    private static List<String> mergeMaterialIds(List<String> left, List<String> right) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(left);
        ids.addAll(right);
        return List.copyOf(ids);
    }

    private static String oreKey(Component name) {
        return Component.Serializer.toJson(name);
    }
}
