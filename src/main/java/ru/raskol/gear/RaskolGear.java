package ru.raskol.gear;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.gear.command.RgearCommand;
import ru.raskol.gear.item.GearFactory;

public final class RaskolGear extends JavaPlugin {

    private GearFactory gearFactory;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gearFactory = new GearFactory(this);

        PluginCommand cmd = getCommand("rgear");
        if (cmd != null) {
            RgearCommand executor = new RgearCommand(this, gearFactory);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("RaskolGear v" + getDescription().getVersion()
                + " включён. Классов: 5, редкостей: 3.");
    }

    @Override
    public void onDisable() {
        getLogger().info("RaskolGear выключен.");
    }

    public GearFactory getGearFactory() {
        return gearFactory;
    }
}
