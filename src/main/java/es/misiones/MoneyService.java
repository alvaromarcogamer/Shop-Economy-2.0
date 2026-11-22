package es.misiones;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Acceso al dinero a través de Vault. Solo se usa si Vault está instalado. */
final class MoneyService {

    private final Economy eco;

    private MoneyService(Economy eco) {
        this.eco = eco;
    }

    static MoneyService create(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new MoneyService(rsp.getProvider());
    }

    void deposit(Player p, double amount) {
        eco.depositPlayer(p, amount);
    }

    String format(double amount) {
        return eco.format(amount);
    }
}
