package su.terrafirmagreg.core.common.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.PacketDistributor;

import su.terrafirmagreg.core.common.data.TFGTags;
import su.terrafirmagreg.core.config.TFGConfig;
import su.terrafirmagreg.core.config.tools.PropickConfig;
import su.terrafirmagreg.core.config.tools.RenderingPropickConfig;
import su.terrafirmagreg.core.network.TFGNetworkHandler;
import su.terrafirmagreg.core.network.packet.OreHighlightPacket;

/** Directional ore scan used by all TFG propick tiers. */
public final class OreProspectorScanner {

    private static final TagKey<Block> ORE_TAG = Tags.Blocks.ORES;

    private OreProspectorScanner() {
    }

    /** TFG propick tier: scan volume, chat/map behaviour, and ServerCache registration. */
    public record ProspectTier(
            TagKey<Item> itemTag,
            ProspectMode mode,
            DoubleSupplier lengthSupplier,
            DoubleSupplier halfWidthSupplier,
            DoubleSupplier halfHeightSupplier,
            BooleanSupplier mapMarkersSupplier,
            BooleanSupplier registerClusterSupplier,
            BooleanSupplier xrayHighlightSupplier,
            boolean advancedTooltip) {

        public double length() {
            return lengthSupplier.getAsDouble();
        }

        public double halfWidth() {
            return halfWidthSupplier.getAsDouble();
        }

        public double halfHeight() {
            return halfHeightSupplier.getAsDouble();
        }

        public boolean mapMarkers() {
            return mapMarkersSupplier.getAsBoolean();
        }

        public boolean registerCluster() {
            return registerClusterSupplier.getAsBoolean();
        }

        public boolean xrayHighlight() {
            return xrayHighlightSupplier.getAsBoolean();
        }

        public static List<ProspectTier> all() {
            return List.of(
                    weak(TFGConfig.SERVER.copperPropickConfig, TFGTags.Items.OreProspectorsCopper),
                    weak(TFGConfig.SERVER.bronzePropickConfig, TFGTags.Items.OreProspectorsBronze),
                    normal(TFGConfig.SERVER.wroughtIronPropickConfig, TFGTags.Items.OreProspectorsWroughtIron),
                    normal(TFGConfig.SERVER.steelPropickConfig, TFGTags.Items.OreProspectorsSteel),
                    normal(TFGConfig.SERVER.blackSteelPropickConfig, TFGTags.Items.OreProspectorsBlackSteel),
                    advanced(TFGConfig.SERVER.blueSteelPropickConfig, TFGTags.Items.OreProspectorsBlueSteel),
                    advanced(TFGConfig.SERVER.redSteelPropickConfig, TFGTags.Items.OreProspectorsRedSteel));
        }

        @Nullable
        public static ProspectTier forStack(ItemStack stack) {
            for (ProspectTier tier : all()) {
                if (stack.is(tier.itemTag())) {
                    return tier;
                }
            }
            return null;
        }

        public static boolean isProspector(ItemStack stack) {
            return forStack(stack) != null;
        }

        private static ProspectTier weak(PropickConfig config, TagKey<Item> tag) {
            return new ProspectTier(
                    tag,
                    ProspectMode.NAMES_ONLY,
                    config.searchLength()::get,
                    config.searchWidth()::get,
                    config.searchWidth()::get,
                    () -> true,
                    () -> false,
                    () -> false,
                    false);
        }

        private static ProspectTier normal(PropickConfig config, TagKey<Item> tag) {
            return new ProspectTier(
                    tag,
                    ProspectMode.WITH_COUNTS,
                    config.searchLength()::get,
                    config.searchWidth()::get,
                    config.searchWidth()::get,
                    () -> true,
                    () -> true,
                    () -> false,
                    false);
        }

        private static ProspectTier advanced(RenderingPropickConfig config, TagKey<Item> tag) {
            return new ProspectTier(
                    tag,
                    ProspectMode.WITH_COUNTS,
                    config.inner().searchLength()::get,
                    config.inner().searchWidth()::get,
                    config.inner().searchWidth()::get,
                    config.preciselyRenderVein()::get,
                    config.preciselyRenderVein()::get,
                    () -> !config.preciselyRenderVein().get(),
                    true);
        }
    }

