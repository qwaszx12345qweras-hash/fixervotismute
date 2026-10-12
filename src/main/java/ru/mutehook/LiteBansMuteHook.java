package ru.mutehook;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class LiteBansMuteHook extends JavaPlugin implements Listener {

    private String muteMessage;
    private List<String> blockedCommands;
    private Method isPlayerMutedMethod;
    private Object databaseInstance;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadMessages();

        if (!hookLiteBans()) {
            getLogger().severe("LiteBans не найден или API недоступно! Плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("LiteBansMuteHook включён.");
    }

    private boolean hookLiteBans() {
        try {
            Class<?> dbClass = Class.forName("litebans.api.Database");
            databaseInstance = dbClass.getMethod("get").invoke(null);
            if (databaseInstance == null) return false;

            try {
                isPlayerMutedMethod = dbClass.getMethod("isPlayerMuted", UUID.class, String.class);
            } catch (NoSuchMethodException e) {
                try {
                    isPlayerMutedMethod = dbClass.getMethod("isPlayerMuted", UUID.class);
                } catch (NoSuchMethodException e2) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            getLogger().warning("Не удалось подключиться к LiteBans: " + t.getMessage());
            return false;
        }
    }

    private void reloadMessages() {
        reloadConfig();
        muteMessage = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("mute-message", "&cВы заглушены."));
        blockedCommands = getConfig().getStringList("blocked-commands");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isMuted(player)) {
            event.setCancelled(true);
            player.sendMessage(muteMessage);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isMuted(player)) return;

        String message = event.getMessage();
        if (!message.startsWith("/")) return;

        String cmd = message.substring(1).split(" ")[0].toLowerCase();
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }

        if (blockedCommands.contains(cmd)) {
            event.setCancelled(true);
            player.sendMessage(muteMessage);
        }
    }

    private boolean isMuted(Player player) {
        if (isPlayerMutedMethod == null || databaseInstance == null) return false;
        try {
            Object result;
            if (isPlayerMutedMethod.getParameterCount() == 2) {
                result = isPlayerMutedMethod.invoke(databaseInstance, player.getUniqueId(), null);
            } else {
                result = isPlayerMutedMethod.invoke(databaseInstance, player.getUniqueId());
            }
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }
}
