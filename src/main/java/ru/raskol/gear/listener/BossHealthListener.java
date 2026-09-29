package ru.raskol.gear.listener;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.raskol.gear.RaskolGear;

/**
 * Виртуальное HP боссов: вычитает урон из PDC-тега.
 * Пока виртуальное HP > 0 — моб не умирает, обновляется nametag.
 * Когда HP <= 0 — mob умирает нормально, срабатывает дроп.
 */
public final class BossHealthListener implements Listener {

    private final RaskolGear plugin;
    private final NamespacedKey kRealHp;
    private final NamespacedKey kBaseName;

    public BossHealthListener(RaskolGear plugin) {
        this.plugin = plugin;
        kRealHp = new NamespacedKey(plugin, "real_hp");
        kBaseName = new NamespacedKey(plugin, "base_name");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity living)) return;

        PersistentDataContainer pdc = living.getPersistentDataContainer();
        Double realHp = pdc.get(kRealHp, PersistentDataType.DOUBLE);
        if (realHp == null) return; // не наш босс с виртуальным HP

        double damage = event.getDamage();
        double newHp = realHp - damage;

        if (newHp > 0) {
            // HP ещё есть — обновляем PDC и nametag, НЕ даём умереть
            pdc.set(kRealHp, PersistentDataType.DOUBLE, newHp);
            updateNametag(living, newHp);
            
            // Отменяем ванильный урон и ставим HP = max, чтобы не сдох
            event.setDamage(0.0);
            living.setHealth(living.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        } else {
            // HP <= 0 — даём умереть, очищаем PDC
            pdc.remove(kRealHp);
            pdc.remove(kBaseName);
            // Не трогаем урон — пусть сработает обычная логика смерти и дропа
        }
    }

    private void updateNametag(LivingEntity entity, double currentHp) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String baseName = pdc.get(kBaseName, PersistentDataType.STRING);
        if (baseName == null) return;
        
        double maxHp = getMaxHp(baseName);
        double percent = maxHp > 0 ? currentHp / maxHp * 100.0 : 0;
        
        // Цвет по % HP: зелёный > жёлтый > красный
        String color = percent > 66 ? "&a" : (percent > 33 ? "&e" : "&c");
        
        String nametag = "&6" + baseName + " " + color + "[" + fmt(currentHp) + "/" + fmt(maxHp) + "]";
        entity.setCustomName(ChatColor.translateAlternateColorCodes('&', nametag));
        entity.setCustomNameVisible(true);
    }

    private double getMaxHp(String name) {
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("drops.mob-stats");
        if (section == null) return 1024.0;
        for (String key : section.getKeys(false)) {
            if (name.contains(key)) {
                ConfigurationSection mob = section.getConfigurationSection(key);
                if (mob != null) return mob.getDouble("hp", 1024.0);
            }
        }
        return 1024.0;
    }

    private static String fmt(double v) {
        if (v >= 1000) return String.format("%.1fk", v / 1000.0);
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
