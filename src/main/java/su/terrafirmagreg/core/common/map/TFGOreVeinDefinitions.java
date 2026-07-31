package su.terrafirmagreg.core.common.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.BiomeWeightModifier;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.IndicatorGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.generator.indicators.SurfaceIndicatorGenerator;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;
import com.gregtechceu.gtceu.integration.map.cache.server.ServerCache;
import com.mojang.datafixers.util.Either;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import su.terrafirmagreg.core.TFGCore;
import su.terrafirmagreg.core.compat.emi.ExportedOreVeinInfo;
import su.terrafirmagreg.core.compat.emi.OreVeinInfoRecipe;

/**
 * Registers {@link GTOreDefinition} entries for every TFG ore vein so GregTech's {@code ServerCache} and prospecting
 * packets can reference them by id ({@code tfg:deep_galena}, etc.).
 */
public final class TFGOreVeinDefinitions {

    private static final Map<ResourceLocation, GTOreDefinition> DEFINITIONS = new HashMap<>();
    private static final Map<ResourceLocation, Set<Material>> VEIN_MATERIALS = new HashMap<>();

    private TFGOreVeinDefinitions() {
    }

    /** Early mod-load registration (may be cleared later by KubeJS {@code event.removeAll()}). */
    public static void register(com.gregtechceu.gtceu.api.GTCEuAPI.RegisterEvent<ResourceLocation, GTOreDefinition> event) {
        installAllDefinitions((id, definition) -> event.register(id, definition));
        TFGCore.LOGGER.info("Registered {} TFG ore vein definitions for GregTech map integration", DEFINITIONS.size());
    }

    /**
     * Re-register after KubeJS clears {@link GTRegistries#ORE_VEINS} in {@code GTCEuServerEvents.oreVeins}.
     * Uses {@code registerOrOverride} so entries are canonical for packets and NBT.
     */
    public static void reregisterAfterKubeJsClear() {
        if (GTRegistries.ORE_VEINS.isFrozen()) {
            TFGCore.LOGGER.warn("[TFG veins] Skipping ore vein re-registration — registry is frozen");
            return;
        }
        installAllDefinitions(GTRegistries.ORE_VEINS::registerOrOverride);
        ServerCache.instance.oreVeinDefinitionsChanged(GTRegistries.ORE_VEINS.registry());
        syncClientOreVeins();
        TFGCore.LOGGER.info("[TFG veins] Re-registered {} ore vein definitions after KubeJS clear", DEFINITIONS.size());
    }

