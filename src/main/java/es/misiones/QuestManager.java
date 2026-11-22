package es.misiones;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Carga las misiones y guarda/gestiona el progreso de cada jugador. */
public class QuestManager {

    public static class Progress {
        public int progress;
        public boolean active;
        public boolean completed;
        public int times;
    }

    private final MisionesPlugin plugin;
    private final Map<String, Quest> quests = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Progress>> data = new HashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private File dataFile;
    private int maxActive = 3;
    private boolean ignoreCreative = true;
    private boolean antiExploit = true;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public QuestManager(MisionesPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- Carga ----------

    public void init() {
        loadQuests();
        dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = cfg.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidKey : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection ps = players.getConfigurationSection(uuidKey);
            if (ps == null) continue;
            Map<String, Progress> map = new HashMap<>();
            for (String questId : ps.getKeys(false)) {
                ConfigurationSection qs = ps.getConfigurationSection(questId);
                if (qs == null) continue;
                Progress p = new Progress();
                p.progress = qs.getInt("progress");
                p.active = qs.getBoolean("active");
                p.completed = qs.getBoolean("completed");
                p.times = qs.getInt("times");
                map.put(questId, p);
            }
            data.put(uuid, map);
        }
    }

    public void loadQuests() {
        plugin.reloadConfig();
        maxActive = Math.max(1, plugin.getConfig().getInt("max-active-quests", 3));
        ignoreCreative = plugin.getConfig().getBoolean("ignore-creative", true);
        antiExploit = plugin.getConfig().getBoolean("anti-exploit", true);

        File f = new File(plugin.getDataFolder(), "quests.yml");
        if (!f.exists()) plugin.saveResource("quests.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);

        quests.clear();
        ConfigurationSection sec = cfg.getConfigurationSection("quests");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection q = sec.getConfigurationSection(id);
            if (q == null) continue;
            try {
                Quest.Type type = Quest.Type.valueOf(q.getString("type", "").toUpperCase());
                int amount = Math.max(1, q.getInt("amount", 1));

                Material icon = Material.matchMaterial(q.getString("icon", "PAPER"));
                if (icon == null) icon = Material.PAPER;

                Set<String> targets = new HashSet<>();
                Object t = q.get("target");
                if (t instanceof List<?> list) {
                    for (Object o : list) targets.add(String.valueOf(o).toUpperCase());
                } else if (t != null) {
                    targets.add(String.valueOf(t).toUpperCase());
                }
                if (targets.contains("ANY")) targets.clear();

                // Avisar de errores de escritura que harían que la misión nunca cuente
                for (String tg : targets) {
                    boolean valid = switch (type) {
                        case KILL_MOB, BREED -> isEntity(tg);
                        case BREAK_BLOCK, PLACE_BLOCK, EAT -> Material.matchMaterial(tg) != null;
                        case FISH -> true;
                    };
                    if (!valid) {
                        plugin.getLogger().warning("Misión '" + id + "': el objetivo '" + tg
                                + "' no existe para el tipo " + type + ". Revisa que esté bien escrito.");
                    }
                }

                List<ItemStack> items = new ArrayList<>();
                for (String s : stringList(q, "reward-items")) {
                    String[] parts = s.split(":");
                    Material m = Material.matchMaterial(parts[0].trim());
                    if (m == null) {
                        plugin.getLogger().warning("Misión " + id + ": objeto de recompensa desconocido: " + s);
                        continue;
                    }
                    int amt = 1;
                    if (parts.length > 1) {
                        try {
                            amt = Math.max(1, Integer.parseInt(parts[1].trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    items.add(new ItemStack(m, amt));
                }

                quests.put(id, new Quest(id, q.getString("name", id), stringList(q, "description"), type, targets,
                        amount, icon, q.getBoolean("repeatable", false), q.getString("reward-text", ""),
                        Math.max(0, q.getDouble("reward-money", 0)), stringList(q, "reward-commands"), items));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Misión '" + id + "' ignorada: tipo inválido. Usa KILL_MOB, BREAK_BLOCK, PLACE_BLOCK, FISH, EAT o BREED.");
            }
        }
        plugin.getLogger().info("Misiones cargadas: " + quests.size());
    }

    private boolean isEntity(String name) {
        try {
            EntityType.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private List<String> stringList(ConfigurationSection s, String path) {
        if (s.isList(path)) return s.getStringList(path);
        String single = s.getString(path);
        List<String> out = new ArrayList<>();
        if (single != null) out.add(single);
        return out;
    }

    // ---------- Guardado ----------

    public void saveIfDirty() {
        if (dataFile == null || !dirty.getAndSet(false)) return;
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Progress>> e : data.entrySet()) {
            for (Map.Entry<String, Progress> q : e.getValue().entrySet()) {
                Progress p = q.getValue();
                if (!p.active && !p.completed && p.progress == 0 && p.times == 0) continue;
                String base = "players." + e.getKey() + "." + q.getKey();
                cfg.set(base + ".progress", p.progress);
                cfg.set(base + ".active", p.active);
                cfg.set(base + ".completed", p.completed);
                cfg.set(base + ".times", p.times);
            }
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            dirty.set(true);
            plugin.getLogger().severe("No se pudo guardar playerdata.yml: " + e.getMessage());
        }
    }

    // ---------- Consultas ----------

    public List<Quest> all() {
        return new ArrayList<>(quests.values());
    }

    public Quest get(String id) {
        return quests.get(id);
    }

    public int getMaxActive() {
        return maxActive;
    }

    public Progress progress(UUID id, String questId) {
        return data.computeIfAbsent(id, k -> new HashMap<>()).computeIfAbsent(questId, k -> new Progress());
    }

    public int activeCount(UUID id) {
        Map<String, Progress> m = data.get(id);
        if (m == null) return 0;
        int n = 0;
        for (Map.Entry<String, Progress> e : m.entrySet()) {
            if (e.getValue().active && quests.containsKey(e.getKey())) n++;
        }
        return n;
    }

    public boolean isAntiExploit() {
        return antiExploit;
    }

    /** true si el modo de juego del jugador hace que el progreso no cuente. */
    public boolean isIgnored(Player p) {
        if (!ignoreCreative) return false;
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    /** true si el progreso del jugador cuenta ahora. Si no, le avisa (como mucho cada 10 s). */
    public boolean counts(Player p) {
        if (!isIgnored(p)) return true;
        if (activeCount(p.getUniqueId()) > 0) {
            long now = System.currentTimeMillis();
            Long last = lastWarn.get(p.getUniqueId());
            if (last == null || now - last > 10_000) {
                lastWarn.put(p.getUniqueId(), now);
                actionBar(p, ChatColor.RED + "El progreso de las misiones no cuenta en modo "
                        + (p.getGameMode() == GameMode.CREATIVE ? "creativo" : "espectador"));
            }
        }
        return false;
    }

    // ---------- Acciones ----------

    /** Devuelve null si se aceptó, o el motivo del error. */
    public String accept(Player p, Quest q) {
        Progress pr = progress(p.getUniqueId(), q.id);
        if (pr.active) return "Ya tienes esta misión activa.";
        if (pr.completed && !q.repeatable) return "Ya completaste esta misión.";
        if (activeCount(p.getUniqueId()) >= maxActive) {
            return "Solo puedes tener " + maxActive + " misiones activas a la vez.";
        }
        pr.active = true;
        pr.completed = false;
        pr.progress = 0;
        dirty.set(true);
        return null;
    }

    public boolean abandon(UUID id, Quest q) {
        Progress pr = progress(id, q.id);
        if (!pr.active) return false;
        pr.active = false;
        pr.progress = 0;
        dirty.set(true);
        return true;
    }

    public void reset(UUID id, String questId) {
        Map<String, Progress> m = data.get(id);
        if (m == null) return;
        if (questId == null) m.clear();
        else m.remove(questId);
        dirty.set(true);
    }

    public void addProgress(Player p, Quest.Type type, String target, int amount) {
        Map<String, Progress> m = data.get(p.getUniqueId());
        if (m == null) return;
        for (Quest q : quests.values()) {
            if (q.type != type) continue;
            Progress pr = m.get(q.id);
            if (pr == null || !pr.active || !q.matches(target)) continue;

            pr.progress = Math.min(q.amount, pr.progress + amount);
            dirty.set(true);
            if (pr.progress >= q.amount) {
                complete(p, q, pr);
            } else {
                actionBar(p, MisionesPlugin.color(q.name) + ChatColor.GRAY + ": " + ChatColor.WHITE
                        + pr.progress + "/" + q.amount);
            }
        }
    }

    private void complete(Player p, Quest q, Progress pr) {
        pr.active = false;
        pr.completed = true;
        pr.progress = q.amount;
        pr.times++;

        p.sendMessage(MisionesPlugin.PREFIX + ChatColor.GREEN + "¡Misión completada! " + MisionesPlugin.color(q.name));
        if (!q.rewardText.isEmpty()) {
            p.sendMessage(MisionesPlugin.PREFIX + ChatColor.GOLD + "Recompensa: " + MisionesPlugin.color(q.rewardText));
        }
        if (q.rewardMoney > 0) {
            MoneyService money = plugin.money();
            if (money != null) {
                money.deposit(p, q.rewardMoney);
                p.sendMessage(MisionesPlugin.PREFIX + ChatColor.GOLD + "+" + money.format(q.rewardMoney));
            } else {
                plugin.getLogger().warning("La misión " + q.id + " da dinero, pero no hay economía de Vault. "
                        + "Instala Vault y un plugin de economía (por ejemplo ShopEconomy).");
            }
        }
        for (ItemStack it : q.items) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
            for (ItemStack rest : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
        for (String cmd : q.commands) {
            String c = cmd.replace("%player%", p.getName()).trim();
            if (c.startsWith("/")) c = c.substring(1);
            if (c.isEmpty()) continue;
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            if (!ok) {
                plugin.getLogger().warning("El comando de recompensa falló o no existe: /" + c
                        + " (misión " + q.id + "). ¿Está instalado el plugin que lo ejecuta?");
            }
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    private void actionBar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }
}
