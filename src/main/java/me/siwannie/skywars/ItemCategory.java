package me.siwannie.skywars;

import org.bukkit.Material;

public enum ItemCategory {
    WEAPON, ARMOR, PROJECTILE, BLOCK, OTHER;

    public static ItemCategory fromMaterial(Material mat) {
        String name = mat.name();

        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("BOW")) {
            return WEAPON;
        }
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
                name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            return ARMOR;
        }
        if (name.equals("SNOW_BALL") || name.equals("EGG") || name.equals("FISHING_ROD") || name.equals("ARROW")) {
            return PROJECTILE;
        }
        if (mat.isBlock() && !name.contains("CHEST") && !name.contains("TNT")) {
            return BLOCK;
        }
        return OTHER;
    }
}
