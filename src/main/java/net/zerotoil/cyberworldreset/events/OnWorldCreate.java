package net.zerotoil.cyberworldreset.events;

import net.zerotoil.cyberworldreset.CyberWorldReset;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

public class OnWorldCreate implements Listener {

    private CyberWorldReset main;

    public OnWorldCreate(CyberWorldReset main) {
        this.main = main;
        Bukkit.getPluginManager().registerEvents(this, main);
        main.addEvent();
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onWorldCreate(WorldInitEvent event) {
        if (!main.worlds().getWorlds().containsKey(event.getWorld().getName())) {
            main.worlds().cancelTimers();
            main.loadCache();
        }
        // if server is open and world is not one that is regenerating
        if (main.onJoin().isServerOpen() && !main.onWorldChange().getClosedWorlds().contains(event.getWorld().getName())) return;
        final boolean loadSpawn = main.config().getLoadingType().matches("(?i)STANDARD");
        // System.out.println(loadSpawn);
        main.getScheduler().runTask(() -> {
            event.getWorld().setKeepSpawnInMemory(loadSpawn);
            event.getWorld().setAutoSave(loadSpawn);
        });
    }

}
