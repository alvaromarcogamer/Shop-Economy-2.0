package es.misiones;

import org.bukkit.block.Block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Recuerda (en memoria) qué bloques han colocado los jugadores, con un límite para no gastar RAM. */
final class PlacedBlocks {

    private static final int MAX = 100_000;

    private final Set<String> set = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX;
        }
    });

    private static String key(Block b) {
        return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    void add(Block b) {
        set.add(key(b));
    }

    /** Devuelve true si el bloque estaba marcado como colocado por un jugador (y lo desmarca). */
    boolean remove(Block b) {
        return set.remove(key(b));
    }
}
