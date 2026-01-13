package me.siwannie.skywars;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Skywars extends JavaPlugin {

    private TournamentManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.manager = new TournamentManager(this);

        getCommand("skywars").setExecutor(new SkywarsCommand(manager));

        getServer().getPluginManager().registerEvents(new GameListener(manager), this);
        getServer().getPluginManager().registerEvents(new SetupListener(manager), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SkywarsExpansion(manager).register();
        }

        getLogger().info("Skywars Tournament Plugin Enabled!");
    }

    @Override
    public void onDisable() {
    }

    public TournamentManager getManager() {
        return manager;
    }
}