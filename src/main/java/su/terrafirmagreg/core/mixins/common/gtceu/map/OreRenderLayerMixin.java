package su.terrafirmagreg.core.mixins.common.gtceu.map;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.integration.map.layer.builtin.OreRenderLayer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import su.terrafirmagreg.core.TFGCore;
import su.terrafirmagreg.core.common.map.TFGOreVeinLang;
import su.terrafirmagreg.core.common.map.TFGProspectVeinGenerator;

/**
 * GT map tooltips use {@code gtceu.jei.ore_vein.<path>}, but TFG vein names live in the pack under
 * {@code tfg.ore_vein.<path>}. Prospected clusters use hammer-style ore counts instead.
 */
@Mixin(value = OreRenderLayer.class, remap = false)
public abstract class OreRenderLayerMixin {

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private static void tfg$localizedVeinName(GeneratedVeinMetadata vein, CallbackInfoReturnable<MutableComponent> cir) {
        if (vein == null || vein.id() == null || vein.definition() == null) {
            return;
        }
        if (vein.id().getNamespace().equals(TFGCore.MOD_ID) && vein.id().getPath().startsWith("prospect_")) {
            if (vein.definition().veinGenerator() instanceof TFGProspectVeinGenerator generator) {
                MutableComponent name = TFGOreVeinLang.prospectVeinMapName(generator);
                if (name != null) {
                    cir.setReturnValue(name);
                }
            }
            return;
        }
        if (TFGOreVeinLang.isTfgVein(vein.id())) {
            cir.setReturnValue(TFGOreVeinLang.displayName(vein.id()));
        }
    }

    @Inject(method = "getTooltip", at = @At("HEAD"), cancellable = true)
    private static void tfg$prospectTooltip(String name, GeneratedVeinMetadata vein,
            CallbackInfoReturnable<List<Component>> cir) {
        if (vein == null || vein.definition() == null) {
            return;
        }
        if (vein.definition().veinGenerator() instanceof TFGProspectVeinGenerator generator) {
            List<Component> tooltip = TFGOreVeinLang.prospectVeinTooltip(vein, generator);
            if (!tooltip.isEmpty()) {
                cir.setReturnValue(tooltip);
            }
        }
    }
}
