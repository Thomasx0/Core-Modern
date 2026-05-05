package su.terrafirmagreg.core.common.event;

import org.jetbrains.annotations.NotNull;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import su.terrafirmagreg.core.TFGCore;
import su.terrafirmagreg.core.config.TFGConfig;
import su.terrafirmagreg.core.utils.SnowCorrectionQueue;

@Mod.EventBusSubscriber(modid = TFGCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SnowCorrectionQueueEvents {

    @SubscribeEvent
    public static void onServerStarting(@NotNull ServerStartingEvent event) {
        SnowCorrectionQueue.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(@NotNull ServerStoppingEvent event) {
        SnowCorrectionQueue.clear();
    }

    @SubscribeEvent
    public static void onServerTick(@NotNull TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!TFGConfig.SERVER.enableSnowCorrection.get() || !TFGConfig.SERVER.snowCorrectionQueue.get()) {
            return;
        }
        SnowCorrectionQueue.processTick(event.getServer().overworld());
    }
}
