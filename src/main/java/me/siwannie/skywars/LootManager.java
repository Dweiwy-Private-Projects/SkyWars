package me.siwannie.skywars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class LootManager {

    private final Skywars plugin;
    private final IslandMapper mapper;
    private final Random random = new Random();

    public LootManager(Skywars plugin, IslandMapper mapper) {
        this.plugin = plugin;
        this.mapper = mapper;
    }

    public void fillIslands() {
        Map<Location, List<Location>> islands = mapper.getIslandMap();

        for (List<Location> islandChests : islands.values()) {
            if (islandChests.isEmpty()) continue;
            generateIslandLoot(islandChests);
        }
    }

    public void fillChest(Inventory inv, int tier) {
        inv.clear();

        String path = "loot.island";
        if (tier == 2) path = "loot.semi-mid";
        if (tier == 3) path = "loot.center";

        FileConfiguration config = plugin.getConfig();
        if (!config.contains(path)) return;

        List<String> items = new ArrayList<>(config.getStringList(path));
        if (items.isEmpty()) return;

        Collections.shuffle(items);

        int min = config.getInt("settings.chest-items.min", 4);
        int max = config.getInt("settings.chest-items.max", 8);
        if (max <= min) max = min + 1;

        int desiredItemCount = min + random.nextInt(max - min + 1);
        int itemsAdded = 0;

        for (String entry : items) {
            if (itemsAdded >= desiredItemCount) break;

            ItemStack item = parseItem(entry, tier);

            if (item != null) {
                if (isGear(item.getType()) && inv.contains(item.getType())) {
                    continue;
                }

                int slot = getRandomEmptySlot(inv);
                if (slot != -1) {
                    inv.setItem(slot, item);
                    itemsAdded++;
                }
            }
        }
    }

    private void generateIslandLoot(List<Location> chestLocations) {
        int min = plugin.getConfig().getInt("settings.chest-items.min", 5);
        int max = plugin.getConfig().getInt("settings.chest-items.max", 9);
        if (max <= min) max = min + 1;

        FileConfiguration config = plugin.getConfig();
        List<String> configItems = new ArrayList<>(config.getStringList("loot.island"));

        Set<String> assignedCategories = new HashSet<>();

        LinkedList<ItemStack> guarantees = new LinkedList<>();

        ItemStack weapon = getGuaranteedItem(ItemCategory.WEAPON);
        guarantees.add(weapon);
        assignedCategories.add(getLootCategory(weapon.getType()));

        guarantees.add(getGuaranteedItem(ItemCategory.PROJECTILE));
        guarantees.add(getGuaranteedItem(ItemCategory.BLOCK));

        List<ItemStack> armors = getDistinctArmorSet();
        for (ItemStack armor : armors) {
            guarantees.add(armor);
            assignedCategories.add(getLootCategory(armor.getType()));
        }

        Collections.shuffle(guarantees);

        List<Inventory> inventories = new ArrayList<>();
        for (Location loc : chestLocations) {
            if (loc.getBlock().getState() instanceof Chest) {
                Chest chest = (Chest) loc.getBlock().getState();
                chest.getBlockInventory().clear();
                inventories.add(chest.getBlockInventory());
            }
        }

        if (inventories.isEmpty()) return;

        int chestIndex = 0;
        while (!guarantees.isEmpty()) {
            ItemStack item = guarantees.poll();
            Inventory inv = inventories.get(chestIndex % inventories.size());
            int slot = getRandomEmptySlot(inv);
            if (slot != -1) inv.setItem(slot, item);
            chestIndex++;
        }

        for (Inventory inv : inventories) {
            int currentCount = countItems(inv);
            int targetCount = min + random.nextInt(max - min + 1);
            int needed = targetCount - currentCount;

            if (needed > 0 && !configItems.isEmpty()) {
                int attempts = 0;
                while (needed > 0 && attempts < 50) {
                    attempts++;
                    String entry = configItems.get(random.nextInt(configItems.size()));

                    ItemStack item = parseItem(entry, 1);
                    if (item != null) {
                        String category = getLootCategory(item.getType());

                        if (!category.equals("OTHER") && assignedCategories.contains(category)) {
                            continue;
                        }

                        int slot = getRandomEmptySlot(inv);
                        if (slot != -1) {
                            inv.setItem(slot, item);
                            needed--;
                            if (!category.equals("OTHER")) {
                                assignedCategories.add(category);
                            }
                        }
                    }
                }
            }
        }
    }

    private String getLootCategory(Material mat) {
        String name = mat.name();
        if (name.endsWith("_SWORD")) return "SWORD";
        if (name.endsWith("_AXE")) return "AXE";
        if (name.endsWith("_PICKAXE")) return "PICKAXE";
        if (name.endsWith("_SPADE")) return "SHOVEL";
        if (name.endsWith("_HELMET")) return "HELMET";
        if (name.endsWith("_CHESTPLATE")) return "CHESTPLATE";
        if (name.endsWith("_LEGGINGS")) return "LEGGINGS";
        if (name.endsWith("_BOOTS")) return "BOOTS";
        if (mat == Material.BOW) return "BOW";
        if (mat == Material.FLINT_AND_STEEL) return "FNS";
        if (mat == Material.WATER_BUCKET) return "WATER";
        if (mat == Material.LAVA_BUCKET) return "LAVA";
        return "OTHER";
    }

    private int countItems(Inventory inv) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) count++;
        }
        return count;
    }

    private List<ItemStack> getDistinctArmorSet() {
        List<ItemStack> armors = new ArrayList<>();
        List<Material> types = new ArrayList<>(Arrays.asList(
                Material.LEATHER_HELMET,
                Material.LEATHER_CHESTPLATE,
                Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS
        ));
        Collections.shuffle(types);

        armors.add(new ItemStack(types.get(0)));
        armors.add(new ItemStack(types.get(1)));
        armors.add(new ItemStack(types.get(2)));

        return armors;
    }

    private ItemStack getGuaranteedItem(ItemCategory category) {
        switch (category) {
            case WEAPON: return new ItemStack(Material.STONE_SWORD);
            case PROJECTILE: return new ItemStack(Material.SNOW_BALL, 16);
            case BLOCK: return new ItemStack(Material.WOOD, 16);
            default: return new ItemStack(Material.STONE);
        }
    }

    private int getRandomEmptySlot(Inventory inv) {
        List<Integer> emptySlots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                emptySlots.add(i);
            }
        }
        if (emptySlots.isEmpty()) return -1;
        return emptySlots.get(random.nextInt(emptySlots.size()));
    }

    private ItemStack parseItem(String entry, int tier) {
        try {
            String[] parts = entry.split(":");
            String matName = parts[0];

            if (matName.equalsIgnoreCase("WOOD_PLANK")) matName = "WOOD";

            int amount = Integer.parseInt(parts[1]);
            int chance = Integer.parseInt(parts[2]);

            int duration = -1;
            if (parts.length > 3) {
                try {
                    duration = Integer.parseInt(parts[3]) * 20;
                } catch (NumberFormatException ignored) {}
            }

            if (random.nextInt(100) >= chance) return null;

            if (matName.startsWith("Potion_of_")) {
                ItemStack potion = new ItemStack(Material.POTION, amount);
                PotionMeta meta = (PotionMeta) potion.getItemMeta();
                String type = matName.replace("Potion_of_", "");

                if (type.equalsIgnoreCase("Swiftness")) {
                    potion.setDurability((short) 16386);
                    if (duration > 0) meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0), true);
                } else if (type.equalsIgnoreCase("Regeneration")) {
                    potion.setDurability((short) 16385);
                    if (duration > 0) meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0), true);
                } else if (type.equalsIgnoreCase("Fire_Resistance")) {
                    potion.setDurability((short) 16387);
                    if (duration > 0) meta.addCustomEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0), true);
                } else if (type.equalsIgnoreCase("Healing")) {
                    potion.setDurability((short) 16453);
                } else if (type.equalsIgnoreCase("Poison")) {
                    potion.setDurability((short) 16388);
                    if (duration > 0) meta.addCustomEffect(new PotionEffect(PotionEffectType.POISON, duration, 0), true);
                }

                potion.setItemMeta(meta);
                return potion;
            }

            Material mat = Material.getMaterial(matName);
            if (mat == null) {
                plugin.getLogger().warning("Unknown material: " + matName);
                return null;
            }

            ItemStack item = new ItemStack(mat, amount);

            if (mat == Material.LOG || mat == Material.WOOD) {
                item.setDurability((short) random.nextInt(4));
            }

            if (isGear(mat)) {
                applyRandomEnchant(item, tier);
            }

            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid loot entry: " + entry);
            return null;
        }
    }

    private boolean isGear(Material mat) {
        String name = mat.name();
        return name.endsWith("_SWORD") || name.endsWith("_HELMET") ||
                name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") ||
                name.endsWith("_BOOTS") || name.equals("BOW");
    }

    private void applyRandomEnchant(ItemStack item, int tier) {
        double enchantChance = 0.0;
        if (tier == 1) enchantChance = 0.10;
        if (tier == 2) enchantChance = 0.30;
        if (tier == 3) enchantChance = 0.60;

        if (random.nextDouble() > enchantChance) return;

        Enchantment ench = null;
        String name = item.getType().name();

        if (name.endsWith("_SWORD")) {
            ench = Enchantment.DAMAGE_ALL;
        } else if (name.equals("BOW")) {
            ench = Enchantment.ARROW_DAMAGE;
        } else if (name.contains("_")) {
            ench = Enchantment.PROTECTION_ENVIRONMENTAL;
        }

        if (ench != null) {
            int level = 1;
            if (tier == 3 && random.nextInt(100) < 30) level = 2;
            item.addEnchantment(ench, level);
        }
    }
}