package me.siwannie.skywars;

import org.bukkit.Location;
import java.util.*;

public class IslandMapper {

    private Map<Location, List<Location>> islandMap = new HashMap<>();

    public void mapChestsToSpawns(List<Location> spawnPoints, List<Location> allChestLocations) {
        islandMap.clear();

        for (Location spawn : spawnPoints) {
            islandMap.put(spawn, new ArrayList<>());
        }

        int radius = 9;

        for (Location spawn : spawnPoints) {
            int minX = spawn.getBlockX() - radius;
            int maxX = spawn.getBlockX() + radius;
            int minY = spawn.getBlockY() - radius;
            int maxY = spawn.getBlockY() + radius;
            int minZ = spawn.getBlockZ() - radius;
            int maxZ = spawn.getBlockZ() + radius;

            for (Location chestLoc : allChestLocations) {
                if (!chestLoc.getWorld().equals(spawn.getWorld())) continue;

                int cx = chestLoc.getBlockX();
                int cy = chestLoc.getBlockY();
                int cz = chestLoc.getBlockZ();

                if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY && cz >= minZ && cz <= maxZ) {
                    islandMap.get(spawn).add(chestLoc);
                }
            }
        }
    }

    public Map<Location, List<Location>> getIslandMap() {
        return islandMap;
    }
}