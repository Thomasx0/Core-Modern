package su.terrafirmagreg.core.mixins.client.gtceu.map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import xaero.map.gui.dropdown.rightclick.RightClickOption;

@Mixin(value = RightClickOption.class, remap = false)
public interface RightClickOptionAccessor {

    @Accessor("name")
    String tfg$getName();
}
