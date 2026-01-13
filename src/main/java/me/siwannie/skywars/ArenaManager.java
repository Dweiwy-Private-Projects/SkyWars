package me.siwannie.skywars;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArenaManager {

    private final Skywars plugin;
    private final List<Arena> arenas;

    public ArenaManager(Skywars plugin) {
        this.plugin = plugin;
        this.arenas = new ArrayList<>();
        loadArenas();
    }

    public void loadArenas() {
        arenas.clear();
        FileConfiguration config = plugin.getConfig();
        if (!config.isConfigurationSection("arenas")) return;

        for (String key : config.getConfigurationSection("arenas").getKeys(false)) {
            Arena arena = new Arena(key);

            List<String> locs = config.getStringList("arenas." + key + ".spawns");
            for (String s : locs) {
                arena.addSpawn(parseLocation(s));
            }

            String p1Str = config.getString("arenas." + key + ".pos1");
            String p2Str = config.getString("arenas." + key + ".pos2");
            if (p1Str != null && p2Str != null) {
                arena.setBounds(parseLocation(p1Str), parseLocation(p2Str));
            }

            if (config.contains("arenas." + key + ".chests.island")) {
                for (String s : config.getStringList("arenas." + key + ".chests.island")) {
                    arena.addChest(parseLocation(s), "ISLAND");
                }
            }
            if (config.contains("arenas." + key + ".chests.semi-mid")) {
                for (String s : config.getStringList("arenas." + key + ".chests.semi-mid")) {
                    arena.addChest(parseLocation(s), "SEMI_MID");
                }
            }
            if (config.contains("arenas." + key + ".chests.center")) {
                for (String s : config.getStringList("arenas." + key + ".chests.center")) {
                    arena.addChest(parseLocation(s), "CENTER");
                }
            }

            arenas.add(arena);
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arenas.");
    }

    public void saveArena(Arena arena) {
        String path = "arenas." + arena.getId();
        FileConfiguration config = plugin.getConfig();

        List<String> spawns = new ArrayList<>();
        for (Location loc : arena.getSpawnPoints()) spawns.add(serializeLocation(loc));
        config.set(path + ".spawns", spawns);

        if (arena.getPos1() != null && arena.getPos2() != null) {
            config.set(path + ".pos1", serializeLocation(arena.getPos1()));
            config.set(path + ".pos2", serializeLocation(arena.getPos2()));
        }

        List<String> island = new ArrayList<>();
        for (Location loc : arena.getIslandChests()) island.add(serializeLocation(loc));
        config.set(path + ".chests.island", island);

        List<String> semi = new ArrayList<>();
        for (Location loc : arena.getSemiMidChests()) semi.add(serializeLocation(loc));
        config.set(path + ".chests.semi-mid", semi);

        List<String> center = new ArrayList<>();
        for (Location loc : arena.getCenterChests()) center.add(serializeLocation(loc));
        config.set(path + ".chests.center", center);

        config.set(path + ".regions", null);

        plugin.saveConfig();
    }

    public Arena getFreeArena() {
        List<Arena> shuffled = new ArrayList<>(arenas);
        Collections.shuffle(shuffled);
        for (Arena arena : shuffled) {
            if (!arena.isActive() && !arena.getSpawnPoints().isEmpty()) return arena;
        }
        return null;
    }

    public void clearSpawns(String arenaName) {
        Arena arena = getArena(arenaName);
        if (arena != null) {
            arena.getSpawnPoints().clear();
            saveArena(arena);
        }
    }

    public Arena getArena(String id) {
        return arenas.stream().filter(a -> a.getId().equals(id)).findFirst().orElse(null);
    }

    public Arena createArena(String id) {
        Arena a = new Arena(id);
        arenas.add(a);
        return a;
    }

    public Arena getArenaAtLocation(Location loc) {
        for (Arena arena : arenas) {
            if (arena.isInside(loc)) {
                return arena;
            }
        }
        return null;
    }

    public void removeArena(String id) {
        Arena arena = getArena(id);
        if (arena != null) {
            arenas.remove(arena);
            plugin.getConfig().set("arenas." + id, null);
            plugin.saveConfig();
        }
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
    }

    private Location parseLocation(String s) {
        String[] parts = s.split(",");
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) {
            w = Bukkit.createWorld(new WorldCreator(parts[0]));
        }
        return new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
    }
}