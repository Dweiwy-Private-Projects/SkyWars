package me.siwannie.skywars;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsManager {

    private final Skywars plugin;
    private final File statsFile;
    private final FileConfiguration statsConfig;
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> wins = new HashMap<>();

    public StatsManager(Skywars plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        loadStats();
    }

    private void loadStats() {
        if (statsConfig.contains("kills")) {
            for (String key : statsConfig.getConfigurationSection("kills").getKeys(false)) {
                kills.put(UUID.fromString(key), statsConfig.getInt("kills." + key));
            }
        }
        if (statsConfig.contains("wins")) {
            for (String key : statsConfig.getConfigurationSection("wins").getKeys(false)) {
                wins.put(UUID.fromString(key), statsConfig.getInt("wins." + key));
            }
        }
    }

    public void addKill(Player player) {
        UUID id = player.getUniqueId();
        kills.put(id, kills.getOrDefault(id, 0) + 1);
        saveAsync();
    }

    public void addWin(Player player) {
        UUID id = player.getUniqueId();
        wins.put(id, wins.getOrDefault(id, 0) + 1);
        saveAsync();
    }

    public int getKills(Player player) {
        return kills.getOrDefault(player.getUniqueId(), 0);
    }

    private void saveAsync() {
        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            statsConfig.set("kills." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Integer> entry : wins.entrySet()) {
            statsConfig.set("wins." + entry.getKey().toString(), entry.getValue());
        }

        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}