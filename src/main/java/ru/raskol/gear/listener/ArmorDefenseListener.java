package ru.raskol.gear.listener;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.raskol.gear.RaskolGear;

import java.util.HashMap;
import java.util.Map;

/**
 * Броня: суммирует резисты надетых предметов + сет-бонус (4/4)
 * и снижает входящий урон по типу (физ/маг).
 */
public final class ArmorDefenseListener implements Listener {

    private final RaskolGear plugin;
    private final NamespacedKey kType;
    private final NamespacedKey kClass;
    private final NamespacedKey kRarity;
    private final NamespacedKey kPhys;
    private final NamespacedKey kMagic;

    public ArmorDefenseListener(RaskolGear plugin) {
        this.plugin = plugin;
        kType = new NamespacedKey(plugin, "gear_type");
        kClass = new NamespacedKey(plugin, "gear_class");
        kRarity = new NamespacedKey(plugin, "gear_rarity");
        kPhys = new NamespacedKey(plugin, "phys_resist");
        kMagic = new NamespacedKey(plugin, "magic_resist");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double phys = 0.0;
        double magic = 0.0;
        Map<String, Integer> setCount = new HashMap<>();

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null) continue;
            ItemMeta meta = armor.getItemMeta();
            if (meta == null) continue;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (!"ARMOR".equals(pdc.get(kType, PersistentDataType.STRING))) continue;

            phys += pdc.getOrDefault(kPhys, PersistentDataType.DOUBLE, 0.0);
            magic += pdc.getOrDefault(kMagic, PersistentDataType.DOUBLE, 0.0);

            String cls = pdc.get(kClass, PersistentDataType.STRING);
            String rar = pdc.get(kRarity, PersistentDataType.STRING);
            if (cls != null && rar != null) {
                setCount.merge(cls + ":" + rar, 1, Integer::sum);
            }
        }

        // Сет-бонус: все 4 предмета одного сета (класс + редкость)
        for (Map.Entry<String, Integer> entry : setCount.entrySet()) {
            if (entry.getValue() < 4) continue;
            String[] parts = entry.getKey().split(":");
            ConfigurationSection bonus = plugin.getConfig()
                    .getConfigurationSection("armor." + parts[0] + "." + parts[1] + ".set-bonus");
            if (bonus == null) continue;
            phys += bonus.getDouble("phys-resist");
            magic += bonus.getDouble("magic-resist");
            if (plugin.getConfig().getBoolean("combat.debug", false)) {
                plugin.getLogger().info("[Gear] set bonus active: " + entry.getKey()
                        + " for " + player.getName());
            }
        }

        double cap = plugin.getConfig().getDouble("armor.resist-cap", 75.0);
        double resist = isMagicCause(event.getCause()) ? magic : phys;
        resist = Math.min(resist, cap);
        if (resist <= 0.0) return;

        double raw = event.getDamage();
        event.setDamage(raw * (1.0 - resist / 100.0));

        if (plugin.getConfig().getBoolean("combat.debug", false)) {
            plugin.getLogger().info("[Gear] armor: " + player.getName()
                    + " cause=" + event.getCause() + " resist=" + resist
                    + "% raw=" + raw + " final=" + event.getDamage());
        }
    }

    private boolean isMagicCause(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.MAGIC
                || cause == EntityDamageEvent.DamageCause.POISON
                || cause == EntityDamageEvent.DamageCause.WITHER
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.DRAGON_BREATH
                || cause == EntityDamageEvent.DamageCause.LIGHTNING;
    }
}
