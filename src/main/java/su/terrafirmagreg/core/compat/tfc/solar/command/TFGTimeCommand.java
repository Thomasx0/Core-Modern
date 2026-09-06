package su.terrafirmagreg.core.compat.tfc.solar.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarActions;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarDebug;

public final class TFGTimeCommand {
    private TFGTimeCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("time")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.literal("dayLength")
                                .then(Commands.literal("vanilla").executes(c -> setDayLength(c, 20)))
                                .then(Commands.literal("default").executes(c -> setDayLength(c, 24)))
                                .then(Commands.literal("disabled").executes(c -> setDayLength(c, -1)))
                                .then(Commands.literal("realtime").executes(c -> setDayLength(c, 24 * 60)))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                        .executes(c -> setDayLength(c, IntegerArgumentType.getInteger(c, "minutes")))))
                        .then(Commands.literal("monthlength")
                                .then(Commands.literal("default")
                                        .executes(c -> setMonthLength(c, net.dries007.tfc.config.TFCConfig.COMMON.defaultMonthLength.get())))
                                .then(Commands.literal("realtime").executes(c -> setMonthLength(c, 30)))
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 1000))
                                        .executes(c -> setMonthLength(c, IntegerArgumentType.getInteger(c, "days")))))
                        .then(Commands.literal("day").executes(c -> setTimeFromDayTime(c, 0.3f)))
                        .then(Commands.literal("noon").executes(c -> setTimeFromDayTime(c, 0.5f)))
                        .then(Commands.literal("night").executes(c -> setTimeFromDayTime(c, 0.8f)))
                        .then(Commands.literal("midnight").executes(c -> setTimeFromDayTime(c, 0f)))
                        .then(Commands.literal("hour")
                                .then(Commands.argument("hour", IntegerArgumentType.integer(0, 24))
                                        .executes(c -> {
                                            int hour = IntegerArgumentType.getInteger(c, "hour");
                                            return setTimeFromDayTime(c, hour == 24 ? 0f : hour / 24f);
                                        }))))
                .then(Commands.literal("add")
                        .then(Commands.literal("years")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(context -> addTime(IntegerArgumentType.getInteger(context, "value")
                                                * Calendars.SERVER.getCalendarTicksInYear()))))
                        .then(Commands.literal("months")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(context -> addTime(IntegerArgumentType.getInteger(context, "value")
                                                * Calendars.SERVER.getCalendarTicksInMonth()))))
                        .then(Commands.literal("days")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(context -> addTime(IntegerArgumentType.getInteger(context, "value")
                                                * (long) ICalendar.TICKS_IN_DAY))))
                        .then(Commands.literal("hours")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(context -> addTime(IntegerArgumentType.getInteger(context, "value")
                                                * (long) ICalendar.TICKS_IN_HOUR))))
                        .then(Commands.literal("ticks")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(context -> addTime(IntegerArgumentType.getInteger(context, "value"))))))
                .then(Commands.literal("query")
                        .then(Commands.literal("daytime")
                                .executes(context -> sendQueryResults(context.getSource(), "tfc.commands.time.query.daytime", Calendars.SERVER.getCalendarDayTime())))
                        .then(Commands.literal("gametime")
                                .executes(context -> sendQueryResults(context.getSource(), "tfc.commands.time.query.game_time", context.getSource().getLevel().getGameTime())))
                        .then(Commands.literal("day")
                                .executes(context -> sendQueryResults(context.getSource(), "tfc.commands.time.query.day", Calendars.SERVER.getTotalDays())))
                        .then(Commands.literal("ticks")
                                .executes(context -> sendQueryResults(context.getSource(), "tfc.commands.time.query.player_ticks", Calendars.SERVER.getTicks())))
                        .then(Commands.literal("calendarticks")
                                .executes(context -> sendQueryResults(context.getSource(), "tfc.commands.time.query.calendar_ticks", Calendars.SERVER.getCalendarTicks()))));
    }

    private static int setDayLength(CommandContext<CommandSourceStack> context, int dayLengthInMinutes) {
        SolarCalendarActions.setCalendarTickRate(dayLengthInMinutes == -1 ? 0f : 20f / dayLengthInMinutes);
        context.getSource().sendSuccess(() -> dayLengthInMinutes != -1
                ? Component.translatable("tfc.commands.time.set_day_length", dayLengthInMinutes)
                : Component.translatable("tfc.commands.time.set_day_length_disabled"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setMonthLength(CommandContext<CommandSourceStack> context, int monthLengthInDays) {
        SolarCalendarActions.setMonthLength(monthLengthInDays);
        context.getSource().sendSuccess(
                () -> Component.translatable("tfc.commands.time.set_month_length", monthLengthInDays),
                true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setTimeFromDayTime(CommandContext<CommandSourceStack> context, float fractionOfDay) {
        final float currentFractionOfDay = SolarCalendarBackport.getCalendarFractionOfDay(context.getSource().getLevel());
        final float targetFraction = fractionOfDay > currentFractionOfDay ? fractionOfDay : 1 + fractionOfDay;
        return addTime(context, (long) ((targetFraction - currentFractionOfDay) * ICalendar.TICKS_IN_DAY));
    }

    private static int addTime(CommandContext<CommandSourceStack> context, long ticksToAdd) {
        if (!SolarCalendarBackport.isEnabled()) {
            SolarCalendarDebug.log("addTime blocked: backport disabled (ticksToAdd={})", ticksToAdd);
            context.getSource().sendFailure(Component.literal("[TFG] Solar calendar backport is disabled in tfg-server.toml"));
            return 0;
        }
        final long before = Calendars.SERVER.getCalendarTicks();
        SolarCalendarDebug.log("addTime: adding {} calendar ticks (before={})", ticksToAdd, before);
        SolarCalendarActions.skipForwardBy(ticksToAdd);
        final long after = Calendars.SERVER.getCalendarTicks();
        SolarCalendarDebug.log("addTime: after={} (delta={})", after, after - before);
        context.getSource().sendSuccess(() -> Component.translatable(
                "tfc.commands.time.add_time",
                ICalendar.getTimeDelta(ticksToAdd, Calendars.SERVER.getCalendarDaysInMonth()),
                ticksToAdd), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int addTime(long ticksToAdd) {
        if (!SolarCalendarBackport.isEnabled()) {
            SolarCalendarDebug.log("addTime (no context) blocked: backport disabled");
            return 0;
        }
        SolarCalendarDebug.log("addTime (no context): adding {} calendar ticks", ticksToAdd);
        SolarCalendarActions.skipForwardBy(ticksToAdd);
        return Command.SINGLE_SUCCESS;
    }

    private static int sendQueryResults(CommandSourceStack source, String translationKey, long value) {
        SolarCalendarDebug.log("query {} = {} (backport enabled={})", translationKey, value, SolarCalendarBackport.isEnabled());
        source.sendSuccess(() -> Component.translatable(translationKey, (int) value), false);
        return Command.SINGLE_SUCCESS;
    }
}
