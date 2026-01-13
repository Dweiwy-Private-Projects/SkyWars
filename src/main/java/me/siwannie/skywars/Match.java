package me.siwannie.skywars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Match {

    private final Arena arena;
    private final List<UUID> alivePlayers;
    private final List<UUID> originalPlayers;
    private final Set<UUID> spectators;
    private final int matchId;
    private final TournamentManager manager;
    private final List<BlockState> changedBlocks;
    private final List<Location> cageBlocks = new ArrayList<>();
    private final List<UUID> deathOrder = new ArrayList<>();
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();

    private boolean ending = false;
    private boolean frozen = false;
    private final int refillSeconds = 180;

    public Match(TournamentManager manager, Arena arena, List<UUID> players, int matchId) {
        this.manager = manager;
        this.arena = arena;
        this.matchId = matchId;
        this.alivePlayers = new ArrayList<>(players);
        this.originalPlayers = new ArrayList<>(players);
        this.spectators = new HashSet<>();
        this.changedBlocks = new ArrayList<>();
    }

    public Arena getArena() { return arena; }
    public List<UUID> getOriginalPlayers() { return new ArrayList<>(originalPlayers); }
    public int getMatchId() { return matchId; }
    public int getAliveCount() { return alivePlayers.size(); }
    public boolean isFrozen() { return frozen; }

    public List<UUID> getRankings() {
        List<UUID> rankings = new ArrayList<>();
        if (!alivePlayers.isEmpty()) {
            rankings.add(alivePlayers.get(0));
        }
        List<UUID> losers = new ArrayList<>(deathOrder);
        Collections.reverse(losers);
        rankings.addAll(losers);
        return rankings;
    }

    public void addSpectator(Player p) {
        this.spectators.add(p.getUniqueId());
    }

    public void removeSpectator(Player p) {
        spectators.remove(p.getUniqueId());
    }

    public void start() {
        arena.setActive(true);
        frozen = true;

        clearAllChests();

        buildCages();

        for (int i = 0; i < alivePlayers.size(); i++) {
            Player p = Bukkit.getPlayer(alivePlayers.get(i));
            if (p != null) {
                Location raw = arena.getSpawnPoints().get(i);
                Location spawn = new Location(raw.getWorld(), raw.getBlockX() + 0.5, raw.getBlockY() + 11, raw.getBlockZ() + 0.5, raw.getYaw(), raw.getPitch());

                p.teleport(spawn);
                p.setGameMode(GameMode.SURVIVAL);
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                p.setLevel(0);
                p.setExp(0f);

                for (UUID otherUUID : alivePlayers) {
                    Player other = Bukkit.getPlayer(otherUUID);
                    if (other != null && !other.getUniqueId().equals(p.getUniqueId())) {
                        p.showPlayer(other);
                        other.showPlayer(p);
                    }
                }
            }
        }

        new BukkitRunnable() {
            int count = 10;
            @Override
            public void run() {
                if (alivePlayers.isEmpty()) {
                    this.cancel();
                    endMatch();
                    return;
                }

                if (count > 0) {
                    ChatColor color = (count <= 3) ? ChatColor.RED : ChatColor.YELLOW;

                    for (int i = 0; i < alivePlayers.size(); i++) {
                        Player p = Bukkit.getPlayer(alivePlayers.get(i));
                        if (p != null) {
                            p.playSound(p.getLocation(), Sound.NOTE_STICKS, 1f, 2f);

                            if (count == 10 || count <= 5) {
                                sendTitle(p, color + String.valueOf(count), ChatColor.GRAY + "Get ready to fight!");
                            }
                            else {
                                sendActionBar(p, ChatColor.YELLOW + "Starting in " + color + count + "...");
                            }
                        }
                    }
                    count--;
                } else {
                    beginGame();
                    this.cancel();
                }
            }
        }.runTaskTimer(manager.getPlugin(), 0L, 20L);
    }

    private void sendTitle(Player player, String title, String subtitle) {
        try {
            Object enumTitle = getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0].getField("TITLE").get(null);
            Object chatTitle = getNMSClass("IChatBaseComponent").getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + title + "\"}");
            Object packetTitle = getNMSClass("PacketPlayOutTitle").getConstructor(getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0], getNMSClass("IChatBaseComponent")).newInstance(enumTitle, chatTitle);

            Object enumSubtitle = getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0].getField("SUBTITLE").get(null);
            Object chatSubtitle = getNMSClass("IChatBaseComponent").getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + subtitle + "\"}");
            Object packetSubtitle = getNMSClass("PacketPlayOutTitle").getConstructor(getNMSClass("PacketPlayOutTitle").getDeclaredClasses()[0], getNMSClass("IChatBaseComponent")).newInstance(enumSubtitle, chatSubtitle);

            sendPacket(player, packetTitle);
            sendPacket(player, packetSubtitle);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendActionBar(Player player, String message) {
        try {
            Object chatMsg = getNMSClass("IChatBaseComponent").getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + message + "\"}");
            Object packet = getNMSClass("PacketPlayOutChat").getConstructor(getNMSClass("IChatBaseComponent"), byte.class).newInstance(chatMsg, (byte) 2);
            sendPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPacket(Player player, Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(playerConnection, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Class<?> getNMSClass(String name) {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        try {
            return Class.forName("net.minecraft.server." + version + "." + name);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void beginGame() {
        frozen = false;
        sendMessageToMatch(ChatColor.GREEN + "" + ChatColor.BOLD + "THE CAGES HAVE OPENED! FIGHT!");
        removeCages();

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.ANVIL_LAND, 1f, 1f);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 255));
            }
        }

        fillChests(true);
        startRefillTimer();
    }

    private void buildCages() {
        Material cageMat = Material.GLASS;
        for (Location raw : arena.getSpawnPoints()) {
            Location base = new Location(raw.getWorld(), raw.getBlockX(), raw.getBlockY() + 10, raw.getBlockZ());

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    setCageBlock(base.clone().add(x, 0, z), cageMat);
                    setCageBlock(base.clone().add(x, 3, z), cageMat);
                }
            }
            for (int y = 1; y <= 2; y++) {
                setCageBlock(base.clone().add(1, y, 0), cageMat);
                setCageBlock(base.clone().add(-1, y, 0), cageMat);
                setCageBlock(base.clone().add(0, y, 1), cageMat);
                setCageBlock(base.clone().add(0, y, -1), cageMat);
                setCageBlock(base.clone().add(1, y, 1), cageMat);
                setCageBlock(base.clone().add(1, y, -1), cageMat);
                setCageBlock(base.clone().add(-1, y, 1), cageMat);
                setCageBlock(base.clone().add(-1, y, -1), cageMat);
            }
        }
    }

    private void setCageBlock(Location loc, Material mat) {
        if (loc.getBlock().getType() == Material.AIR) {
            loc.getBlock().setType(mat);
            cageBlocks.add(loc);
        }
    }

    private void removeCages() {
        for (Location loc : cageBlocks) {
            loc.getBlock().setType(Material.AIR);
        }
        cageBlocks.clear();
    }

    private void startRefillTimer() {
        new BukkitRunnable() {
            int timeLeft = refillSeconds;
            @Override
            public void run() {
                if (!arena.isActive() || alivePlayers.size() <= 1) {
                    this.cancel();
                    return;
                }
                timeLeft--;
                if (timeLeft == 60 || timeLeft == 30 || timeLeft == 10) {
                    sendMessageToMatch(ChatColor.YELLOW + "Chests refill in " + ChatColor.RED + timeLeft + "s!");
                }
                if (timeLeft <= 0) {
                    fillChests(false);
                    sendMessageToMatch(ChatColor.GREEN + "" + ChatColor.BOLD + "CHESTS HAVE BEEN REFILLED!");
                    timeLeft = refillSeconds;
                }
            }
        }.runTaskTimer(manager.getPlugin(), 20L, 20L);
    }

    public void recordHit(Player victim, Player attacker) {
        lastDamager.put(victim.getUniqueId(), attacker.getUniqueId());
        lastDamageTime.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    public void eliminate(Player victim) {
        if (!alivePlayers.contains(victim.getUniqueId())) return;

        for (ItemStack item : victim.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) victim.getWorld().dropItemNaturally(victim.getLocation(), item);
        }
        for (ItemStack item : victim.getInventory().getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) victim.getWorld().dropItemNaturally(victim.getLocation(), item);
        }

        alivePlayers.remove(victim.getUniqueId());
        deathOrder.add(victim.getUniqueId());
        spectators.add(victim.getUniqueId());

        manager.setSpectatorMode(victim, getSpectatorLocation());

        if (manager.getMode() == TournamentManager.TournamentMode.ELIMINATION) {
            manager.markEliminated(victim.getUniqueId());
        }

        Player killer = null;
        UUID killerUUID = lastDamager.get(victim.getUniqueId());
        Long time = lastDamageTime.get(victim.getUniqueId());

        if (killerUUID != null && time != null && (System.currentTimeMillis() - time) < 10000) {
            killer = Bukkit.getPlayer(killerUUID);
        }

        String msg;
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            msg = ChatColor.RED + victim.getName() + ChatColor.YELLOW + " was killed by " + ChatColor.RED + killer.getName() + ChatColor.YELLOW + "!";
            manager.getStatsManager().addKill(killer);
            manager.addScore(killer, 1);
            killer.sendMessage(ChatColor.GREEN + "+1 Kill");
        } else {
            msg = ChatColor.RED + victim.getName() + ChatColor.YELLOW + " died.";
        }
        sendMessageToMatch(msg);
        victim.sendMessage(ChatColor.RED + "You have been eliminated!");

        if (manager.getState() == TournamentState.ROUND_ONE && manager.getMode() == TournamentManager.TournamentMode.POINTS) {
            victim.sendMessage(ChatColor.YELLOW + "Wait for the second round to start. You will be reshuffled!");
        }

        checkWin();
    }

    public void forceWin(Player winner) {
        if (!alivePlayers.contains(winner.getUniqueId())) return;
        List<UUID> currentAlive = new ArrayList<>(alivePlayers);
        for (UUID uuid : currentAlive) {
            if (!uuid.equals(winner.getUniqueId())) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    eliminate(p);
                } else {
                    alivePlayers.remove(uuid);
                    spectators.add(uuid);
                    if (manager.getMode() == TournamentManager.TournamentMode.ELIMINATION) {
                        manager.markEliminated(uuid);
                    }
                }
            }
        }
        checkWin();
    }

    private void checkWin() {
        if (ending) return;
        if (alivePlayers.size() <= 1) {
            ending = true;
            Player winner = (alivePlayers.isEmpty()) ? null : Bukkit.getPlayer(alivePlayers.get(0));

            if (winner != null) {
                manager.getStatsManager().addWin(winner);
                manager.addScore(winner, 10);
                if (matchId == 999) {
                    Bukkit.broadcastMessage(ChatColor.GREEN + "=== CHAMPION: " + winner.getName() + " ===");
                    winner.getWorld().playSound(winner.getLocation(), Sound.LEVEL_UP, 1f, 1f);
                } else {
                    Bukkit.broadcastMessage(ChatColor.GOLD + winner.getName() + " won Match #" + matchId + "!");

                    if (manager.getMode() == TournamentManager.TournamentMode.ELIMINATION) {
                        manager.addFinalist(winner);
                    }
                }
                winner.setAllowFlight(true);
                winner.setFlying(true);
            } else {
                sendMessageToMatch(ChatColor.RED + "Game Over! No winner.");
            }
            new BukkitRunnable() { public void run() { endMatch(); } }.runTaskLater(manager.getPlugin(), 60L);
        }
    }

    public void terminate() {
        rollbackArena();
    }

    public void endMatch() {
        List<UUID> rankings = getRankings();
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Bracket #" + matchId + " Winners");
        lines.add(ChatColor.GRAY + "--------------------------------");

        int limit = (matchId == 999) ? 10 : 3;
        for (int i = 0; i < rankings.size() && i < limit; i++) {
            String name = Bukkit.getOfflinePlayer(rankings.get(i)).getName();
            String color = (i == 0) ? ChatColor.GREEN + "" : (i == 1) ? ChatColor.YELLOW + "" : (i == 2) ? ChatColor.RED + "" : ChatColor.GRAY + "";
            lines.add(color + "#" + (i + 1) + " " + name);
        }
        lines.add(ChatColor.GRAY + "--------------------------------");
        manager.broadcastHeader(lines.toArray(new String[0]));

        Location lobby = Bukkit.getWorlds().get(0).getSpawnLocation();

        Set<UUID> allToReset = new HashSet<>(alivePlayers);
        allToReset.addAll(spectators);

        for (UUID uuid : allToReset) resetPlayer(uuid, lobby);

        manager.onMatchEnd(this);

        rollbackArena();
    }

    private void resetPlayer(UUID uuid, Location lobby) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.teleport(lobby);
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.setFireTicks(0);
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
            p.setAllowFlight(false);
            p.setFlying(false);
            for (Player online : Bukkit.getOnlinePlayers()) online.showPlayer(p);

            manager.giveLobbyItems(p);
        }
    }

    private void fillChests(boolean initial) {
        if (initial) {
            IslandMapper mapper = new IslandMapper();
            mapper.mapChestsToSpawns(arena.getSpawnPoints(), arena.getIslandChests());
            LootManager islandLoot = new LootManager(manager.getPlugin(), mapper);
            islandLoot.fillIslands();
        } else {
            for (Location loc : arena.getIslandChests()) {
                fillChestAt(loc, 1);
            }
        }
        for (Location loc : arena.getSemiMidChests()) {
            fillChestAt(loc, 2);
        }
        for (Location loc : arena.getCenterChests()) {
            fillChestAt(loc, 3);
        }
    }

    private void fillChestAt(Location loc, int tier) {
        if (!loc.getChunk().isLoaded()) {
            loc.getChunk().load();
        }

        if (loc.getBlock().getType() != Material.CHEST && loc.getBlock().getType() != Material.TRAPPED_CHEST) {
            loc.getBlock().setType(Material.CHEST);
        }

        if (loc.getBlock().getType() == Material.CHEST || loc.getBlock().getType() == Material.TRAPPED_CHEST) {
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) loc.getBlock().getState();
            manager.getLootManager().fillChest(chest.getInventory(), tier);
        }
    }

    private void clearAllChests() {
        for (Location loc : arena.getIslandChests()) clearChestAt(loc);
        for (Location loc : arena.getSemiMidChests()) clearChestAt(loc);
        for (Location loc : arena.getCenterChests()) clearChestAt(loc);
    }

    private void clearChestAt(Location loc) {
        if (!loc.getChunk().isLoaded()) loc.getChunk().load();
        if (loc.getBlock().getType() == Material.CHEST || loc.getBlock().getType() == Material.TRAPPED_CHEST) {
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) loc.getBlock().getState();
            chest.getInventory().clear();
        }
    }

    private void clearDroppedItems() {
        if (arena.getSpawnPoints().isEmpty()) return;

        org.bukkit.World world = arena.getSpawnPoints().get(0).getWorld();

        for (org.bukkit.entity.Entity e : world.getEntities()) {
            if (e instanceof org.bukkit.entity.Item || e instanceof org.bukkit.entity.Projectile) {
                if (arena.isInside(e.getLocation())) {
                    e.remove();
                }
            }
        }
    }

    public Location getSpectatorLocation() {
        if (arena.getSpawnPoints().isEmpty()) return null;
        return arena.getSpawnPoints().get(0).clone().add(0, 20, 0);
    }

    public void recordBlockChange(BlockState oldState) {
        if (!ending) {
            changedBlocks.add(oldState);
        }
    }

    private void rollbackArena() {
        new BukkitRunnable() {
            int blocksPerTick = 1000;

            @Override
            public void run() {
                if (changedBlocks.isEmpty()) {
                    removeCages();
                    clearDroppedItems();
                    clearAllChests();

                    arena.setActive(false);

                    manager.checkAndStartNextMatches();
                    this.cancel();
                    return;
                }

                int count = 0;
                while (!changedBlocks.isEmpty() && count < blocksPerTick) {
                    int lastIndex = changedBlocks.size() - 1;
                    BlockState state = changedBlocks.get(lastIndex);

                    if (state.getWorld() != null) {
                        if (!state.getChunk().isLoaded()) {
                            state.getChunk().load();
                        }
                        state.update(true, false);
                    }
                    changedBlocks.remove(lastIndex);
                    count++;
                }
            }
        }.runTaskTimer(manager.getPlugin(), 20L, 1L);
    }

    public boolean hasPlayer(Player p) { return alivePlayers.contains(p.getUniqueId()) || spectators.contains(p.getUniqueId()); }

    public void handleQuit(Player player) {
        if (alivePlayers.contains(player.getUniqueId())) {
            eliminate(player);
        }
    }

    private void sendMessageToMatch(String msg) {
        for(UUID uuid : alivePlayers) { Player p = Bukkit.getPlayer(uuid); if(p!=null) p.sendMessage(msg); }
        for(UUID uuid : spectators) { Player p = Bukkit.getPlayer(uuid); if(p!=null) p.sendMessage(msg); }
    }
}