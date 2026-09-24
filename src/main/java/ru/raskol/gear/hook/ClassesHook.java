package ru.raskol.gear.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import ru.raskol.gear.RaskolGear;

/** Чтение данных RaskolClasses через PlaceholderAPI (без жёсткой зависимости). */
public final class ClassesHook {

    private final RaskolGear plugin;

    public ClassesHook(RaskolGear plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null
                && plugin.getServer().getPluginManager().getPlugin("RaskolClasses") != null;
    }

    /** Класс игрока (WARRIOR/HUNTER/PRIEST/MAGE/ROGUE); null если не читается. */
    public String classId(Player player) {
        String raw = PlaceholderAPI.setPlaceholders(player, "%raskolclasses_class_id%");
        if (raw == null || raw.isEmpty() || raw.contains("%raskolclasses")) return null;
        return raw.trim().toUpperCase();
    }

    /** Power игрока (уровень профильного скилла); 0 если не читается. */
    public double power(Player player) {
        return parse(PlaceholderAPI.setPlaceholders(player, "%raskolclasses_skill_level%"));
    }

    /** Базовый крит класса: melee -> crit_melee, иначе crit_spell. */
    public double critBase(Player player, boolean melee) {
        return parse(PlaceholderAPI.setPlaceholders(player,
                melee ? "%raskolclasses_crit_melee%" : "%raskolclasses_crit_spell%"));
    }

    private double parse(String s) {
        if (s == null || s.contains("%")) return 0.0;
        try {
            return Double.parseDouble(s.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
