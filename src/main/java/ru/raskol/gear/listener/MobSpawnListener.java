package ru.raskol.gear.listener;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import ru.raskol.gear.RaskolGear;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ставит HP и урон мобам Раскола при первом ударе по ним.
 * HP ≤ 1024: через setBaseValue. HP > 1024: база 1024 + модификаторы.
 * Плюс: наши мобы (имя с "N ур.") никогда не горят.
 */
public final class MobSpawnListener implements Listener {

    private static final Pattern LEVEL_PATTERN = Pattern.compile("(\\d+)\\s*ур");
    private static final double VANILLA_HP_CAP = 1024.0;

    private final RaskolGear plugin;
    private final NamespacedKey kStatsApplied;

    public MobSpawnListener(RaskolGear plugin) {
        this.plugin = plugin;
        kStatsApplied = new NamespacedKey(plugin, "mob_stats_applied");
        plugin.getLogger().info("[Gear] MobSpawnListener initialized");
    }

    public static Integer parseLevel(String strippedName) {
        if (strippedName == null) return null;
        Matcher m = LEVEL_PATTERN.matcher(strippedName);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /* ========== Наши мобы не горят (солнце, огонь) ========== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        String raw = entity.getCustomName();
        if (raw == null) return;
        if (parseLevel(ChatColor.stripColor(raw)) != null) {
            event.setCancelled(true);
        }
    }

    /* ========== Статы при первом ударе ========== */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity living)) return;

        String raw = living.getCustomName();
        if (raw == null) return;

        String name = ChatColor.stripColor(raw);
        Integer level = parseLevel(name);
        if (level == null) return;

        if (living.getPersistentDataContainer().has(kStatsApplied, PersistentDataType.BYTE)) return;

        ConfigurationSection stats = findStats(name);
        if (stats == null) {
            plugin.getLogger().info("[Gear] No stats config found for: " + name);
            return;
        }

        double hp = stats.getDouble("hp", 0.0);
        double damage = stats.getDouble("damage", 0.0);

        if (hp > 0) applyMaxHealth(living, hp, name);
        if (damage > 0) applyAttackDamage(living, damage);

        living.getPersistentDataContainer().set(kStatsApplied, PersistentDataType.BYTE, (byte) 1);
        plugin.getLogger().info("[Gear] mob stats applied: " + name + " hp=" + hp + " dmg=" + damage);
    }

    private ConfigurationSection findStats(String name) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("drops.mob-stats");
        if (root == null) return null;
        for (String key : root.getKeys(false)) {
            if (name.contains(key)) return root.getConfigurationSection(key);
        }
        return null;
    }

    private void applyMaxHealth(LivingEntity entity, double target, String name) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;

        if (target <= VANILLA_HP_CAP) {
            attr.setBaseValue(target);
            entity.setHealth(target);
        } else {
            double base = VANILLA_HP_CAP;
            attr.setBaseValue(base);

            double need = target - base;
            int count = (int) Math.ceil(need / 8000.0);
            double each = need / count;

            attr.getModifiers().stream()
                    .filter(m -> m.getKey().getNamespace().equals(plugin.getName().toLowerCase())
                            && m.getKey().getKey().startsWith("boss_hp_"))
                    .forEach(attr::removeModifier);

            for (int i = 0; i < count; i++) {
                NamespacedKey key = new NamespacedKey(plugin, "boss_hp_" + i);
                attr.addModifier(new AttributeModifier(key, each,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
            }

            double actual = attr.getValue();
            entity.setHealth(Math.min(target, actual));

            plugin.getLogger().info("[Gear] boss HP attempt: " + name
                    + " target=" + target + " actual=" + actual
                    + (actual <= VANILLA_HP_CAP ? " (PAPER CAP)" : ""));
        }
    }

    private void applyAttackDamage(LivingEntity entity, double target) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attr == null) return;
        attr.setBaseValue(Math.min(target, 2048.0));
    }
}
