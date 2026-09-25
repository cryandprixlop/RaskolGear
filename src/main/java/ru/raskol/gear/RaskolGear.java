package ru.raskol.gear;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.gear.command.RgearCommand;
import ru.raskol.gear.hook.ClassesHook;
import ru.raskol.gear.item.GearFactory;
import ru.raskol.gear.listener.ArmorDefenseListener;
import ru.raskol.gear.listener.CraftListener;
import ru.raskol.gear.listener.WeaponDamageListener;

public final class RaskolGear extends JavaPlugin {

    private GearFactory gearFactory;
    private ClassesHook classesHook;
    private CraftListener craftListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gearFactory = new GearFactory(this);
        classesHook = new ClassesHook(this);

        PluginCommand cmd = getCommand("rgear");
        if (cmd != null) {
            RgearCommand executor = new RgearCommand(this, gearFactory);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(
                new WeaponDamageListener(this, classesHook), this);
        getServer().getPluginManager().registerEvents(
                new ArmorDefenseListener(this), this);

        craftListener = new CraftListener(this, gearFactory);
        getServer().getPluginManager().registerEvents(craftListener, this);
        craftListener.registerRecipes();

        // Динамический подсчёт: сколько реально секций в конфиге
        int weaponSets = countSections("weapons");
        int armorSets = countSections("armor");

        getLogger().info("RaskolGear v" + getDescription().getVersion()
                + " включён. Classes hook: " + (classesHook.isAvailable() ? "да" : "НЕТ")
                + ". Сетов брони: " + armorSets
                + ", сетов оружия: " + weaponSets
                + ", крафт: активен.");
    }

    /** Считает реальные сеты: для COMMON/RARE/EPIC — 1 секция = 1 сет,
     *  для LEGENDARY — каждая подсекция (porcupine, dragon...) = отдельный сет. */
    private int countSections(String kind) {
        org.bukkit.configuration.ConfigurationSection root =
                getConfig().getConfigurationSection(kind);
        if (root == null) return 0;
        int count = 0;
        for (String cls : root.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection clsSec =
                    root.getConfigurationSection(cls);
            if (clsSec == null) continue;
            for (String rar : clsSec.getKeys(false)) {
                if ("LEGENDARY".equals(rar)) {
                    org.bukkit.configuration.ConfigurationSection leg =
                            clsSec.getConfigurationSection(rar);
                    if (leg != null) count += leg.getKeys(false).size();
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public void onDisable() {
        getLogger().info("RaskolGear выключен.");
    }

    public GearFactory getGearFactory() { return gearFactory; }
    public ClassesHook getClassesHook() { return classesHook; }
}
