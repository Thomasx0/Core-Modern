package su.terrafirmagreg.core.compat.tfc.solar;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import su.terrafirmagreg.core.TFGCore;
import su.terrafirmagreg.core.compat.tfc.solar.command.TFGTimeCommand;

@Mod.EventBusSubscriber(modid = TFGCore.MOD_ID)
public final class SolarCalendarCommandRegistration {
    private SolarCalendarCommandRegistration() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerCommands(RegisterCommandsEvent event) {
        final boolean enabled = SolarCalendarBackport.isEnabled();
        SolarCalendarDebug.log("RegisterCommandsEvent: backport enabled={}", enabled);
        if (!enabled) {
            return;
        }

        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        final boolean removed = removeRootLiteral(dispatcher, "time");
        SolarCalendarDebug.log("Removed existing /time root node: {}", removed);
        dispatcher.register(TFGTimeCommand.create());
        logTimeCommandTree(dispatcher);
    }

    private static void logTimeCommandTree(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.getRoot().getChildren().stream()
                .filter(node -> node.getName().equals("time"))
                .findFirst()
                .ifPresentOrElse(
                        timeNode -> {
                            final String addChildren = timeNode.getChildren().stream()
                                    .filter(node -> node.getName().equals("add"))
                                    .findFirst()
                                    .map(addNode -> addNode.getChildren().stream()
                                            .map(CommandNode::getName)
                                            .collect(Collectors.joining(", ")))
                                    .orElse("<missing>");
                            SolarCalendarDebug.log(
                                    "Registered TFG /time tree. add subcommands: [{}]. query present: {}",
                                    addChildren,
                                    timeNode.getChildren().stream().anyMatch(node -> node.getName().equals("query")));
                        },
                        () -> SolarCalendarDebug.log("WARNING: /time node missing after registration"));
    }

    @SuppressWarnings("unchecked")
    private static boolean removeRootLiteral(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        final CommandNode<CommandSourceStack> root = dispatcher.getRoot();
        try {
            final Field childrenField = CommandNode.class.getDeclaredField("children");
            childrenField.setAccessible(true);
            final Map<String, CommandNode<CommandSourceStack>> children = (Map<String, CommandNode<CommandSourceStack>>) childrenField.get(root);
            final boolean removedChild = children.remove(name) != null;

            final Field literalsField = CommandNode.class.getDeclaredField("literals");
            literalsField.setAccessible(true);
            final Map<String, LiteralCommandNode<CommandSourceStack>> literals = (Map<String, LiteralCommandNode<CommandSourceStack>>) literalsField.get(root);
            final boolean removedLiteral = literals.remove(name) != null;
            return removedChild || removedLiteral;
        } catch (ReflectiveOperationException error) {
            SolarCalendarDebug.log("Reflection removal failed for /{}, falling back to removeIf: {}", name, error.toString());
            return root.getChildren().removeIf(node -> node.getName().equals(name));
        }
    }
}
