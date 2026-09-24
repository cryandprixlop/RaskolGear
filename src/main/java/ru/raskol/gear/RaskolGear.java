package ru.raskol.gear;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.gear.command.RgearCommand;
import ru.raskol.gear.hook.ClassesHook;
import ru.raskol.gear.item.GearFactory;
import ru.raskol.gear.listener.ArmorDefenseListener;
import ru.raskol.gear.listener.WeaponDamageListener;

public final class RaskolGear extends JavaPlugin {

    private GearFactory gearFactory;
    private ClassesHook classesHook;

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

        getLogger().info("RaskolGear v" + getDescription().getVersion()
                + " включён. Classes hook: " + (classesHook.isAvailable() ? "да" : "НЕТ")
                + ". Сетов брони: 15, сетов оружия: 15.");
    }

    @Override
    public void onDisable() {
        getLogger().info("RaskolGear выключен.");
    }

    public GearFactory getGearFactory() { return gearFactory; }
    public ClassesHook getClassesHook() { return classesHook; }
}
