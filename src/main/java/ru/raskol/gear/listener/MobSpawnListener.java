package ru.raskol.gear.listener;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import ru.raskol.gear.RaskolGear;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ставит виртуальное HP в PDC (обход ванильного cap 1024).
 * Применяется при первом ударе по мобу.
 */
public final class MobSpawnListener implements Listener {

    private static final Pattern LEVEL_PATTERN = Pattern.compile("(\\d+)\\s*ур");

    private final RaskolGear plugin;
    private final NamespacedKey kStatsApplied;
    private final NamespacedKey kRealHp;
    private final NamespacedKey kBaseName;

    public MobSpawnListener(RaskolGear plugin) {
        this.plugin = plugin;
        kStatsApplied = new NamespacedKey(plugin, "mob_stats_applied");
        kRealHp = new NamespacedKey(plugin, "real_hp");
        kBaseName = new NamespacedKey(plugin, "base_name");
    }

    public static Integer parseLevel(String strippedName) {
        if (strippedName == null) return null;
        Matcher m = LEVEL_PATTERN.matcher(strippedName);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

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

        // Виртуальное HP в PDC (обход cap 1024)
        if (hp > 0) {
            living.getPersistentDataContainer().set(kRealHp, PersistentDataType.DOUBLE, hp);
            living.getPersistentDataContainer().set(kBaseName, PersistentDataType.STRING, name);
            plugin.getLogger().info("[Gear] boss virtual HP set: " + name + " -> " + hp);
        }
        
        // Урон через атрибут (на него cap 2048, но нам хватает)
        if (damage > 0) {
            AttributeInstance attr = living.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (attr != null) {
                attr.setBaseValue(Math.min(damage, 2048.0));
            }
        }

        living.getPersistentDataContainer().set(kStatsApplied, PersistentDataType.BYTE, (byte) 1);
    }

    private ConfigurationSection findStats(String name) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("drops.mob-stats");
        if (root == null) return null;
        for (String key : root.getKeys(false)) {
            if (name.contains(key)) return root.getConfigurationSection(key);
        }
        return null;
    }
}
