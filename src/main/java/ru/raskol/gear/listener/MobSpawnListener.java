package ru.raskol.gear.listener;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.raskol.gear.RaskolGear;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ставит HP и урон мобам Раскола (обход ванильного cap 1024 HP).
 * Уровень и имя читаются из кастомного имени моба: "… [5 ур.]", "☠ … [BOSS 50 ур.]".
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
    }

    /** Уровень моба из имени ("12 ур." -> 12); null если не наш моб. */
    public static Integer parseLevel(String strippedName) {
        Matcher m = LEVEL_PATTERN.matcher(strippedName);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!entity.isValid()) return;
                handle(entity);
            }
        }.runTask(plugin);
    }

    private void handle(LivingEntity entity) {
        String raw = entity.getCustomName();
        if (raw == null) return;
        String name = ChatColor.stripColor(raw);
        if (parseLevel(name) == null) return; // ванильный или чужой моб

        if (entity.getPersistentDataContainer().has(kStatsApplied, PersistentDataType.BYTE)) return;

        ConfigurationSection stats = findStats(name);
        if (stats == null) return;

        double hp = stats.getDouble("hp", 0.0);
        double damage = stats.getDouble("damage", 0.0);

        if (hp > 0) applyMaxHealth(entity, hp);
        if (damage > 0) applyAttackDamage(entity, damage);

        entity.getPersistentDataContainer().set(kStatsApplied, PersistentDataType.BYTE, (byte) 1);
        plugin.getLogger().info("[Gear] mob stats applied: " + name
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

    /** HP выше 1024: база 1024 + модификатор ADD_NUMBER на остаток. */
    private void applyMaxHealth(LivingEntity entity, double target) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(Math.min(attr.getBaseValue(), 1024.0));
        double need = target - attr.getValue();
        if (need > 0.5) {
            attr.addModifier(new AttributeModifier(kHpMod, need,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        entity.setHealth(target);
    }

    private void applyAttackDamage(LivingEntity entity, double target) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attr == null) return;
        attr.setBaseValue(Math.min(target, 2048.0));
    }
}
