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

/** Фабрика снаряжения, фрагментов и чертежей с PDC-тегами. */
public final class GearFactory {

    /** Данные чертежа. */
    public static final class BlueprintData {
        public String type;    // WEAPON | ARMOR
        public String cls;     // WARRIOR | ...
        public String rarity;  // COMMON | RARE | EPIC | LEGENDARY
        public String slot;    // для брони: helmet | chestplate | leggings | boots
        public String setKey;  // ключ уникального сета (только LEGENDARY)
    }

    private final RaskolGear plugin;
    private final NamespacedKey kType;
    private final NamespacedKey kClass;
    private final NamespacedKey kRarity;
    private final NamespacedKey kSetKey;
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
    private final NamespacedKey kFragTier;
    private final NamespacedKey kBpType;
    private final NamespacedKey kBpClass;
    private final NamespacedKey kBpRarity;
    private final NamespacedKey kBpSlot;
    private final NamespacedKey kBpSetKey;

    public GearFactory(RaskolGear plugin) {
        this.plugin = plugin;
        kType = new NamespacedKey(plugin, "gear_type");
        kClass = new NamespacedKey(plugin, "gear_class");
        kRarity = new NamespacedKey(plugin, "gear_rarity");
        kSetKey = new NamespacedKey(plugin, "set_key");
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
        kFragTier = new NamespacedKey(plugin, "fragment_tier");
        kBpType = new NamespacedKey(plugin, "bp_type");
        kBpClass = new NamespacedKey(plugin, "bp_class");
        kBpRarity = new NamespacedKey(plugin, "bp_rarity");
        kBpSlot = new NamespacedKey(plugin, "bp_slot");
        kBpSetKey = new NamespacedKey(plugin, "bp_set_key");
    }

    /* ================= СЕКЦИИ КОНФИГА ================= */

    /**
     * Путь к секции предмета: для LEGENDARY — с ключом сета,
     * для остальных редкостей — напрямую.
     */
    private ConfigurationSection gearSection(String kind, String cls, String rarity, String setKey) {
        String base = kind + "." + cls + "." + rarity;
        if ("LEGENDARY".equals(rarity)) {
            if (setKey == null || setKey.isEmpty()) return null;
            return plugin.getConfig().getConfigurationSection(base + "." + setKey);
        }
        return plugin.getConfig().getConfigurationSection(base);
    }

    /** Имя уникального сета (для лора и сообщений). */
    public String setNameOf(String cls, String rarity, String setKey) {
        if (!"LEGENDARY".equals(rarity) || setKey == null) return null;
        return plugin.getConfig().getString(
                "armor." + cls + ".LEGENDARY." + setKey + ".set-name");
    }

    /* ================= ОРУЖИЕ ================= */

    public ItemStack createWeapon(String className, String rarity, String setKey) {
        ConfigurationSection section = gearSection("weapons", className, rarity, setKey);
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
        String setName = setNameOf(className, rarity, setKey);
        if (setName != null) {
            lore.add(color("&7Уникальный сет: &e" + setName));
        }
        lore.add(color("&cТолько для: &7" + className));
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kType, PersistentDataType.STRING, "WEAPON");
        pdc.set(kClass, PersistentDataType.STRING, className);
        pdc.set(kRarity, PersistentDataType.STRING, rarity);
        pdc.set(kSetKey, PersistentDataType.STRING, setKey == null ? "" : setKey);
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

