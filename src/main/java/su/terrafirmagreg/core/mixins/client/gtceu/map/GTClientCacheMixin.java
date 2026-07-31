package su.terrafirmagreg.core.mixins.client.gtceu.map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.integration.map.GenericMapRenderer;
import com.gregtechceu.gtceu.integration.map.cache.client.GTClientCache;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import su.terrafirmagreg.core.common.map.TFGOreVeinLang;

@Mixin(value = GTClientCache.class, remap = false)
public abstract class GTClientCacheMixin {

    /**
     * TFG vein map markers are driven by {@link su.terrafirmagreg.core.network.packet.OreHighlightVeinPacket}
     * ({@link su.terrafirmagreg.core.common.map.TFGProspectMapSync}) so tooltips always show scanned block counts.
     * Keep {@link GTClientCache} storage for the GT prospector GUI / chat notifications.
     */
    @Redirect(method = { "addVein",
            "readDimFile" }, at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/integration/map/GenericMapRenderer;addMarker(Ljava/lang/String;Lnet/minecraft/resources/ResourceKey;Lcom/gregtechceu/gtceu/api/data/worldgen/ores/GeneratedVeinMetadata;Ljava/lang/String;)Z"))
    private boolean tfg$skipTfgVeinMapMarker(GenericMapRenderer renderer, String name, ResourceKey<Level> dim,
            GeneratedVeinMetadata vein, String id) {
        ResourceLocation veinId = vein.id();
        if (TFGOreVeinLang.isTfgVein(veinId) || TFGOreVeinLang.isProspectVein(veinId)) {
            return false;
        }
        return renderer.addMarker(name, dim, vein, id);
    }

    @Redirect(method = "notifyNewVeins", at = @At(value = "INVOKE", target = "Ljava/lang/String;replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"))
    private String tfg$veinLangKey(String veinId, CharSequence target, CharSequence replacement) {
        ResourceLocation id = ResourceLocation.tryParse(veinId);
        if (id == null && veinId.contains(":")) {
            int sep = veinId.indexOf(':');
            id = new ResourceLocation(veinId.substring(0, sep), veinId.substring(sep + 1));
        }
        if (id != null && TFGOreVeinLang.isTfgVein(id)) {
            return TFGOreVeinLang.KEY_PREFIX + id.getPath();
        }
        return veinId.replace(target, replacement);
    }
}
