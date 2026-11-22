package es.misiones;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

public class Quest {

    public enum Type {KILL_MOB, BREAK_BLOCK, PLACE_BLOCK, FISH, EAT, BREED}

    final String id;
    final String name;
    final List<String> description;
    final Type type;
    final Set<String> targets; // vacío = cualquiera
    final int amount;
    final Material icon;
    final boolean repeatable;
    final String rewardText;
    final double rewardMoney;
    final List<String> commands;
    final List<ItemStack> items;

    Quest(String id, String name, List<String> description, Type type, Set<String> targets, int amount,
          Material icon, boolean repeatable, String rewardText, double rewardMoney, List<String> commands, List<ItemStack> items) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.targets = targets;
        this.amount = amount;
        this.icon = icon;
        this.repeatable = repeatable;
        this.rewardText = rewardText;
        this.rewardMoney = rewardMoney;
        this.commands = commands;
        this.items = items;
    }

    boolean matches(String target) {
        return targets.isEmpty() || targets.contains(target.toUpperCase());
    }
}
