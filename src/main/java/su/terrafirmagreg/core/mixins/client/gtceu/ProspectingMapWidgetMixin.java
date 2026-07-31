package su.terrafirmagreg.core.mixins.client.gtceu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gregtechceu.gtceu.api.gui.widget.ProspectingMapWidget;

/**
 * GT places a Xaero custom waypoint (minimap letter) on map click. TFG uses the {@code ore_veins} overlay instead.
 */
@Mixin(value = ProspectingMapWidget.class, remap = false)
public abstract class ProspectingMapWidgetMixin {

    @Inject(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/integration/map/WaypointManager;setWaypoint(Ljava/lang/String;Ljava/lang/String;ILnet/minecraft/resources/ResourceKey;III)V", remap = false), cancellable = true)
    private void tfg$skipProspectorWaypoint(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
        cir.cancel();
    }
}