    public record ScanResult(
            Map<Component, Integer> oreCounts,
            List<BlockPos> orePositions,
            @Nullable BlockPos scanMin,
            @Nullable BlockPos scanMax) {

        public boolean isEmpty() {
            return oreCounts.isEmpty();
        }

        public int totalCount() {
            return oreCounts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public static ScanResult scan(Level level, Player player, double length, double halfWidth, double halfHeight) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle().normalize();

        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = lookDir.cross(up).normalize();
        if (right.lengthSqr() == 0) {
            right = new Vec3(1, 0, 0);
        }
        up = right.cross(lookDir).normalize();

        Set<BlockPos> checkedPositions = new HashSet<>();
        Map<Component, Integer> oreCounts = new HashMap<>();
        List<BlockPos> orePositions = new ArrayList<>();
        int scanMinX = Integer.MAX_VALUE, scanMinY = Integer.MAX_VALUE, scanMinZ = Integer.MAX_VALUE;
        int scanMaxX = Integer.MIN_VALUE, scanMaxY = Integer.MIN_VALUE, scanMaxZ = Integer.MIN_VALUE;

        int stepsX = (int) Math.ceil(halfWidth) * 2;
        int stepsY = (int) Math.ceil(halfHeight) * 2;
        int stepsZ = (int) Math.ceil(length);

        for (int ix = -stepsX / 2; ix <= stepsX / 2; ix++) {
            for (int iy = -stepsY / 2; iy <= stepsY / 2; iy++) {
                for (int iz = 0; iz <= stepsZ; iz++) {
                    Vec3 localPos = right.scale(ix + 0.5)
                            .add(up.scale(iy + 0.5))
                            .add(lookDir.scale(iz + 0.5));
                    Vec3 worldPos = eyePos.add(localPos);

                    BlockPos pos = BlockPos.containing(worldPos);
                    if (!checkedPositions.add(pos)) {
                        continue;
                    }

                    scanMinX = Math.min(scanMinX, pos.getX());
                    scanMinY = Math.min(scanMinY, pos.getY());
                    scanMinZ = Math.min(scanMinZ, pos.getZ());
                    scanMaxX = Math.max(scanMaxX, pos.getX());
                    scanMaxY = Math.max(scanMaxY, pos.getY());
                    scanMaxZ = Math.max(scanMaxZ, pos.getZ());

                    Block block = level.getBlockState(pos).getBlock();
                    if (block.defaultBlockState().is(ORE_TAG)) {
                        Component name = block.getName();
                        oreCounts.merge(name, 1, Integer::sum);
                        orePositions.add(pos);
                    }
                }
            }
        }

        BlockPos scanMin = checkedPositions.isEmpty() ? null : new BlockPos(scanMinX, scanMinY, scanMinZ);
        BlockPos scanMax = checkedPositions.isEmpty() ? null : new BlockPos(scanMaxX, scanMaxY, scanMaxZ);
        return new ScanResult(oreCounts, orePositions, scanMin, scanMax);
    }

    public static void finish(ServerPlayer player, InteractionHand hand, ItemStack held, ProspectTier tier,
            ScanResult result) {
        ServerLevel level = player.serverLevel();
        sendChat(player, tier, result);

        player.swing(hand, true);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_HIT_GROUND,
                net.minecraft.sounds.SoundSource.PLAYERS, 2.0f, 0.1f);
        held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        player.getCooldowns().addCooldown(held.getItem(), 40);

        if (result.isEmpty()) {
            return;
        }

        int scanRange = (int) Math.ceil(tier.length());
        if (tier.mapMarkers()) {
            TFGProspectMapSync.syncOrePositions(player, level, result.orePositions(), tier.mode(), scanRange,
                    result.scanMin(), result.scanMax());
            if (tier.registerCluster()) {
                TFGVeinMetadataHelper.registerCluster(level, player, result.orePositions());
            }
        } else if (tier.xrayHighlight()) {
            TFGNetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new OreHighlightPacket(result.orePositions()));
        }
    }

    private static void sendChat(Player player, ProspectTier tier, ScanResult result) {
        if (result.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("tfg.toast.ore_prospector_none").withStyle(ChatFormatting.GRAY));
            return;
        }

        Object total = tier.mode() == ProspectMode.NAMES_ONLY ? "??" : result.totalCount();
        player.sendSystemMessage(
                Component.translatable("tfg.toast.ore_prospector_message", tier.length(), total)
                        .withStyle(ChatFormatting.GOLD));

        result.oreCounts().forEach((name, count) -> {
            MutableComponent line = Component.literal("- ").append(name).withStyle(ChatFormatting.AQUA);
            if (tier.mode() == ProspectMode.WITH_COUNTS) {
                line.append(Component.literal(": " + count));
            }
            player.sendSystemMessage(line);
        });
    }
}
