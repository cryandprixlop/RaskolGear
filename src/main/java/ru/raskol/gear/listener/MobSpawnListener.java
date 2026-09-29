package ru.raskol.gear.listener;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.raskol.gear.RaskolGear;

import java.util.Optional;
import java.util.UUID;

/**
 * Обход ванильного cap 1024 HP для MM-боссов.
 * На спавне босса добавляем AttributeModifier-ы ADD_NUMBER,
 * которые суммируются с базой и дают нужные 35k/45k HP.
 */
public final class MobSpawnListener implements Listener {

    private final RaskolGear plugin;
    private final NamespacedKey kBossHpApplied;

    public MobSpawnListener(RaskolGear plugin) {
        this.plugin = plugin;
        this.kBossHpApplied = new NamespacedKey(plugin, "boss_hp_applied");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        // Ждём 1 тик — чтобы MM успел установить свои базовые HP
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!entity.isValid()) return;
                handleSpawn(entity);
            }
        }.runTask(plugin);
    }

    private void handleSpawn(LivingEntity entity) {
        Integer level = mythicLevel(entity);
        if (level == null) return;

        String name = entity.getCustomName() == null ? "" : ChatColor.stripColor(entity.getCustomName());
        String marker = plugin.getConfig().getString("drops.boss-marker", "☠");
        int bossMinLevel = plugin.getConfig().getInt("drops.boss-min-level", 50);

        boolean boss = level >= bossMinLevel || (marker != null && !marker.isEmpty() && name.contains(marker));
        if (!boss) return;

        double targetHp = findBossTargetHp(name, level);
        applyBossHp(entity, targetHp);
    }

    private double findBossTargetHp(String name, int level) {
        var section = plugin.getConfig().getConfigurationSection("drops.boss-health");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (name.contains(key)) return section.getDouble(key);
            }
        }
        // Дефолт по уровню
        return level >= 60 ? 45000.0 : 35000.0;
    }

    private void applyBossHp(LivingEntity entity, double targetHp) {
        // Защита от повторного применения
        if (entity.getPersistentDataContainer().has(kBossHpApplied, PersistentDataType.BYTE)) return;

        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;

        double base = attr.getBaseValue(); // MM ставит максимум 1024
        double need = targetHp - base;
        if (need <= 0) return;

        // Разбиваем на модификаторы по 10000 каждый (ADD_NUMBER обходит cap)
        double perMod = 10000.0;
        int count = (int) Math.ceil(need / perMod);
        double each = need / count;

        // Снимаем все старые модификаторы с нашим префиксом (если были)
        attr.getModifiers().stream()
                .filter(m -> m.getKey().getNamespace().equals(plugin.getName().toLowerCase())
                        && m.getKey().getKey().startsWith("boss_hp_"))
                .forEach(attr::removeModifier);

        for (int i = 0; i < count; i++) {
            AttributeModifier mod = new AttributeModifier(
                    new NamespacedKey(plugin, "boss_hp_" + i),
                    each,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY);
            attr.addModifier(mod);
        }

        // Ставим текущее HP = целевое (иначе моб появится с 1024 из 35000)
        entity.setHealth(targetHp);

        entity.getPersistentDataContainer().set(kBossHpApplied, PersistentDataType.BYTE, (byte) 1);

        plugin.getLogger().info("[Gear] boss HP boosted: " + entity.getName()
                + " (base=" + base + ") -> " + targetHp + " via " + count + " modifiers");
    }

    private Integer mythicLevel(LivingEntity entity) {
        try {
            Class<?> bukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = bukkitClass.getMethod("getInstance").invoke(null);
            Object mobManager = inst.getClass().getMethod("getMobManager").invoke(inst);
            Object opt = mobManager.getClass()
                    .getMethod("getActiveMob", UUID.class)
                    .invoke(mobManager, entity.getUniqueId());
            if (!(opt instanceof Optional<?> o) || o.isEmpty()) return null;
            Object activeMob = o.get();
            Object level = activeMob.getClass().getMethod("getLevel").invoke(activeMob);
            return level instanceof Number n ? n.intValue() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
