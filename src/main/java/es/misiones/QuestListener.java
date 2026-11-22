package es.misiones;

import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/** Detecta lo que hacen los jugadores y suma progreso a sus misiones. */
public class QuestListener implements Listener {

    private final QuestManager qm;
    private final PlacedBlocks placed = new PlacedBlocks();

    public QuestListener(QuestManager qm) {
        this.qm = qm;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player || e.getEntity() instanceof ArmorStand) return;
        Player p = e.getEntity().getKiller();
        if (p == null || !qm.counts(p)) return;
        qm.addProgress(p, Quest.Type.KILL_MOB, e.getEntityType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        boolean wasPlaced = placed.remove(e.getBlock());
        Player p = e.getPlayer();
        if (!qm.counts(p)) return;
        // Anti-abuso: los bloques colocados por jugadores no cuentan al romperlos
        if (wasPlaced && qm.isAntiExploit()) return;
        qm.addProgress(p, Quest.Type.BREAK_BLOCK, e.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        // Los cultivos se excluyen para que se puedan plantar y cosechar
        if (qm.isAntiExploit() && !(b.getBlockData() instanceof Ageable)) placed.add(b);
        Player p = e.getPlayer();
        if (!qm.counts(p)) return;
        qm.addProgress(p, Quest.Type.PLACE_BLOCK, b.getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        if (!qm.counts(p)) return;
        qm.addProgress(p, Quest.Type.FISH, "FISH", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!qm.counts(p)) return;
        qm.addProgress(p, Quest.Type.EAT, e.getItem().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (!(e.getBreeder() instanceof Player p) || !qm.counts(p)) return;
        qm.addProgress(p, Quest.Type.BREED, e.getEntityType().name(), 1);
    }
}
