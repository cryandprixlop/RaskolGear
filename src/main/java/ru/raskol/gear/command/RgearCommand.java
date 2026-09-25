package ru.raskol.gear.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
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
    private static final List<String> TIERS =
            Arrays.asList("iron", "steel", "mithril");

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
        sender.sendMessage("§e/rgear give <игрок> weapon <класс> <редкость> [сет] §7— выдать оружие");
        sender.sendMessage("§e/rgear give <игрок> armor <класс> <редкость> <слот|set> [сет] §7— выдать броню");
        sender.sendMessage("§e/rgear give <игрок> fragment <iron|steel|mithril> [кол-во] §7— выдать фрагменты");
        sender.sendMessage("§e/rgear give <игрок> blueprint <weapon|armor> <класс> <редкость> [слот] [сет] §7— выдать чертёж");
        sender.sendMessage("§7Для LEGENDARY ключ сета обязателен (например: porcupine).");
    }

    private List<String> setKeys(String kind, String cls) {
        ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection(kind + "." + cls + ".LEGENDARY");
        return sec == null ? Collections.emptyList() : new ArrayList<>(sec.getKeys(false));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolgear.admin")) {
            sender.sendMessage("§cНет прав.");
            return;
        }
        if (args.length < 4) {
            sendHelp(sender);
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден: " + args[1]);
            return;
        }

        String kind = args[2].toLowerCase();

        /* ----- фрагменты ----- */
        if (kind.equals("fragment")) {
            String tier = args[3].toLowerCase();
            int amount = 1;
            if (args.length >= 5) {
                try { amount = Integer.parseInt(args[4]); } catch (NumberFormatException e) { amount = 1; }
            }
            ItemStack item = factory.createFragment(tier, amount);
            if (item == null) {
                sender.sendMessage("§cНеизвестный тир: " + tier + " §7(iron | steel | mithril)");
                return;
            }
            give(target, item);
            sender.sendMessage("§aВыдано: фрагменты §e" + tier + " x" + amount + " §a-> " + target.getName());
            return;
        }

        /* ----- чертёж ----- */
        if (kind.equals("blueprint")) {
            if (args.length < 6) {
                sender.sendMessage("§cИспользуй: blueprint <weapon|armor> <класс> <редкость> [слот] [сет]");
                return;
            }
            String type = args[3].toUpperCase();
            String cls = args[4].toUpperCase();
            String rarity = args[5].toUpperCase();
            String slot = null;
            String setKey = null;
            if (type.equals("ARMOR")) {
                if (args.length < 7) {
                    sender.sendMessage("§cДля чертежа брони укажи слот: helmet | chestplate | leggings | boots");
                    return;
                }
                slot = args[6].toLowerCase();
                if (rarity.equals("LEGENDARY")) {
                    if (args.length < 8) {
                        sender.sendMessage("§cДля LEGENDARY укажи сет: " + setKeys("armor", cls));
                        return;
                    }
                    setKey = args[7].toLowerCase();
                }
            } else if (type.equals("WEAPON")) {
                if (rarity.equals("LEGENDARY")) {
                    if (args.length < 7) {
                        sender.sendMessage("§cДля LEGENDARY укажи сет: " + setKeys("weapons", cls));
                        return;
                    }
                    setKey = args[6].toLowerCase();
                }
            } else {
                sender.sendMessage("§cТип чертежа: weapon | armor");
                return;
            }
            ItemStack item = factory.createBlueprint(type, cls, rarity, slot, setKey);
            if (item == null) {
                sender.sendMessage("§cНе удалось создать чертёж.");
                return;
            }
            give(target, item);
            sender.sendMessage("§aВыдан чертёж: §e" + item.getItemMeta().getDisplayName()
                    + " §a-> " + target.getName());
            return;
        }

        /* ----- оружие / броня ----- */
        if (args.length < 5) {
            sendHelp(sender);
            return;
        }
        String className = args[3].toUpperCase();
        String rarity = args[4].toUpperCase();

        if (kind.equals("weapon")) {
            String setKey = null;
            if (rarity.equals("LEGENDARY")) {
                if (args.length < 6) {
                    sender.sendMessage("§cДля LEGENDARY укажи сет: " + setKeys("weapons", className));
                    return;
                }
                setKey = args[5].toLowerCase();
            }
            ItemStack item = factory.createWeapon(className, rarity, setKey);
            if (item == null) {
                sender.sendMessage("§cНе удалось создать оружие: " + className + " " + rarity
                        + (setKey != null ? " " + setKey : ""));
                return;
            }
            give(target, item);
            sender.sendMessage("§aВыдано: §e" + item.getItemMeta().getDisplayName()
                    + " §a-> " + target.getName());
            plugin.getLogger().info("[Gear] give: " + sender.getName() + " -> " + target.getName()
                    + " weapon " + className + " " + rarity + (setKey != null ? " " + setKey : ""));
            return;
        }

        if (kind.equals("armor")) {
            if (args.length < 6) {
                sender.sendMessage("§cУкажи слот: helmet | chestplate | leggings | boots | set");
                return;
            }
            String slot = args[5].toLowerCase();
            String setKey = null;
            if (rarity.equals("LEGENDARY")) {
                if (args.length < 7) {
                    sender.sendMessage("§cДля LEGENDARY укажи сет: " + setKeys("armor", className));
                    return;
                }
                setKey = args[6].toLowerCase();
            }
            if (slot.equals("set")) {
                int count = 0;
                for (String s : SLOTS) {
                    if (s.equals("set")) continue;
                    ItemStack piece = factory.createArmor(className, rarity, s, setKey);
                    if (piece != null) { give(target, piece); count++; }
                }
                if (count == 0) {
                    sender.sendMessage("§cНе удалось создать сет: " + className + " " + rarity
                            + (setKey != null ? " " + setKey : ""));
                    return;
                }
                sender.sendMessage("§aВыдан сет (§e" + count + " предм.§a): §e"
                        + className + " " + rarity + (setKey != null ? " " + setKey : "")
                        + " §a-> " + target.getName());
            } else {
                ItemStack piece = factory.createArmor(className, rarity, slot, setKey);
                if (piece == null) {
                    sender.sendMessage("§cНе удалось создать броню: " + className + " " + rarity
                            + " " + slot + (setKey != null ? " " + setKey : ""));
                    return;
                }
                give(target, piece);
                sender.sendMessage("§aВыдано: §e" + piece.getItemMeta().getDisplayName()
                        + " §a-> " + target.getName());
            }
            return;
        }

        sender.sendMessage("§cНеизвестный тип: " + kind + " §7(weapon | armor | fragment | blueprint)");
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
            return filter(Arrays.asList("weapon", "armor", "fragment", "blueprint"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            if (args[2].equalsIgnoreCase("fragment")) return filter(TIERS, args[3]);
            if (args[2].equalsIgnoreCase("blueprint")) return filter(Arrays.asList("weapon", "armor"), args[3]);
            return filter(Arrays.asList("WARRIOR", "HUNTER", "PRIEST", "MAGE", "ROGUE"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            if (args[2].equalsIgnoreCase("blueprint")) {
                return filter(Arrays.asList("WARRIOR", "HUNTER", "PRIEST", "MAGE", "ROGUE"), args[4]);
            }
            return filter(RARITIES, args[4]);
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("give")) {
            if (args[2].equalsIgnoreCase("weapon")) {
                if (args[4].equalsIgnoreCase("LEGENDARY")) return setKeys("weapons", args[3]);
                return Collections.emptyList();
            }
            if (args[2].equalsIgnoreCase("armor")) return filter(SLOTS, args[5]);
            if (args[2].equalsIgnoreCase("blueprint")) return filter(RARITIES, args[5]);
        }
        if (args.length == 7 && args[0].equalsIgnoreCase("give")) {
            if (args[2].equalsIgnoreCase("armor") && args[4].equalsIgnoreCase("LEGENDARY")) {
                return setKeys("armor", args[3]);
            }
            if (args[2].equalsIgnoreCase("blueprint") && args[3].equalsIgnoreCase("armor")) {
                return filter(Arrays.asList("helmet", "chestplate", "leggings", "boots"), args[6]);
            }
            if (args[2].equalsIgnoreCase("blueprint") && args[3].equalsIgnoreCase("weapon")
                    && args[5].equalsIgnoreCase("LEGENDARY")) {
                return setKeys("weapons", args[4]);
            }
        }
        if (args.length == 8 && args[0].equalsIgnoreCase("give")
                && args[2].equalsIgnoreCase("blueprint") && args[3].equalsIgnoreCase("armor")
                && args[5].equalsIgnoreCase("LEGENDARY")) {
            return setKeys("armor", args[4]);
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
