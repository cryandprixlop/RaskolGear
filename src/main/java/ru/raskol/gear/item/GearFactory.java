package ru.raskol.gear.item;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
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
    private final NamespacedKey kSlot;
    private final NamespacedKey kPhys;
    private final NamespacedKey kMagic;
    private final NamespacedKey kHp;
    private final NamespacedKey kSet;
    private final NamespacedKey kReflect;

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
        kSlot = new NamespacedKey(plugin, "armor_slot");
        kPhys = new NamespacedKey(plugin, "phys_resist");
        kMagic = new NamespacedKey(plugin, "magic_resist");
        kHp = new NamespacedKey(plugin, "hp_bonus");
        kSet = new NamespacedKey(plugin, "set_name");
        kReflect = new NamespacedKey(plugin, "reflect");
    }

    /* ================= ОРУЖИЕ ================= */

    public ItemStack createWeapon(String className, String rarity) {
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("weapons." + className + "." + rarity);
        if (section == null) return null;

        Material material = Material.matchMaterial(section.getString("material", "IRON_SWORD"));
        if (material == null) return null;
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String color = plugin.getConfig().getString("rarity." + rarity + ".color", "&f");
        meta.setDisplayName(color(color + section.getString("name", className + " weapon")));

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

    /* ================= БРОНЯ ================= */

    public ItemStack createArmor(String className, String rarity, String slot) {
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("armor." + className + "." + rarity);
        if (section == null) return null;

        String suffix = switch (slot) {
            case "helmet" -> "_HELMET";
            case "chestplate" -> "_CHESTPLATE";
            case "leggings" -> "_LEGGINGS";
            case "boots" -> "_BOOTS";
            default -> null;
        };
        if (suffix == null) return null;

        Material material = Material.matchMaterial(section.getString("material", "IRON") + suffix);
        if (material == null) return null;
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        double phys = section.getDouble("phys-resist");
        double magic = section.getDouble("magic-resist");
        double hp = section.getDouble("hp-bonus");
        double reflect = section.getDouble("reflect");
        String setName = section.getString("set-name", className);

        String color = plugin.getConfig().getString("rarity." + rarity + ".color", "&f");
        meta.setDisplayName(color(color + setName + ": " + slotRu(slot)));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7──────────────"));
        lore.add(color("&eФиз. резист: &7+" + phys + "%"));
        lore.add(color("&eМаг. резист: &7+" + magic + "%"));
        lore.add(color("&eЗдоровье: &7+" + hp + " HP"));
        if (reflect > 0) {
            lore.add(color("&eШипы: &7" + reflect + "% урона возвращается атакующему"));
            lore.add(color("&7(действует при полном сете 4/4)"));
        }
        lore.add(color("&eПрочность: &7Неразрушимый"));
        lore.add(color("&7Сет: &e" + setName + " &7— бонус при 4 предметах"));
        lore.add(color("&cТолько для: &7" + className));
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kType, PersistentDataType.STRING, "ARMOR");
        pdc.set(kClass, PersistentDataType.STRING, className);
        pdc.set(kRarity, PersistentDataType.STRING, rarity);
        pdc.set(kSlot, PersistentDataType.STRING, slot);
        pdc.set(kPhys, PersistentDataType.DOUBLE, phys);
        pdc.set(kMagic, PersistentDataType.DOUBLE, magic);
        pdc.set(kHp, PersistentDataType.DOUBLE, hp);
        pdc.set(kSet, PersistentDataType.STRING, setName);
        pdc.set(kReflect, PersistentDataType.DOUBLE, reflect);

        // Неразрушимая броня
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        // +HP через ванильный атрибут
        try {
            EquipmentSlotGroup group = switch (slot) {
                case "helmet" -> EquipmentSlotGroup.HEAD;
                case "chestplate" -> EquipmentSlotGroup.CHEST;
                case "leggings" -> EquipmentSlotGroup.LEGS;
                case "boots" -> EquipmentSlotGroup.FEET;
                default -> EquipmentSlotGroup.ARMOR;
            };
            meta.addAttributeModifier(Attribute.GENERIC_MAX_HEALTH, new AttributeModifier(
                    new NamespacedKey(plugin, "hp_" + slot), hp,
                    AttributeModifier.Operation.ADD_NUMBER, group));
        } catch (Throwable t) {
            plugin.getLogger().warning("[Gear] не удалось добавить атрибут HP: " + t.getMessage());
        }

        item.setItemMeta(meta);
        return item;
    }

    /* ================= ЧТЕНИЕ ================= */

    public String parseType(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(kType, PersistentDataType.STRING);
    }

    public String parseClass(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(kClass, PersistentDataType.STRING);
    }

    private String slotRu(String slot) {
        return switch (slot) {
            case "helmet" -> "шлем";
            case "chestplate" -> "нагрудник";
            case "leggings" -> "поножи";
            case "boots" -> "сапоги";
            default -> slot;
        };
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
