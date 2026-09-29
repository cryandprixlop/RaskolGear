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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import ru.raskol.gear.RaskolGear;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ставит HP и урон мобам Раскола при первом ударе по ним.
 */
public final class MobSpawnListener implements Listener {

    private static final Pattern LEVEL_PATTERN = Pattern.compile("(\\d+)\\s*ур");

    private final RaskolGear plugin;
    private final NamespacedKey kStatsApplied;
    private final NamespacedKey kHpMod;

    public MobSpawnListener(RaskolGear plugin) {
        this.plugin = plugin;
        kStatsApplied = new NamespacedKey(plugin, "mob_stats_applied");
        kHpMod = new NamespacedKey(plugin, "boss_hp_0");
        plugin.getLogger().info("[Gear] MobSpawnListener initialized");
    }

    public static Integer parseLevel(String strippedName) {
        if (strippedName == null) return null;
        Matcher m = LEVEL_PATTERN.matcher(strippedName);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity living)) {
            return;
        }

        String raw = living.getCustomName();
        if (raw == null) return;
        
        String name = ChatColor.stripColor(raw);
        Integer level = parseLevel(name);
        
        if (level == null) return;

        // Отладка: пишем в лог каждый раз, когда видим нашего моба
        plugin.getLogger().info("[Gear] MobSpawnListener triggered for: " + name + " lvl=" + level);

        if (living.getPersistentDataContainer().has(kStatsApplied, PersistentDataType.BYTE)) {
            plugin.getLogger().info("[Gear] Stats already applied for: " + name);
            return;
        }

        ConfigurationSection stats = findStats(name);
        if (stats == null) {
            plugin.getLogger().info("[Gear] No stats config found for: " + name);
            return;
        }

        double hp = stats.getDouble("hp", 0.0);
        double damage = stats.getDouble("damage", 0.0);

        if (hp > 0) applyMaxHealth(living, hp);
        if (damage > 0) applyAttackDamage(living, damage);

        living.getPersistentDataContainer().set(kStatsApplied, PersistentDataType.BYTE, (byte) 1);
        plugin.getLogger().info("[Gear] mob stats applied on first hit: " + name
                + " hp=" + hp + " dmg=" + damage);
    }

    private ConfigurationSection findStats(String name) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("drops.mob-stats");
        if (root == null) return null;
        for (String key : root.getKeys(false)) {
            if (name.contains(key)) return root.getConfigurationSection(key);
        }
        return null;
    }

    private void applyMaxHealth(LivingEntity entity, double target) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        
        attr.getModifiers().stream()
                .filter(m -> m.getKey().equals(kHpMod))
                .forEach(attr::removeModifier);
        
        double base = Math.min(1024.0, attr.getBaseValue());
        attr.setBaseValue(base);
        
        double need = target - base;
        if (need > 0.5) {
            attr.addModifier(new AttributeModifier(kHpMod, need,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        
        entity.setHealth(Math.min(target, attr.getValue()));
    }

    private void applyAttackDamage(LivingEntity entity, double target) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attr == null) return;
        attr.setBaseValue(Math.min(target, 2048.0));
    }
}
