package su.terrafirmagreg.core.common.event;

import org.jetbrains.annotations.NotNull;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import su.terrafirmagreg.core.TFGCore;
import su.terrafirmagreg.core.common.map.OreProspectorScanner;
import su.terrafirmagreg.core.common.map.OreProspectorScanner.ProspectTier;

@Mod.EventBusSubscriber(modid = TFGCore.MOD_ID)
public class OreProspectorEvent {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.@NotNull RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        ItemStack held = player.getItemInHand(event.getHand());

        if (level.isClientSide()) {
            return;
        }

        if (held.isEmpty() || !ProspectTier.isProspector(held)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        boolean isContainer = blockEntity instanceof MenuProvider;
        boolean isMachine = blockEntity instanceof IMachineBlockEntity;
        boolean hasEntityTarget = event.getEntity() != null
                && player.pick(5.0D, 0.0F, false) instanceof EntityHitResult;
        boolean shouldCancel = (!hasEntityTarget && !isMachine && !isContainer) || player.isCrouching();

        if (shouldCancel && dispatchProspectorUse(event, held)) {
            event.setCanceled(true);
        }
    }

    private static boolean dispatchProspectorUse(PlayerInteractEvent.RightClickBlock event, ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        ProspectTier tier = ProspectTier.forStack(held);
        if (tier == null) {
            return false;
        }

        Player player = event.getEntity();
        if (player.getCooldowns().isOnCooldown(held.getItem())) {
            return false;
        }

        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }

        OreProspectorScanner.ScanResult result = OreProspectorScanner.scan(
                player.level(),
                player,
                tier.length(),
                tier.halfWidth(),
                tier.halfHeight());
        OreProspectorScanner.finish(sp, event.getHand(), held, tier, result);
        return true;
    }
}