    public ItemStack createArmor(String className, String rarity, String slot, String setKey) {
        ConfigurationSection section = gearSection("armor", className, rarity, setKey);
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
        pdc.set(kSetKey, PersistentDataType.STRING, setKey == null ? "" : setKey);
        pdc.set(kSlot, PersistentDataType.STRING, slot);
        pdc.set(kPhys, PersistentDataType.DOUBLE, phys);
        pdc.set(kMagic, PersistentDataType.DOUBLE, magic);
        pdc.set(kHp, PersistentDataType.DOUBLE, hp);
        pdc.set(kSet, PersistentDataType.STRING, setName);
        pdc.set(kReflect, PersistentDataType.DOUBLE, reflect);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

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

    /* ================= ФРАГМЕНТЫ ================= */

    public static Material fragmentMaterial(String tier) {
        return switch (tier) {
            case "iron" -> Material.IRON_NUGGET;
            case "steel" -> Material.GOLD_NUGGET;
            case "mithril" -> Material.ECHO_SHARD;
            default -> null;
        };
    }

    public ItemStack createFragment(String tier, int amount) {
        Material mat = fragmentMaterial(tier);
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String name = switch (tier) {
            case "iron" -> "&fЖелезный фрагмент";
            case "steel" -> "&7Стальной фрагмент";
            case "mithril" -> "&bМифриловый фрагмент";
            default -> "&fФрагмент";
        };
        meta.setDisplayName(color(name));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Используется в ковке снаряжения."));
        lore.add(color("&7Рецепт: чертёж в центр + 8 фрагментов."));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(kFragTier, PersistentDataType.STRING, tier);
        item.setItemMeta(meta);
        return item;
    }

    public String parseFragment(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(kFragTier, PersistentDataType.STRING);
    }

    /* ================= ЧЕРТЕЖИ ================= */

    public ItemStack createBlueprint(String type, String cls, String rarity, String slot, String setKey) {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String color = plugin.getConfig().getString("rarity." + rarity + ".color", "&f");
        String targetName;
        if ("WEAPON".equals(type)) {
            targetName = gearSection("weapons", cls, rarity, setKey) != null
                    ? gearSection("weapons", cls, rarity, setKey).getString("name", cls + " weapon")
                    : cls + " weapon";
        } else {
            String setName = gearSection("armor", cls, rarity, setKey) != null
                    ? gearSection("armor", cls, rarity, setKey).getString("set-name", cls)
                    : cls;
            targetName = setName + ": " + slotRu(slot);
        }
        meta.setDisplayName(color(color + "Чертёж: " + targetName));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7──────────────"));
        lore.add(color("&eТип: &7" + ("WEAPON".equals(type) ? "оружие" : "броня")));
        lore.add(color("&eКласс: &7" + cls));
        lore.add(color("&eКачество: &7" + rarity));
        if (setKey != null) lore.add(color("&eСет: &7" + setKey));
        if ("ARMOR".equals(type)) lore.add(color("&eСлот: &7" + slotRu(slot)));
        lore.add(color("&7Ковка: чертёж в центр верстака"));
        lore.add(color("&7+ 8 фрагментов вокруг."));
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kBpType, PersistentDataType.STRING, type);
        pdc.set(kBpClass, PersistentDataType.STRING, cls);
        pdc.set(kBpRarity, PersistentDataType.STRING, rarity);
        pdc.set(kBpSlot, PersistentDataType.STRING, slot == null ? "" : slot);
        pdc.set(kBpSetKey, PersistentDataType.STRING, setKey == null ? "" : setKey);
        item.setItemMeta(meta);
        return item;
    }

    public BlueprintData parseBlueprint(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String type = pdc.get(kBpType, PersistentDataType.STRING);
        if (type == null) return null;
        BlueprintData d = new BlueprintData();
        d.type = type;
        d.cls = pdc.get(kBpClass, PersistentDataType.STRING);
        d.rarity = pdc.get(kBpRarity, PersistentDataType.STRING);
        d.slot = pdc.get(kBpSlot, PersistentDataType.STRING);
        d.setKey = pdc.get(kBpSetKey, PersistentDataType.STRING);
        if (d.slot != null && d.slot.isEmpty()) d.slot = null;
        if (d.setKey != null && d.setKey.isEmpty()) d.setKey = null;
        return d;
    }

    /** Целевой предмет чертежа. */
    public ItemStack blueprintTarget(BlueprintData bp) {
        if (bp == null) return null;
        if ("WEAPON".equals(bp.type)) return createWeapon(bp.cls, bp.rarity, bp.setKey);
        if ("ARMOR".equals(bp.type) && bp.slot != null) return createArmor(bp.cls, bp.rarity, bp.slot, bp.setKey);
        return null;
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
