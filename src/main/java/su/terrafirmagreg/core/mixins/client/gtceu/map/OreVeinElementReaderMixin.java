package su.terrafirmagreg.core.mixins.client.gtceu.map;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregtechceu.gtceu.integration.map.xaeros.worldmap.ore.OreVeinElement;
import com.gregtechceu.gtceu.integration.map.xaeros.worldmap.ore.OreVeinElementReader;

import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/**
 * TFG ore markers use the {@code ore_veins} overlay only — hide the dead Xaero waypoint toggle entry.
 */
@Mixin(value = OreVeinElementReader.class, remap = false)
public abstract class OreVeinElementReaderMixin {

    private static final String WAYPOINT_OPTION = "button.gtceu.toggle_waypoint.name";

    @Inject(method = "getRightClickOptions", at = @At("RETURN"))
    private void tfg$removeWaypointMenuOption(OreVeinElement element, IRightClickableElement target,
            CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        cir.getReturnValue().removeIf(option -> WAYPOINT_OPTION.equals(((RightClickOptionAccessor) option).tfg$getName()));
    }
}
