package ru.raskol.gear.listener;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import ru.raskol.gear.RaskolGear;
import ru.raskol.gear.item.GearFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Дроп с мобов Раскола (имя с "N ур."):
 * - фрагменты по тиру уровня (1-5 штук);
 * - с боссов (уровень >= boss-min-level или метка ☠) — 100% чертёж.
 */
public final class MobDropListener implements Listener {

    private static final String[] CLASSES = {"WARRIOR", "HUNTER", "PRIEST", "MAGE", "ROGUE"};
    private static final String[] SLOTS = {"helmet", "chestplate", "leggings", "boots"};

    private final RaskolGear plugin;
    private final GearFactory factory;
    private final Random random = new Random();

    public MobDropListener(RaskolGear plugin, GearFactory factory) {
        this.plugin = plugin;
        this.factory = factory;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (!plugin.getConfig().getBoolean("drops.enabled", true)) return;

        LivingEntity victim = event.getEntity();
        String raw = victim.getCustomName();
        if (raw == null) return;
        String name = ChatColor.stripColor(raw);

        Integer level = MobSpawnListener.parseLevel(name);
        if (level == null) return; // ванильный моб — не трогаем

        Player killer = victim.getKiller();

        int bossMin = plugin.getConfig().getInt("drops.boss-min-level", 50);
        String marker = plugin.getConfig().getString("drops.boss-marker", "☠");
        boolean boss = level >= bossMin
                || (marker != null && !marker.isEmpty() && name.contains(marker));

        if (boss) {
            ItemStack bp = rollBlueprint();
            if (bp != null) {
                event.getDrops().add(bp);
                if (killer != null) {
                    killer.sendMessage("§6★ Босс выбил чертёж: " + bp.getItemMeta().getDisplayName());
                    killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
                plugin.getLogger().info("[Gear] drop blueprint from boss " + name
                        + " lvl " + level + " (killer: " + (killer != null ? killer.getName() : "-") + ")");
            }
        } else {
            for (Map<?, ?> tier : plugin.getConfig().getMapList("drops.tiers")) {
                int min = toInt(tier.get("min-level"), 1);
                int max = toInt(tier.get("max-level"), 999);
                if (level < min || level > max) continue;

                double chance = toDouble(tier.get("fragment-chance"), 0.0);
                if (random.nextDouble(100.0) < chance) {
                    String fragTier = String.valueOf(tier.get("fragment"));
                    int minAmount = toInt(tier.get("fragment-min"), 1);
                    int maxAmount = toInt(tier.get("fragment-max"), 5);
                    int amount = minAmount + random.nextInt(maxAmount - minAmount + 1);

                    ItemStack frag = factory.createFragment(fragTier, amount);
                    if (frag != null) {
                        event.getDrops().add(frag);
                        if (killer != null) {
                            killer.sendMessage("§7⚙ Дроп: фрагмент (" + tierRu(fragTier)
                                    + ") x" + amount + " §8[моб " + level + " ур.]");
                        }
                        plugin.getLogger().info("[Gear] drop fragment: " + fragTier + " x" + amount
                                + " from " + name + " lvl " + level);
                    }
                }
                break;
            }
        }
    }

    /* ========== Генерация чертежа (100% с босса) ========== */

    private ItemStack rollBlueprint() {
        String cls = CLASSES[random.nextInt(CLASSES.length)];
        boolean weapon = random.nextBoolean();
        String rarity = rollRarity();

        String setKey = null;
        if ("LEGENDARY".equals(rarity)) {
            List<String> keys = legendaryKeys(weapon ? "weapons" : "armor", cls);
            if (keys.isEmpty()) {
                rarity = "EPIC";
            } else {
                setKey = keys.get(random.nextInt(keys.size()));
            }
        }

        String slot = null;
        if (!weapon) slot = SLOTS[random.nextInt(SLOTS.length)];

        return factory.createBlueprint(weapon ? "WEAPON" : "ARMOR", cls, rarity, slot, setKey);
    }

    private String rollRarity() {
        ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("drops.bosses.blueprint-rarities");
        if (sec == null) return "COMMON";
        double total = 0;
        for (String k : sec.getKeys(false)) total += sec.getDouble(k);
        if (total <= 0) return "COMMON";
        double roll = random.nextDouble(total);
        for (String k : sec.getKeys(false)) {
            roll -= sec.getDouble(k);
            if (roll < 0) return k;
        }
        return "COMMON";
    }

    private List<String> legendaryKeys(String kind, String cls) {
        ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection(kind + "." + cls + ".LEGENDARY");
        return sec == null ? List.of() : new ArrayList<>(sec.getKeys(false));
    }

    private String tierRu(String tier) {
        return switch (tier) {
            case "iron" -> "железный";
            case "steel" -> "стальной";
            case "mithril" -> "мифриловый";
            default -> tier;
        };
    }

    private int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private double toDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }
}
