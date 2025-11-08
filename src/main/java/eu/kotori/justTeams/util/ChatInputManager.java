package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class ChatInputManager
implements Listener {
    private final JustTeams plugin;
    private final Map<UUID, InputData> pendingInput = new ConcurrentHashMap<UUID, InputData>();
    private final Object inputLock = new Object();

    public ChatInputManager(JustTeams plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plugin);
    }

    public void awaitInput(Player player, IRefreshableGUI previousGui, Consumer<String> onInput) {
        this.pendingInput.put(player.getUniqueId(), new InputData(onInput, previousGui));
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        InputData inputData = this.pendingInput.get(player.getUniqueId());
        if (inputData == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        this.pendingInput.remove(player.getUniqueId());
        this.plugin.getTaskRunner().run(() -> {
            inputData.onInput().accept(message);
            if (inputData.previousGui() != null) {
                inputData.previousGui().refresh();
            }
        });
    }

    public void cancelInput(Player player) {
        this.pendingInput.remove(player.getUniqueId());
    }

    public boolean hasPendingInput(Player player) {
        return this.pendingInput.containsKey(player.getUniqueId());
    }

    public void clearAllPendingInput() {
        this.pendingInput.clear();
    }

    private static class InputData {
        private final Consumer<String> onInput;
        private final IRefreshableGUI previousGui;

        public InputData(Consumer<String> onInput, IRefreshableGUI previousGui) {
            this.onInput = onInput;
            this.previousGui = previousGui;
        }

        public Consumer<String> onInput() {
            return this.onInput;
        }

        public IRefreshableGUI previousGui() {
            return this.previousGui;
        }
    }
}

