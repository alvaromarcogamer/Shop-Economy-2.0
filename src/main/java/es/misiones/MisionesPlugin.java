package es.misiones;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class MisionesPlugin extends JavaPlugin implements TabExecutor {

    static final String PREFIX = ChatColor.LIGHT_PURPLE + "[Misiones] " + ChatColor.RESET;

    private QuestManager manager;
    private QuestGui gui;
    private MoneyService money;

    static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Devuelve el acceso a la economía de Vault, o null si no hay Vault o ningún plugin de economía. */
    MoneyService money() {
        if (money == null && getServer().getPluginManager().getPlugin("Vault") != null) {
            money = MoneyService.create(this);
        }
        return money;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();

        manager = new QuestManager(this);
        manager.init();
        gui = new QuestGui(this, manager);

        getServer().getPluginManager().registerEvents(new QuestListener(manager), this);
        getServer().getPluginManager().registerEvents(gui, this);

        getCommand("misiones").setExecutor(this);
        getCommand("misiones").setTabCompleter(this);

        // Guardado automático cada 5 minutos
        getServer().getScheduler().runTaskTimer(this, manager::saveIfDirty, 6000L, 6000L);

        // Cuando ya han cargado todos los plugins, comprobar que hay economía
        getServer().getScheduler().runTask(this, () -> {
            if (money() == null) {
                getLogger().warning("No se encontró una economía de Vault: reward-money no pagará dinero. "
                        + "Instala Vault y un plugin de economía (por ejemplo ShopEconomy).");
            } else {
                getLogger().info("Economía de Vault conectada.");
            }
        });
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.saveIfDirty();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Solo jugadores.");
                return true;
            }
            if (!p.hasPermission("misiones.use")) {
                p.sendMessage(PREFIX + ChatColor.RED + "No tienes permiso.");
                return true;
            }
            gui.open(p);
            return true;
        }

        if (!sender.hasPermission("misiones.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Solo los operadores pueden usar eso.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                manager.loadQuests();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Misiones recargadas (" + manager.all().size() + ").");
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /misiones reset <jugador> [id]");
                    return true;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                String questId = args.length > 2 ? args[2] : null;
                if (questId != null && manager.get(questId) == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Esa misión no existe.");
                    return true;
                }
                manager.reset(target.getUniqueId(), questId);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Progreso de " + args[1]
                        + (questId == null ? " reiniciado por completo." : " reiniciado para " + questId + "."));
            }
            default -> {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "/misiones" + ChatColor.GRAY + " - abre el menú");
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "/misiones reload" + ChatColor.GRAY + " - recarga quests.yml");
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "/misiones reset <jugador> [id]" + ChatColor.GRAY + " - reinicia progreso");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("misiones.admin")) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("reload", "reset"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return null; // jugadores online
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            for (Quest q : manager.all()) out.add(q.id);
        }
        String last = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(last));
        return out;
    }
}
