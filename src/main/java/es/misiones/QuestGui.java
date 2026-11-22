package es.misiones;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class QuestGui implements Listener {

    private static class QuestHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final MisionesPlugin plugin;
    private final QuestManager qm;

    public QuestGui(MisionesPlugin plugin, QuestManager qm) {
        this.plugin = plugin;
        this.qm = qm;
    }

    public void open(Player p) {
        List<Quest> list = qm.all();
        if (list.isEmpty()) {
            p.sendMessage(MisionesPlugin.PREFIX + ChatColor.YELLOW + "No hay misiones configuradas.");
            return;
        }
        int size = Math.max(9, Math.min(54, (int) Math.ceil(list.size() / 9.0) * 9));
        Inventory inv = Bukkit.createInventory(new QuestHolder(), size,
                ChatColor.DARK_PURPLE + "Misiones " + ChatColor.GRAY + "(" + qm.activeCount(p.getUniqueId())
                        + "/" + qm.getMaxActive() + " activas)");
        for (int i = 0; i < list.size() && i < size; i++) {
            inv.setItem(i, display(p, list.get(i)));
        }
        p.openInventory(inv);
    }

    private ItemStack display(Player p, Quest q) {
        QuestManager.Progress pr = qm.progress(p.getUniqueId(), q.id);
        ItemStack it = new ItemStack(q.icon);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        meta.setDisplayName((pr.active ? ChatColor.YELLOW + "▶ " : "") + MisionesPlugin.color(q.name));

        List<String> lore = new ArrayList<>();
        for (String line : q.description) lore.add(MisionesPlugin.color(line));
        lore.add("");
        if (!q.rewardText.isEmpty()) {
            lore.add(ChatColor.GOLD + "Recompensa: " + MisionesPlugin.color(q.rewardText));
            lore.add("");
        }

        if (pr.active) {
            lore.add(ChatColor.YELLOW + "En progreso: " + ChatColor.WHITE + pr.progress + "/" + q.amount);
            lore.add(bar(pr.progress, q.amount));
            lore.add("");
            lore.add(ChatColor.GRAY + "Shift + click derecho para abandonar");
        } else if (pr.completed && !q.repeatable) {
            lore.add(ChatColor.GREEN + "✔ Completada");
        } else if (pr.completed) {
            lore.add(ChatColor.GREEN + "✔ Completada " + pr.times + (pr.times == 1 ? " vez" : " veces"));
            lore.add(ChatColor.YELLOW + "Click para repetirla");
        } else {
            lore.add(ChatColor.GRAY + "Objetivo: " + ChatColor.WHITE + q.amount);
            lore.add(ChatColor.YELLOW + "Click para aceptar");
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private String bar(int value, int max) {
        int filled = (int) Math.round(10.0 * value / max);
        StringBuilder sb = new StringBuilder(ChatColor.GREEN.toString());
        for (int i = 0; i < 10; i++) {
            if (i == filled) sb.append(ChatColor.DARK_GRAY);
            sb.append(i < filled ? "█" : "░");
        }
        return sb.toString();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof QuestHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        List<Quest> list = qm.all();
        int slot = e.getSlot();
        if (slot < 0 || slot >= list.size()) return;
        Quest q = list.get(slot);
        QuestManager.Progress pr = qm.progress(p.getUniqueId(), q.id);

        if (pr.active) {
            if (e.isShiftClick() && e.isRightClick()) {
                qm.abandon(p.getUniqueId(), q);
                p.sendMessage(MisionesPlugin.PREFIX + ChatColor.YELLOW + "Has abandonado la misión " + MisionesPlugin.color(q.name));
            } else {
                return;
            }
        } else {
            String error = qm.accept(p, q);
            if (error != null) {
                p.sendMessage(MisionesPlugin.PREFIX + ChatColor.RED + error);
            } else {
                p.sendMessage(MisionesPlugin.PREFIX + ChatColor.GREEN + "Misión aceptada: " + MisionesPlugin.color(q.name));
                if (qm.isIgnored(p)) {
                    p.sendMessage(MisionesPlugin.PREFIX + ChatColor.RED
                            + "Ojo: en modo creativo o espectador el progreso no cuenta. Cambia a supervivencia.");
                }
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> open(p));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof QuestHolder) e.setCancelled(true);
    }
}
