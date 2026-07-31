package su.terrafirmagreg.core.common.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreBlockPlacer;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Display-only vein generator: carries multiple {@link Material}s for map tooltips and icons.
 * Does not participate in worldgen.
 */
public final class TFGProspectVeinGenerator extends VeinGenerator {

    public static final Codec<TFGProspectVeinGenerator> CODEC = Codec.unit(TFGProspectVeinGenerator::empty);

    private final List<Material> materials;
    private final List<TFGProspectVeinMarker.OreCount> oreCounts;
    private final int scanRange;

    public TFGProspectVeinGenerator(List<Material> materials) {
        this(materials, null, 0);
    }

    public TFGProspectVeinGenerator(List<Material> materials, @Nullable List<TFGProspectVeinMarker.OreCount> oreCounts) {
        this(materials, oreCounts, 0);
    }

    public TFGProspectVeinGenerator(List<Material> materials, @Nullable List<TFGProspectVeinMarker.OreCount> oreCounts,
            int scanRange) {
        this.materials = List.copyOf(materials);
        this.oreCounts = oreCounts == null ? List.of() : List.copyOf(oreCounts);
        this.scanRange = scanRange;
    }

    private TFGProspectVeinGenerator() {
        this.materials = List.of();
        this.oreCounts = List.of();
        this.scanRange = 0;
    }

    public int scanRange() {
        return scanRange;
    }

    public int totalBlocks() {
        return TFGOreVeinLang.totalBlocks(oreCounts);
    }

    public List<TFGProspectVeinMarker.OreCount> getOreCounts() {
        return oreCounts;
    }

    public boolean hasOreCounts() {
        return scanRange > 0 && oreCounts.stream().anyMatch(ore -> ore.count() > 0);
    }

    public boolean isHandProspect() {
        return scanRange == 0;
    }

    private static TFGProspectVeinGenerator empty() {
        return new TFGProspectVeinGenerator();
    }

    @Override
    public List<VeinEntry> getAllEntries() {
        if (materials.isEmpty()) {
            return List.of();
        }
        List<VeinEntry> entries = new ArrayList<>(materials.size());
        int weight = materials.size() * 10;
        for (Material material : materials) {
            if (material != null && !material.isNull()) {
                entries.add(VeinEntry.ofMaterial(material, weight));
                weight = Math.max(1, weight - 10);
            }
        }
        return entries;
    }

    @Override
    public Map<BlockPos, OreBlockPlacer> generate(WorldGenLevel level, RandomSource random, GTOreDefinition entry,
            BlockPos origin) {
        return Collections.emptyMap();
    }

    @Override
    public VeinGenerator build() {
        return this;
    }

    @Override
    public VeinGenerator copy() {
        return new TFGProspectVeinGenerator(materials, oreCounts, scanRange);
    }

    @Override
    public Codec<? extends VeinGenerator> codec() {
        return CODEC;
    }
}
