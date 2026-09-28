package ru.raskol.gear.listener;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.raskol.gear.RaskolGear;
import ru.raskol.gear.hook.ClassesHook;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Бой: класс-лок (отключаемый), урон (base+Power*coeff)*multiplier, криты, капы, проки. */
public final class WeaponDamageListener implements Listener {

    private final RaskolGear plugin;
    private final ClassesHook classes;
    private final NamespacedKey kType;
    private final NamespacedKey kClass;
    private final NamespacedKey kDamageType;
    private final NamespacedKey kBaseDamage;
    private final NamespacedKey kPowerCoeff;
    private final NamespacedKey kCritBonus;
    private final NamespacedKey kProcChance;
    private final NamespacedKey kProcEffect;
    private final NamespacedKey kProcDuration;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public WeaponDamageListener(RaskolGear plugin, ClassesHook classes) {
        this.plugin = plugin;
        this.classes = classes;
        kType = new NamespacedKey(plugin, "gear_type");
        kClass = new NamespacedKey(plugin, "gear_class");
        kDamageType = new NamespacedKey(plugin, "damage_type");
        kBaseDamage = new NamespacedKey(plugin, "base_damage");
        kPowerCoeff = new NamespacedKey(plugin, "power_coeff");
        kCritBonus = new NamespacedKey(plugin, "crit_bonus");
        kProcChance = new NamespacedKey(plugin, "proc_chance");
        kProcEffect = new NamespacedKey(plugin, "proc_effect");
        kProcDuration = new NamespacedKey(plugin, "proc_duration");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event);
        if (attacker == null) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!"WEAPON".equals(pdc.get(kType, PersistentDataType.STRING))) return;

        String weaponClass = pdc.get(kClass, PersistentDataType.STRING);
        String playerClass = classes.classId(attacker);

        // Класс-лок (отключается в конфиге: combat.class-lock: false)
        if (plugin.getConfig().getBoolean("combat.class-lock", true)
                && playerClass != null && weaponClass != null
                && !playerClass.equals(weaponClass)) {
            event.setCancelled(true);
            debug("[Gear] cancel: class-lock " + attacker.getName()
                    + " (player=" + playerClass + ", weapon=" + weaponClass + ")");
            warn(attacker, "§cЭто оружие не для вашего класса: нужно §e" + weaponClass + "§c.");
            return;
        }

        double power = classes.power(attacker);
        double base = pdc.getOrDefault(kBaseDamage, PersistentDataType.DOUBLE, 0.0);
        double coeff = pdc.getOrDefault(kPowerCoeff, PersistentDataType.DOUBLE, 0.0);
        String damageType = pdc.getOrDefault(kDamageType, PersistentDataType.STRING, "physical");

        double finalDamage = (base + power * coeff)
                * plugin.getConfig().getDouble("combat.damage-multiplier", 1.0);

        // ПОЛ УРОНА: предмет с нулевыми статами не обнуляет бой — оставляем ванильный урон
        if (finalDamage <= 0.0) {
            debug("[Gear] skip: zero stats on weapon of " + attacker.getName()
                    + " (base=" + base + ", coeff=" + coeff + ") — vanilla damage kept");
            return;
        }

        // Крит
        double critChance = classes.critBase(attacker, "physical".equals(damageType))
                + pdc.getOrDefault(kCritBonus, PersistentDataType.DOUBLE, 0.0);
        boolean crit = ThreadLocalRandom.current().nextDouble(100.0) < critChance;
        if (crit) {
            finalDamage *= plugin.getConfig().getDouble("combat.crit-multiplier", 2.0);
            attacker.sendMessage("§6⚡ КРИТ! §e" + fmt(finalDamage) + " урона.");
        }

        // Капы
        double maxHit = plugin.getConfig().getDouble("combat.max-single-hit", 300.0);
        if (event.getEntity() instanceof Player) {
            maxHit = Math.min(maxHit, plugin.getConfig().getDouble("combat.max-single-hit-pvp", 200.0));
        }
        if (finalDamage > maxHit) {
            debug("[Gear] cap applied: " + fmt(finalDamage) + " -> " + fmt(maxHit));
            finalDamage = maxHit;
        }

        event.setDamage(finalDamage);
        debug("[Gear] hit: " + attacker.getName() + " -> " + event.getEntity().getName()
                + " weapon=" + weaponClass + " power=" + power + " base=" + base
                + " final=" + fmt(finalDamage) + (crit ? " CRIT" : ""));

        // Прок
        double procChance = pdc.getOrDefault(kProcChance, PersistentDataType.DOUBLE, 0.0);
        if (procChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) < procChance) {
            applyProc(attacker, event.getEntity(), pdc);
        }
    }

    private void applyProc(Player attacker, org.bukkit.entity.Entity victim, PersistentDataContainer pdc) {
        String effect = pdc.getOrDefault(kProcEffect, PersistentDataType.STRING, "");
        int duration = pdc.getOrDefault(kProcDuration, PersistentDataType.INTEGER, 1);
        LivingEntity living = victim instanceof LivingEntity le ? le : null;

        switch (effect) {
            case "BLEED" -> {
                if (living == null) return;
                double perSec = plugin.getConfig().getDouble("combat.bleed-damage-per-sec", 2.0);
                new BukkitRunnable() {
                    int left = duration;
                    @Override
                    public void run() {
                        if (left-- <= 0 || living.isDead()) {
                            cancel();
                            return;
                        }
                        living.damage(perSec);
                    }
                }.runTaskTimer(plugin, 20L, 20L);
                attacker.sendMessage("§c☠ Кровотечение на " + duration + " сек!");
            }
            case "STUN" -> {
                if (living == null) return;
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration * 20, 4));
                living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration * 20, 0));
                attacker.sendMessage("§e✦ Оглушение на " + duration + " сек!");
            }
            case "SLOW" -> {
                if (living == null) return;
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration * 20, 1));
                attacker.sendMessage("§b❄ Замедление на " + duration + " сек!");
            }
            case "BURN" -> {
                if (living == null) return;
                living.setFireTicks(duration * 20);
                attacker.sendMessage("§c🔥 Поджог на " + duration + " сек!");
            }
            case "POISON" -> {
                if (living == null) return;
                living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration * 20, 0));
                attacker.sendMessage("§a☣ Отравление на " + duration + " сек!");
            }
            case "HEAL" -> {
                double heal = plugin.getConfig().getDouble("combat.proc-heal", 6.0);
                attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
                attacker.sendMessage("§a✚ Свет лечит: +" + fmt(heal) + " HP.");
            }
            default -> plugin.getLogger().warning("[Gear] unknown proc effect: " + effect);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    private void warn(Player player, String message) {
        long cooldown = plugin.getConfig().getLong("combat.warn-cooldown-seconds", 3) * 1000L;
        long now = System.currentTimeMillis();
        Long prev = lastWarn.get(player.getUniqueId());
        if (prev != null && now - prev < cooldown) return;
        lastWarn.put(player.getUniqueId(), now);
        player.sendMessage(message);
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
