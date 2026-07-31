package su.terrafirmagreg.core.network.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import su.terrafirmagreg.core.client.map.TFGProspectMapLayer;
import su.terrafirmagreg.core.common.map.TFGProspectVeinMarker;
import su.terrafirmagreg.core.common.map.TFGProspectVeinMarker.OreCount;

public record OreHighlightVeinPacket(
        List<TFGProspectVeinMarker> markers,
        @Nullable BlockPos scanMin,
        @Nullable BlockPos scanMax,
        boolean showParticles) {

    public OreHighlightVeinPacket(List<TFGProspectVeinMarker> markers) {
        this(markers, null, null, true);
    }

    public OreHighlightVeinPacket(List<TFGProspectVeinMarker> markers, @Nullable BlockPos scanMin,
            @Nullable BlockPos scanMax) {
        this(markers, scanMin, scanMax, true);
    }

    public static void encode(@NotNull OreHighlightVeinPacket pkt, @NotNull FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.markers.size());
        for (TFGProspectVeinMarker marker : pkt.markers) {
            buf.writeBlockPos(marker.center());
            buf.writeVarInt(marker.ores().size());
            for (OreCount ore : marker.ores()) {
                buf.writeComponent(ore.displayName());
                buf.writeVarInt(ore.count());
            }
            buf.writeVarInt(marker.materialIds().size());
            for (String id : marker.materialIds()) {
                buf.writeUtf(id);
            }
            buf.writeVarInt(marker.scanRange());
        }
        boolean hasBounds = pkt.scanMin != null && pkt.scanMax != null;
        buf.writeBoolean(hasBounds);
        if (hasBounds) {
            buf.writeBlockPos(pkt.scanMin);
            buf.writeBlockPos(pkt.scanMax);
        }
        buf.writeBoolean(pkt.showParticles);
    }

    @Contract("_ -> new")
    public static @NotNull OreHighlightVeinPacket decode(@NotNull FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<TFGProspectVeinMarker> markers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            BlockPos center = buf.readBlockPos();
            int oreCount = buf.readVarInt();
            List<OreCount> ores = new ArrayList<>(oreCount);
            for (int j = 0; j < oreCount; j++) {
                Component name = buf.readComponent();
                int count = buf.readVarInt();
                ores.add(new OreCount(name, count));
            }
            int matCount = buf.readVarInt();
            List<String> materialIds = new ArrayList<>(matCount);
            for (int j = 0; j < matCount; j++) {
                materialIds.add(buf.readUtf());
            }
            int scanRange = buf.readVarInt();
            markers.add(new TFGProspectVeinMarker(center, ores, materialIds, scanRange));
        }
        BlockPos scanMin = null;
        BlockPos scanMax = null;
        if (buf.readBoolean()) {
            scanMin = buf.readBlockPos();
            scanMax = buf.readBlockPos();
        }
        boolean showParticles = buf.readBoolean();
        return new OreHighlightVeinPacket(markers, scanMin, scanMax, showParticles);
    }

    public static void handle(OreHighlightVeinPacket pkt, @NotNull Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> TFGProspectMapLayer.handlePacket(pkt));
        ctx.get().setPacketHandled(true);
    }
}