    /** Keep {@link com.gregtechceu.gtceu.client.ClientProxy#CLIENT_ORE_VEINS} in sync with the live registry. */
    public static void syncClientOreVeins() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        for (ResourceLocation id : DEFINITIONS.keySet()) {
            GTOreDefinition definition = GTRegistries.ORE_VEINS.get(id);
            if (definition != null) {
                com.gregtechceu.gtceu.client.ClientProxy.CLIENT_ORE_VEINS.put(id, definition);
            }
        }
        com.gregtechceu.gtceu.integration.map.cache.client.GTClientCache.instance
                .oreVeinDefinitionsChanged(com.gregtechceu.gtceu.client.ClientProxy.CLIENT_ORE_VEINS);
    }

    public static Map<ResourceLocation, GTOreDefinition> getDefinitions() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }

    public static GTOreDefinition get(ResourceLocation id) {
        GTOreDefinition fromRegistry = GTRegistries.ORE_VEINS.get(id);
        if (fromRegistry != null) {
            return fromRegistry;
        }
        return DEFINITIONS.get(id);
    }

    public static Map<ResourceLocation, Set<Material>> veinMaterials() {
        return Collections.unmodifiableMap(VEIN_MATERIALS);
    }

    public static ResourceLocation matchVein(List<Material> foundMaterials) {
        if (foundMaterials.isEmpty()) {
            return null;
        }
        Set<Material> found = new HashSet<>(foundMaterials);

        ResourceLocation bestId = null;
        int bestScore = 0;
        for (var entry : VEIN_MATERIALS.entrySet()) {
            int score = 0;
            for (Material material : found) {
                if (entry.getValue().contains(material)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestId = entry.getKey();
            }
        }
        return bestScore > 0 ? bestId : null;
    }

    private interface DefinitionInstaller {
        void install(ResourceLocation id, GTOreDefinition definition);
    }

    private static void installAllDefinitions(DefinitionInstaller installer) {
        for (OreVeinInfoRecipe recipe : ExportedOreVeinInfo.RECIPES) {
            ResourceLocation id = TFGCore.id(recipe.getVeinName());
            List<Material> materials = resolveMaterials(recipe.getOres());
            if (materials.isEmpty()) {
                TFGCore.LOGGER.warn("Skipping TFG ore vein {} — no resolvable GT materials", id);
                continue;
            }

            GTOreDefinition definition = createDefinition(recipe, materials);
            installer.install(id, definition);
            DEFINITIONS.put(id, GTRegistries.ORE_VEINS.get(id));
            VEIN_MATERIALS.put(id, Set.copyOf(materials));
        }
    }

    private static GTOreDefinition createDefinition(OreVeinInfoRecipe recipe, List<Material> materials) {
        return new GTOreDefinition(
                ConstantInt.of(recipe.getSize() > 0 ? recipe.getSize() : 16),
                (float) recipe.getDensity(),
                Math.max(1, recipe.getRarity()),
                null,
                new HashSet<>(),
                heightRangeFor(recipe),
                0f,
                null,
                BiomeWeightModifier.EMPTY,
                new TFGProspectVeinGenerator(materials),
                indicatorGeneratorsFor(recipe, materials));
    }

    /**
     * GT {@link com.gregtechceu.gtceu.integration.map.cache.server.ServerCache#prospectBySurfaceRockMaterial} matches
     * vein indicators against {@code gtceu:<material>_indicator} piles ({@link SurfaceIndicatorGenerator}).
     * One generator per vein ore material that has a registered GT surface rock block.
     */
    private static List<IndicatorGenerator> indicatorGeneratorsFor(OreVeinInfoRecipe recipe, List<Material> materials) {
        if (recipe.getIndicatorDepth() <= 1) {
            return List.of();
        }
        List<IndicatorGenerator> generators = new ArrayList<>();
        Set<Material> seen = new HashSet<>();
        for (Material material : materials) {
            if (material == null || material.isNull() || !seen.add(material)) {
                continue;
            }
            if (!GTMaterialBlocks.SURFACE_ROCK_BLOCKS.containsKey(material)) {
                continue;
            }
            generators.add(new SurfaceIndicatorGenerator(
                    Either.right(material),
                    ConstantInt.of(Math.max(1, recipe.getSize() > 0 ? recipe.getSize() / 4 : 5)),
                    ConstantFloat.of(0.2f),
                    SurfaceIndicatorGenerator.IndicatorPlacement.ABOVE));
        }
        return generators;
    }

    private static HeightRangePlacement heightRangeFor(OreVeinInfoRecipe recipe) {
        int min = recipe.getMinY();
        int max = recipe.getMaxY();
        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }
        if (min == max) {
            min -= 8;
            max += 8;
        }
        return HeightRangePlacement.uniform(VerticalAnchor.absolute(min), VerticalAnchor.absolute(max));
    }

    private static List<Material> resolveMaterials(OreVeinInfoRecipe.WeightedBlock[] ores) {
        List<Material> materials = new ArrayList<>(ores.length);
        for (OreVeinInfoRecipe.WeightedBlock ore : ores) {
            Material material = GTCEuAPI.materialManager.getMaterial("gtceu:" + ore.ore());
            if (material != null && !material.isNull()) {
                materials.add(material);
            }
        }
        return materials;
    }
}
