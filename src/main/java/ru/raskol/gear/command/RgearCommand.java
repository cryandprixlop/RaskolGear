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

    private static final List<String> SLOTS =
            Arrays.asList("helmet", "chestplate", "leggings", "boots", "set");
    private static final List<String> RARITIES =
            Arrays.asList("COMMON", "RARE", "EPIC", "LEGENDARY");

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
        sender.sendMessage("§e/rgear give <игрок> armor <класс> <редкость> <слот|set> §7— выдать броню");
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolgear.admin")) {
            sender.sendMessage("§cНет прав.");
            return;
        }
        if (args.length < 5) {
            sender.sendMessage("§cИспользуй: /rgear give <игрок> <weapon|armor> <класс> <редкость> [слот|set]");
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
            give(target, item);
            sender.sendMessage("§aВыдано: оружие §e" + className + " " + rarity + " §a-> " + target.getName());
            plugin.getLogger().info("[Gear] give: " + sender.getName() + " -> " + target.getName()
                    + " weapon " + className + " " + rarity);
            return;
        }

        if (gearType.equals("armor")) {
            if (args.length < 6) {
                sender.sendMessage("§cУкажи слот: helmet | chestplate | leggings | boots | set");
                return;
            }
            String slot = args[5].toLowerCase();
            if (slot.equals("set")) {
                int count = 0;
                for (String s : SLOTS) {
                    if (s.equals("set")) continue;
                    ItemStack piece = factory.createArmor(className, rarity, s);
                    if (piece != null) {
                        give(target, piece);
                        count++;
                    }
                }
                if (count == 0) {
                    sender.sendMessage("§cНе удалось создать сет: " + className + " " + rarity);
                    return;
                }
                sender.sendMessage("§aВыдан сет (" + count + " предм.): §e"
                        + className + " " + rarity + " §a-> " + target.getName());
                plugin.getLogger().info("[Gear] give: " + sender.getName() + " -> " + target.getName()
                        + " armor set " + className + " " + rarity);
            } else {
                ItemStack piece = factory.createArmor(className, rarity, slot);
                if (piece == null) {
                    sender.sendMessage("§cНе удалось создать броню: " + className + " " + rarity + " " + slot);
                    return;
                }
                give(target, piece);
                sender.sendMessage("§aВыдано: броня §e" + className + " " + rarity + " " + slot
                        + " §a-> " + target.getName());
                plugin.getLogger().info("[Gear] give: " + sender.getName() + " -> " + target.getName()
                        + " armor " + className + " " + rarity + " " + slot);
            }
            return;
        }

        sender.sendMessage("§cНеизвестный тип: " + gearType + " §7(weapon | armor)");
    }

    private void give(Player target, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        for (ItemStack drop : overflow.values()) {
            target.getWorld().dropItem(target.getLocation(), drop);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("give"), args[0]);
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
            return filter(RARITIES, args[4]);
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("give")
                && args[2].equalsIgnoreCase("armor")) {
            return filter(SLOTS, args[5]);
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
