package su.terrafirmagreg.core.compat.tfc.solar.meteor.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.compat.tfc.solar.meteor.FallingStarNightHelper;
import su.terrafirmagreg.core.compat.tfc.solar.meteor.FallingStarSpawner;
import su.terrafirmagreg.core.compat.tfc.solar.meteor.FallingStarWorldData;
import su.terrafirmagreg.core.config.TFGConfig;

public final class FallingStarDebugCommand {
    private static final int DEFAULT_BURST_COUNT = 8;
    private static final int DEBUG_SHOWER_DURATION = 4800;

    private FallingStarDebugCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> debug) {
        debug.then(literal("falling_stars")
                .then(literal("status").executes(FallingStarDebugCommand::status))
                .then(literal("star").executes(FallingStarDebugCommand::spawnStar))
                .then(literal("pair").executes(FallingStarDebugCommand::spawnPair))
                .then(literal("burst")
                        .executes(context -> spawnBurst(context, DEFAULT_BURST_COUNT))
                        .then(argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> spawnBurst(
                                        context,
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(literal("shower")
                        .then(literal("start").executes(FallingStarDebugCommand::startShower))
                        .then(literal("stop").executes(FallingStarDebugCommand::stopShower))
                        .then(literal("burst")
                                .executes(context -> spawnShowerBurst(context, DEFAULT_BURST_COUNT))
                                .then(argument("count", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> spawnShowerBurst(
                                                context,
                                                IntegerArgumentType.getInteger(context, "count")))))));
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final ServerLevel level = getOverworld(source);
        final FallingStarWorldData data = FallingStarWorldData.get(level);
        final long calendarDay = FallingStarNightHelper.getCalendarDay(level);
        final int nightTick = FallingStarNightHelper.getNightTick(level);
        final int moonPhase = FallingStarNightHelper.getMoonPhase(level);

        source.sendSuccess(() -> Component.literal("[TFG] Falling stars status:"), false);
        source.sendSuccess(() -> Component.literal(
                "  enabled=" + TFGConfig.SERVER.enableFallingStars.get()
                        + " | solarCalendar=" + SolarCalendarBackport.isEnabled()
                        + " | meteorNight=" + FallingStarNightHelper.isMeteorNight(level)
                        + " | raining=" + level.isRaining()),
                false);
        source.sendSuccess(() -> Component.literal(
                "  calendarDay=" + calendarDay
                        + " | nightTick=" + (nightTick < 0 ? "day" : nightTick)
                        + " | moonPhase=" + moonPhase
                        + " | gameTime=" + level.getGameTime()),
                false);
        source.sendSuccess(() -> Component.literal(
                "  showerActive=" + data.isMeteorShowerActive()
                        + " | showerTicks=" + data.getShowerTicksRemaining()
                        + " | nextShowerDay=" + data.getNextShowerDay()
                        + " | radiant=(" + format(data.getShowerRadiantX()) + ", " + format(data.getShowerRadiantZ()) + ")"),
                false);
        source.sendSuccess(() -> Component.literal(
                "  nightlyPending=" + data.getPendingNightlyCount()
                        + " | nextNightlyTick="
                        + (data.getNextNightlySpawnGameTime() < 0 ? "none" : data.getNextNightlySpawnGameTime())),
                false);
        return 1;
    }

    private static int spawnStar(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerPlayer player = getExecutor(context);
        final double[] direction = FallingStarSpawner.pickSporadicDirection(player.serverLevel().getRandom());
        final boolean spawned = FallingStarSpawner.spawnStarFor(player, false, direction[0], direction[1]);
        return reportSpawn(context, spawned ? 1 : 0, "star");
    }

    private static int spawnPair(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerPlayer player = getExecutor(context);
        final double[] direction = FallingStarSpawner.pickSporadicDirection(player.serverLevel().getRandom());
        final int spawned = FallingStarSpawner.spawnTrailingPairFor(player, false, direction[0], direction[1]);
        return reportSpawn(context, spawned, "pair");
    }

    private static int spawnBurst(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        final ServerPlayer player = getExecutor(context);
        final ServerLevel level = player.serverLevel();
        final double[] direction = FallingStarSpawner.pickSporadicDirection(level.getRandom());
        int spawned = 0;
        for (int index = 0; index < count; index++) {
            if (FallingStarSpawner.spawnStarFor(player, false, direction[0], direction[1])) {
                spawned++;
            }
        }
        return reportSpawn(context, spawned, "burst x" + count);
    }

    private static int startShower(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final ServerLevel level = getOverworld(source);
        final FallingStarWorldData data = FallingStarWorldData.get(level);
        final double[] direction = FallingStarSpawner.pickShowerDirection(level.getRandom());
        data.startMeteorShower(DEBUG_SHOWER_DURATION, direction[0], direction[1]);
        spawnShowerBurst(context, DEFAULT_BURST_COUNT);
        source.sendSuccess(() -> Component.literal(
                "[TFG] Meteor shower started for " + DEBUG_SHOWER_DURATION + " ticks."), true);
        return 1;
    }

    private static int stopShower(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final ServerLevel level = getOverworld(source);
        final FallingStarWorldData data = FallingStarWorldData.get(level);
        data.finishMeteorShower(level.getRandom(), FallingStarNightHelper.getCalendarDay(level));
        source.sendSuccess(() -> Component.literal("[TFG] Meteor shower stopped."), true);
        return 1;
    }

    private static int spawnShowerBurst(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        final ServerPlayer player = getExecutor(context);
        final ServerLevel level = getOverworld(context.getSource());
        final FallingStarWorldData data = FallingStarWorldData.get(level);
        final double radiantX = data.isMeteorShowerActive() ? data.getShowerRadiantX() : 0.0;
        final double radiantZ = data.isMeteorShowerActive() ? data.getShowerRadiantZ() : 0.0;
        int spawned = 0;
        for (int index = 0; index < count; index++) {
            if (FallingStarSpawner.spawnStarFor(player, true, radiantX, radiantZ)) {
                spawned++;
            }
        }
        return reportSpawn(context, spawned, "shower burst x" + count);
    }

    private static int reportSpawn(CommandContext<CommandSourceStack> context, int spawned, String label) {
        final CommandSourceStack source = context.getSource();
        if (spawned <= 0) {
            source.sendFailure(Component.literal("[TFG] Failed to spawn falling " + label + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[TFG] Spawned " + spawned + " falling star(s) (" + label + ")."), true);
        return spawned;
    }

    private static ServerPlayer getExecutor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static ServerLevel getOverworld(CommandSourceStack source) {
        return source.getServer().overworld();
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }
}
