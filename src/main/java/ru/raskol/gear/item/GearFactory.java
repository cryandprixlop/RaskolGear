package ru.raskol.gear.item;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.raskol.gear.RaskolGear;

import java.util.ArrayList;
import java.util.List;

/** Фабрика предметов снаряжения с PDC-тегами. */
public final class GearFactory {

    private final RaskolGear plugin;
    private final NamespacedKey kType;
    private final NamespacedKey kClass;
    private final NamespacedKey kRarity;
    private final NamespacedKey kDamageType;
    private final NamespacedKey kBaseDamage;
    private final NamespacedKey kPowerCoeff;
    private final NamespacedKey kCritBonus;
    private final NamespacedKey kProcChance;
    private final NamespacedKey kProcEffect;
    private final NamespacedKey kProcDuration;

    public GearFactory(RaskolGear plugin) {
        this.plugin = plugin;
        kType = new NamespacedKey(plugin, "gear_type");
        kClass = new NamespacedKey(plugin, "gear_class");
        kRarity = new NamespacedKey(plugin, "gear_rarity");
        kDamageType = new NamespacedKey(plugin, "damage_type");
        kBaseDamage = new NamespacedKey(plugin, "base_damage");
        kPowerCoeff = new NamespacedKey(plugin, "power_coeff");
        kCritBonus = new NamespacedKey(plugin, "crit_bonus");
        kProcChance = new NamespacedKey(plugin, "proc_chance");
        kProcEffect = new NamespacedKey(plugin, "proc_effect");
        kProcDuration = new NamespacedKey(plugin, "proc_duration");
    }

    public ItemStack createWeapon(String className, String rarity) {
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("weapons." + className + "." + rarity);
        if (section == null) return null;

        Material material = Material.valueOf(section.getString("material", "IRON_SWORD"));
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String color = plugin.getConfig().getString("rarity." + rarity + ".color", "&f");
        String name = section.getString("name", className + " weapon");
        meta.setDisplayName(color(color + name));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7──────────────"));
        lore.add(color("&eУрон: &c" + section.getDouble("base-damage")
                + " &7(+ &c" + section.getDouble("power-coeff") + "×Power&7)"));
        lore.add(color("&eТип: &7" + section.getString("damage-type")));
        lore.add(color("&eКрит: &7+" + section.getDouble("crit-bonus") + "%"));
        if (section.contains("proc")) {
            lore.add(color("&eПроки: &7" + section.getDouble("proc.chance")
                    + "% шанс " + section.getString("proc.effect")
                    + " (" + section.getInt("proc.duration") + " сек)"));
        }
        lore.add(color("&cТолько для: &7" + className));
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kType, PersistentDataType.STRING, "WEAPON");
        pdc.set(kClass, PersistentDataType.STRING, className);
        pdc.set(kRarity, PersistentDataType.STRING, rarity);
        pdc.set(kDamageType, PersistentDataType.STRING, section.getString("damage-type", "physical"));
        pdc.set(kBaseDamage, PersistentDataType.DOUBLE, section.getDouble("base-damage"));
        pdc.set(kPowerCoeff, PersistentDataType.DOUBLE, section.getDouble("power-coeff"));
        pdc.set(kCritBonus, PersistentDataType.DOUBLE, section.getDouble("crit-bonus"));
        if (section.contains("proc")) {
            pdc.set(kProcChance, PersistentDataType.DOUBLE, section.getDouble("proc.chance"));
            pdc.set(kProcEffect, PersistentDataType.STRING, section.getString("proc.effect"));
            pdc.set(kProcDuration, PersistentDataType.INTEGER, section.getInt("proc.duration"));
        }

        item.setItemMeta(meta);
        return item;
    }

    public String parseClass(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(kClass, PersistentDataType.STRING);
    }

    public String parseType(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(kType, PersistentDataType.STRING);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
