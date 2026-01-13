package me.siwannie.skywars;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import java.util.HashMap;
import java.util.Map;

public class Cuboid implements ConfigurationSerializable {
    private final String worldName;
    private final int x1, y1, z1;
    private final int x2, y2, z2;

    public Cuboid(Location l1, Location l2) {
        if (!l1.getWorld().equals(l2.getWorld())) throw new IllegalArgumentException("Locations must be in same world");
        this.worldName = l1.getWorld().getName();
        this.x1 = Math.min(l1.getBlockX(), l2.getBlockX());
        this.y1 = Math.min(l1.getBlockY(), l2.getBlockY());
        this.z1 = Math.min(l1.getBlockZ(), l2.getBlockZ());
        this.x2 = Math.max(l1.getBlockX(), l2.getBlockX());
        this.y2 = Math.max(l1.getBlockY(), l2.getBlockY());
        this.z2 = Math.max(l1.getBlockZ(), l2.getBlockZ());
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) return false;
        return loc.getBlockX() >= x1 && loc.getBlockX() <= x2 &&
                loc.getBlockY() >= y1 && loc.getBlockY() <= y2 &&
                loc.getBlockZ() >= z1 && loc.getBlockZ() <= z2;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("world", worldName);
        map.put("x1", x1); map.put("y1", y1); map.put("z1", z1);
        map.put("x2", x2); map.put("y2", y2); map.put("z2", z2);
        return map;
    }

    public static Cuboid deserialize(Map<String, Object> map) {
        return null;
    }

    public String toString() {
        return worldName + "," + x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;
    }

    public static Cuboid fromString(String s) {
        String[] p = s.split(",");
        Location l1 = new Location(org.bukkit.Bukkit.getWorld(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        Location l2 = new Location(org.bukkit.Bukkit.getWorld(p[0]), Double.parseDouble(p[4]), Double.parseDouble(p[5]), Double.parseDouble(p[6]));
        return new Cuboid(l1, l2);
    }
}