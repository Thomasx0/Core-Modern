package su.terrafirmagreg.core.common.map;

import java.util.ArrayList;
import java.util.List;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import su.terrafirmagreg.core.TFGCore;

public final class TFGOreVeinLang {

    public static final String KEY_PREFIX = "tfg.ore_vein.";
    public static final String PROSPECT_MESSAGE_KEY = "tfg.toast.ore_prospector_message";

    private TFGOreVeinLang() {
    }

    public static MutableComponent prospectMessageTitle(int scanRange, int totalBlocks) {
        return Component.translatable(PROSPECT_MESSAGE_KEY, scanRange, totalBlocks);
    }

    /** Bare-hand ore block click — single block, no scan radius or block counts. */
    public static boolean isHandProspect(int scanRange) {
        return scanRange == 0;
    }

    /** Weak propicks and bare-hand marks list ore names without block counts. */
    public static boolean isNamesOnlyProspect(int scanRange, Iterable<TFGProspectVeinMarker.OreCount> ores) {
        if (isHandProspect(scanRange)) {
            return true;
        }
        for (TFGProspectVeinMarker.OreCount ore : ores) {
            if (ore.count() > 0) {
                return false;
            }
        }
        return true;
    }

    public static MutableComponent joinOreNames(Iterable<TFGProspectVeinMarker.OreCount> ores) {
        MutableComponent result = null;
        for (TFGProspectVeinMarker.OreCount ore : ores) {
            if (result == null) {
                result = ore.displayName().copy();
            } else {
                result.append(", ").append(ore.displayName());
            }
        }
        return result != null ? result : Component.empty();
    }

    public static MutableComponent prospectMarkerTitle(int scanRange, Iterable<TFGProspectVeinMarker.OreCount> ores) {
        if (isNamesOnlyProspect(scanRange, ores)) {
            return joinOreNames(ores);
        }
        return prospectMessageTitle(scanRange, totalBlocks(ores));
    }

    public static int totalBlocks(Iterable<TFGProspectVeinMarker.OreCount> ores) {
        int total = 0;
        for (TFGProspectVeinMarker.OreCount ore : ores) {
            total += ore.count();
        }
        return total;
    }

    public static boolean isProspectVein(ResourceLocation id) {
        return id != null && TFGCore.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith("prospect_");
    }

    public static boolean isTfgVein(ResourceLocation id) {
        return id != null && TFGCore.MOD_ID.equals(id.getNamespace()) && !id.getPath().startsWith("prospect_");
    }

    public static MutableComponent displayName(ResourceLocation veinId) {
        return Component.translatable(KEY_PREFIX + veinId.getPath());
    }

    public static MutableComponent prospectVeinMapName(TFGProspectVeinGenerator generator) {
        if (generator.hasOreCounts()) {
            return prospectMessageTitle(generator.scanRange(), generator.totalBlocks());
        }
        if (!generator.getOreCounts().isEmpty()) {
            return joinOreNames(generator.getOreCounts());
        }
        return null;
    }

    public static List<Component> prospectVeinTooltip(GeneratedVeinMetadata vein, TFGProspectVeinGenerator generator) {
        if (generator.hasOreCounts()) {
            return buildCountedProspectTooltip(vein, generator);
        }
        if (!generator.getOreCounts().isEmpty()) {
            return buildNamesOnlyProspectTooltip(generator);
        }
        return List.of();
    }

    private static List<Component> buildCountedProspectTooltip(GeneratedVeinMetadata vein,
            TFGProspectVeinGenerator generator) {
        List<Component> tooltip = new ArrayList<>();
        MutableComponent title = prospectMessageTitle(generator.scanRange(), generator.totalBlocks());
        if (vein.depleted()) {
            title.append(" (").append(Component.translatable("gtceu.minimap.ore_vein.depleted")).append(")");
        }
        tooltip.add(title);
        for (TFGProspectVeinMarker.OreCount ore : generator.getOreCounts()) {
            tooltip.add(Component.literal("- ")
                    .append(ore.displayName())
                    .append(Component.literal(": " + ore.count()))
                    .withStyle(ChatFormatting.AQUA));
        }
        return tooltip;
    }

    private static List<Component> buildNamesOnlyProspectTooltip(TFGProspectVeinGenerator generator) {
        List<Component> tooltip = new ArrayList<>();
        if (!generator.isHandProspect()) {
            tooltip.add(prospectMessageTitle(generator.scanRange(), 0));
        }
        for (TFGProspectVeinMarker.OreCount ore : generator.getOreCounts()) {
            tooltip.add(Component.literal("- ")
                    .append(ore.displayName())
                    .withStyle(ChatFormatting.AQUA));
        }
        return tooltip;
    }
}
