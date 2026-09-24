package ru.raskol.gear.listener;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.raskol.gear.RaskolGear;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Броня: снижение входящего урона по резистам (физ/маг) + сет-бонусы (4/4).
 * Шипы (reflect): работают ТОЛЬКО при полном сете, значение суммарное из конфига.
 */
public final class ArmorDefenseListener implements Listener {

    private final RaskolGear plugin;
    private final NamespacedKey kType;
    private final NamespacedKey kClass;
    private final NamespacedKey kRarity;
    private final NamespacedKey kPhys;
    private final NamespacedKey kMagic;
    private final Map<UUID, Long> lastReflectMsg = new HashMap<>();

    public ArmorDefenseListener(RaskolGear plugin) {
        this.plugin = plugin;
        kType = new NamespacedKey(plugin, "gear_type");
        kClass = new NamespacedKey(plugin, "gear_class");
        kRarity = new NamespacedKey(plugin, "gear_rarity");
        kPhys = new NamespacedKey(plugin, "phys_resist");
        kMagic = new NamespacedKey(plugin, "magic_resist");
    }

    /* ========== Снижение урона по резистам ========== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double phys = 0.0;
        double magic = 0.0;
        Map<String, Integer> setCount = new HashMap<>();

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            PersistentDataContainer pdc = armorPdc(armor);
            if (pdc == null) continue;
            phys += pdc.getOrDefault(kPhys, PersistentDataType.DOUBLE, 0.0);
            magic += pdc.getOrDefault(kMagic, PersistentDataType.DOUBLE, 0.0);
            String cls = pdc.get(kClass, PersistentDataType.STRING);
            String rar = pdc.get(kRarity, PersistentDataType.STRING);
            if (cls != null && rar != null) setCount.merge(cls + ":" + rar, 1, Integer::sum);
        }

        // Сет-бонус при 4/4
        for (Map.Entry<String, Integer> entry : setCount.entrySet()) {
            if (entry.getValue() < 4) continue;
            String[] parts = entry.getKey().split(":");
            ConfigurationSection bonus = plugin.getConfig()
                    .getConfigurationSection("armor." + parts[0] + "." + parts[1] + ".set-bonus");
            if (bonus == null) continue;
            phys += bonus.getDouble("phys-resist");
            magic += bonus.getDouble("magic-resist");
            debug("[Gear] set bonus active: " + entry.getKey() + " for " + player.getName());
        }

        double cap = plugin.getConfig().getDouble("armor.resist-cap", 75.0);
        double resist = Math.min(isMagicCause(event.getCause()) ? magic : phys, cap);
        if (resist <= 0.0) return;

        double raw = event.getDamage();
        event.setDamage(raw * (1.0 - resist / 100.0));
        debug("[Gear] armor: " + player.getName() + " cause=" + event.getCause()
                + " resist=" + resist + "% raw=" + raw + " final=" + event.getDamage());
    }

    /* ========== Шипы: возврат урона при полном сете ========== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReflect(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (victim.isDead()) return;
        // урон от проков/кровотечения (CUSTOM) не отражаем — защита от цепочек
        if (event.getCause() == EntityDamageEvent.DamageCause.CUSTOM) return;

        double reflect = fullSetReflect(victim);
        if (reflect <= 0.0) return;

        LivingEntity attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;

        double back = event.getDamage() * reflect / 100.0;
        if (back <= 0.0) return;
        attacker.damage(back);

        debug("[Gear] reflect: " + victim.getName() + " -> " + attacker.getName()
                + " " + back + " (" + reflect + "%, full set)");

        long now = System.currentTimeMillis();
        Long prev = lastReflectMsg.get(victim.getUniqueId());
        if (prev == null || now - prev > 2000) {
            lastReflectMsg.put(victim.getUniqueId(), now);
            victim.sendMessage("§6🦔 Шипы дикобраза вернули атакующему §e" + fmt(back) + " §6урона!");
        }
    }

    /** Суммарный процент шипов: только если надет полный сет (4/4) с reflect в конфиге. */
    private double fullSetReflect(Player player) {
        Map<String, Integer> setCount = new HashMap<>();
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            PersistentDataContainer pdc = armorPdc(armor);
            if (pdc == null) continue;
            String cls = pdc.get(kClass, PersistentDataType.STRING);
            String rar = pdc.get(kRarity, PersistentDataType.STRING);
            if (cls != null && rar != null) setCount.merge(cls + ":" + rar, 1, Integer::sum);
        }
        double reflect = 0.0;
        for (Map.Entry<String, Integer> entry : setCount.entrySet()) {
            if (entry.getValue() < 4) continue;
            String[] parts = entry.getKey().split(":");
            reflect = Math.max(reflect, plugin.getConfig()
                    .getDouble("armor." + parts[0] + "." + parts[1] + ".reflect", 0.0));
        }
        double cap = plugin.getConfig().getDouble("armor.reflect-cap", 25.0);
        return Math.min(reflect, cap);
    }

    /* ========== Вспомогательные ========== */

    private PersistentDataContainer armorPdc(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!"ARMOR".equals(pdc.get(kType, PersistentDataType.STRING))) return null;
        return pdc;
    }

    private LivingEntity resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity le) return le;
        if (event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof LivingEntity le) return le;
        return null;
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

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("combat.debug", false)) {
            plugin.getLogger().info(message);
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
