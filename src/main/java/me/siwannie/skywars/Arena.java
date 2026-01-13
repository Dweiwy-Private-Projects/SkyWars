package me.siwannie.skywars;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;

public class Arena {

    private final String id;
    private final List<Location> spawnPoints;
    private Location pos1;
    private Location pos2;
    private boolean active;

    private final List<Location> islandChests;
    private final List<Location> semiMidChests;
    private final List<Location> centerChests;

    private final List<Cuboid> centerRegions;
    private final List<Cuboid> semiMidRegions;

    public Arena(String id) {
        this.id = id;
        this.spawnPoints = new ArrayList<>();
        this.centerRegions = new ArrayList<>();
        this.semiMidRegions = new ArrayList<>();

        this.islandChests = new ArrayList<>();
        this.semiMidChests = new ArrayList<>();
        this.centerChests = new ArrayList<>();

        this.active = false;
    }

    public String getId() { return id; }
    public List<Location> getSpawnPoints() { return spawnPoints; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public void addSpawn(Location loc) { spawnPoints.add(loc); }

    public void setBounds(Location p1, Location p2) {
        this.pos1 = p1;
        this.pos2 = p2;
    }

    public Location getPos1() { return pos1; }
    public Location getPos2() { return pos2; }

    public void addChest(Location loc, String type) {
        removeChest(loc);
        switch (type.toUpperCase()) {
            case "CENTER": centerChests.add(loc); break;
            case "SEMI_MID": semiMidChests.add(loc); break;
            default: islandChests.add(loc); break;
        }
    }

    public boolean removeChest(Location loc) {
        boolean r1 = islandChests.remove(loc);
        boolean r2 = semiMidChests.remove(loc);
        boolean r3 = centerChests.remove(loc);
        return r1 || r2 || r3;
    }

    public List<Location> getIslandChests() { return islandChests; }
    public List<Location> getSemiMidChests() { return semiMidChests; }
    public List<Location> getCenterChests() { return centerChests; }

    public void addCenterRegion(Location p1, Location p2) { centerRegions.add(new Cuboid(p1, p2)); }
    public void addSemiMidRegion(Location p1, Location p2) { semiMidRegions.add(new Cuboid(p1, p2)); }
    public List<Cuboid> getCenterRegions() { return centerRegions; }
    public List<Cuboid> getSemiMidRegions() { return semiMidRegions; }

    public boolean isInside(Location loc) {
        if (pos1 == null || pos2 == null) return false;

        if (loc.getWorld() == null || !loc.getWorld().getName().equals(pos1.getWorld().getName())) {
            return false;
        }

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        return loc.getX() >= minX && loc.getX() <= maxX && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }
}