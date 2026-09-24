package ru.raskol.gear.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.raskol.gear.RaskolGear;
import ru.raskol.gear.item.GearFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public final class RgearCommand implements CommandExecutor, TabCompleter {

    private final RaskolGear plugin;
    private final GearFactory factory;

    public RgearCommand(RaskolGear plugin, GearFactory factory) {
        this.plugin = plugin;
        this.factory = factory;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            handleGive(sender, args);
        } else {
            sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== RaskolGear ===");
        sender.sendMessage("§e/rgear give <игрок> weapon <класс> <редкость> §7— выдать оружие");
        sender.sendMessage("§e/rgear give <игрок> armor <класс> <редкость> <слот> §7— выдать броню");
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolgear.admin")) {
            sender.sendMessage("§cНет прав.");
            return;
        }
        if (args.length < 5) {
            sender.sendMessage("§cИспользуй: /rgear give <игрок> weapon <класс> <редкость>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден: " + args[1]);
            return;
        }

        String gearType = args[2].toLowerCase();
        String className = args[3].toUpperCase();
        String rarity = args[4].toUpperCase();

        if (gearType.equals("weapon")) {
            ItemStack item = factory.createWeapon(className, rarity);
            if (item == null) {
                sender.sendMessage("§cНе удалось создать оружие: " + className + " " + rarity);
                return;
            }
            HashMap<Integer, ItemStack> overflow = target.getInventory().addItem(item);
            for (ItemStack drop : overflow.values()) {
                target.getWorld().dropItem(target.getLocation(), drop);
            }
            target.sendMessage("§aПолучено оружие: §e" + className + " " + rarity);
            sender.sendMessage("§aВыдано игроку §e" + target.getName() + "§a: " + className + " " + rarity);
            plugin.getLogger().info("[Gear] give: " + sender.getName() + " -> "
                    + target.getName() + " weapon " + className + " " + rarity);
        } else {
            sender.sendMessage("§cБроня будет доступна в Этапе 3.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(Arrays.asList("weapon", "armor"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filter(Arrays.asList("WARRIOR", "HUNTER", "PRIEST", "MAGE", "ROGUE"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            return filter(Arrays.asList("COMMON", "RARE", "EPIC"), args[4]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) out.add(s);
        }
        return out;
    }
}
