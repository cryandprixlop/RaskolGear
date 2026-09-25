package ru.raskol.gear.listener;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import ru.raskol.gear.RaskolGear;
import ru.raskol.gear.item.GearFactory;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ковка снаряжения: чертёж в центр + 8 фрагментов вокруг.
 * Успех по шансу из конфига; провал сжигает материалы.
 */
public final class CraftListener implements Listener {

    private static final String KEY_PREFIX = "gear_craft_";

    private final RaskolGear plugin;
    private final GearFactory factory;

    public CraftListener(RaskolGear plugin, GearFactory factory) {
        this.plugin = plugin;
        this.factory = factory;
    }

    /** Регистрирует ванильные рецепты для каждого тира фрагментов. */
    public void registerRecipes() {
        int count = 0;
        for (String tier : new String[]{"iron", "steel", "mithril"}) {
            Material frag = GearFactory.fragmentMaterial(tier);
            if (frag == null) continue;
            NamespacedKey key = new NamespacedKey(plugin, KEY_PREFIX + tier);
            if (plugin.getServer().getRecipe(key) != null) continue;

            ItemStack dummy = new ItemStack(Material.PAPER);
            ShapedRecipe recipe = new ShapedRecipe(key, dummy);
            recipe.shape("FFF", "FBF", "FFF");
            recipe.setIngredient('F', frag);
            recipe.setIngredient('B', Material.PAPER);
            plugin.getServer().addRecipe(recipe);
            count++;
        }
        plugin.getLogger().info("[Gear] рецептов ковки зарегистрировано: " + count);
    }

    private ShapedRecipe ours(org.bukkit.inventory.Recipe recipe) {
        if (!(recipe instanceof ShapedRecipe sr)) return null;
        String k = sr.getKey().getKey();
        if (!sr.getKey().getNamespace().equals(plugin.getName().toLowerCase())) return null;
        if (!k.startsWith(KEY_PREFIX)) return null;
        return sr;
    }

    /* ========== Превью результата ========== */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        ShapedRecipe sr = ours(event.getRecipe());
        if (sr == null) return;
        GearFactory.BlueprintData bp = validate(event.getInventory(), sr);
        if (bp == null) {
            event.getInventory().setResult(null);
            return;
        }
        event.getInventory().setResult(factory.blueprintTarget(bp));
    }

    /* ========== Сама ковка ========== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ShapedRecipe sr = ours(event.getRecipe());
        if (sr == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GearFactory.BlueprintData bp = validate(event.getInventory(), sr);
        event.setCancelled(true);
        if (bp == null) {
            player.sendMessage("§cНужен чертёж в центре и 8 фрагментов нужного тира вокруг.");
            return;
        }

        ItemStack target = factory.blueprintTarget(bp);
        if (target == null) {
            player.sendMessage("§cЧертеж повреждён: не удалось создать предмет.");
            return;
        }

        // Сжигаем материалы (центр + фрагменты)
        consume(event.getInventory());

        double chance = plugin.getConfig()
                .getDouble("crafting.success-chance." + bp.rarity, 50.0);
        boolean success = ThreadLocalRandom.current().nextDouble(100.0) < chance;

        if (success) {
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(target);
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItem(player.getLocation(), drop);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
            player.sendMessage("§a✔ Ковка удалась! §7Получено: "
                    + target.getItemMeta().getDisplayName());
            plugin.getLogger().info("[Gear] craft OK: " + player.getName() + " -> "
                    + bp.type + " " + bp.cls + " " + bp.rarity
                    + (bp.slot != null ? " " + bp.slot : ""));
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
            player.sendMessage("§c✖ Крафт провалился: материалы сгорели в горне!");
            plugin.getLogger().info("[Gear] craft FAIL: " + player.getName() + " -> "
                    + bp.type + " " + bp.cls + " " + bp.rarity
                    + (bp.slot != null ? " " + bp.slot : ""));
        }
    }

    /* ========== Проверка раскладки ========== */

    private GearFactory.BlueprintData validate(CraftingInventory inv, ShapedRecipe sr) {
        ItemStack[] matrix = inv.getMatrix();
        if (matrix.length < 9) return null;

        GearFactory.BlueprintData bp = factory.parseBlueprint(matrix[4]);
        if (bp == null) return null;

        String tier = sr.getKey().getKey().substring(KEY_PREFIX.length());
        String needTier = plugin.getConfig()
                .getString("crafting.tier-by-rarity." + bp.rarity, "iron");
        if (!needTier.equals(tier)) return null;

        int required = plugin.getConfig().getInt("crafting.fragments-required", 8);
        int count = 0;
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue;
            ItemStack s = matrix[i];
            if (s == null || s.getType() == Material.AIR) return null;
            String t = factory.parseFragment(s);
            if (t == null || !t.equals(needTier)) return null;
            count++;
        }
        if (count < required) return null;
        return bp;
    }

    private void consume(CraftingInventory inv) {
        ItemStack[] matrix = inv.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack s = matrix[i];
            if (s == null || s.getType() == Material.AIR) continue;
            if (s.getAmount() <= 1) matrix[i] = null;
            else s.setAmount(s.getAmount() - 1);
        }
        inv.setMatrix(matrix);
    }
}
