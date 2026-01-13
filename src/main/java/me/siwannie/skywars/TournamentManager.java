package me.siwannie.skywars;

import com.google.common.collect.Lists;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class TournamentManager {

    public enum TournamentMode {
        POINTS,
        ELIMINATION
    }

    private final Skywars plugin;
    private final ArenaManager arenaManager;
    private final LootManager lootManager;
    private final StatsManager statsManager;

    private TournamentState state;
    private TournamentMode mode = TournamentMode.POINTS;

    private final Set<UUID> queuedPlayers;
    private final Set<UUID> allParticipants;
    private final Set<UUID> eliminatedPlayers;

    private final Map<UUID, Integer> tournamentScores = new HashMap<>();
    private final List<Map.Entry<UUID, Integer>> cachedRankings = new ArrayList<>();

    private final Map<Integer, List<UUID>> bracketResults;

    private List<List<UUID>> brackets;
    private final Queue<List<UUID>> pendingGroups;
    private final List<Match> activeMatches;

    private final int maxPlayers;
    private final int groupSize;
    private final int maxConcurrentMatches;

    private final File activeTournamentFile;
    private final FileConfiguration activeTournamentConfig;

    private int nextMatchId = 1;
    private boolean roundFinishedMessageSent = false;

    public TournamentManager(Skywars plugin) {
        this.plugin = plugin;
        this.arenaManager = new ArenaManager(plugin);
        this.lootManager = new LootManager(plugin, new IslandMapper());
        this.statsManager = new StatsManager(plugin);
        this.state = TournamentState.IDLE;

        this.maxConcurrentMatches = plugin.getConfig().getInt("settings.concurrent-matches", 2);
        this.maxPlayers = plugin.getConfig().getInt("settings.max-players", 144);
        this.groupSize = plugin.getConfig().getInt("settings.group-size", 12);

        this.queuedPlayers = new HashSet<>();
        this.allParticipants = new HashSet<>();
        this.eliminatedPlayers = new HashSet<>();
        this.bracketResults = new HashMap<>();
        this.brackets = new ArrayList<>();

        this.pendingGroups = new LinkedList<>();
        this.activeMatches = new ArrayList<>();

        this.activeTournamentFile = new File(plugin.getDataFolder(), "active_tournament.yml");
        if (!activeTournamentFile.exists()) {
            try { activeTournamentFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.activeTournamentConfig = YamlConfiguration.loadConfiguration(activeTournamentFile);

        restoreTournamentState();
    }

    public void setMode(TournamentMode mode) { this.mode = mode; }
    public TournamentMode getMode() { return mode; }

    public void addScore(Player p, int points) {
        if (mode == TournamentMode.POINTS) {
            tournamentScores.put(p.getUniqueId(), tournamentScores.getOrDefault(p.getUniqueId(), 0) + points);
            p.sendMessage(ChatColor.GOLD + "+" + points + " Tournament Points! (Total: " + tournamentScores.get(p.getUniqueId()) + ")");
            saveScoresAsync();
            updateRankings();
        }
    }

    private void updateRankings() {
        cachedRankings.clear();
        cachedRankings.addAll(tournamentScores.entrySet());
        cachedRankings.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
    }

    public int getPoints(UUID uuid) {
        return tournamentScores.getOrDefault(uuid, 0);
    }

    public int getPlayerRank(UUID uuid) {
        for (int i = 0; i < cachedRankings.size(); i++) {
            if (cachedRankings.get(i).getKey().equals(uuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    public String getTopPlayerName(int rank) {
        if (rank < 1 || rank > cachedRankings.size()) return "---";
        UUID uuid = cachedRankings.get(rank - 1).getKey();
        return Bukkit.getOfflinePlayer(uuid).getName();
    }

    public int getTopPlayerScore(int rank) {
        if (rank < 1 || rank > cachedRankings.size()) return 0;
        return cachedRankings.get(rank - 1).getValue();
    }

    private void saveScoresAsync() {
        for (Map.Entry<UUID, Integer> entry : tournamentScores.entrySet()) {
            activeTournamentConfig.set("scores." + entry.getKey(), entry.getValue());
        }
        try { activeTournamentConfig.save(activeTournamentFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void restoreTournamentState() {
        if (activeTournamentConfig.contains("results")) {
            plugin.getLogger().info("Found active tournament data! Restoring rankings...");

            int maxId = 0;
            for (String key : activeTournamentConfig.getConfigurationSection("results").getKeys(false)) {
                try {
                    int matchId = Integer.parseInt(key);
                    if (matchId > maxId && matchId != 999) maxId = matchId;

                    List<String> uuidStrings = activeTournamentConfig.getStringList("results." + key);
                    List<UUID> rankings = new ArrayList<>();
                    for (String s : uuidStrings) rankings.add(UUID.fromString(s));

                    bracketResults.put(matchId, rankings);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error restoring match " + key);
                }
            }
            this.nextMatchId = maxId + 1;

            if (activeTournamentConfig.contains("scores")) {
                for (String key : activeTournamentConfig.getConfigurationSection("scores").getKeys(false)) {
                    tournamentScores.put(UUID.fromString(key), activeTournamentConfig.getInt("scores." + key));
                }
                updateRankings();
            }

            if (!bracketResults.isEmpty()) {
                this.state = TournamentState.ROUND_ONE;
                plugin.getLogger().info("Restored " + bracketResults.size() + " match results. Next Match ID: " + nextMatchId);
            }
        }
    }

    public TournamentState getState() { return state; }
    public void setState(TournamentState state) { this.state = state; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public LootManager getLootManager() { return lootManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public Skywars getPlugin() { return plugin; }
    public int getQueueCount() { return queuedPlayers.size(); }

    public boolean addPlayer(Player player) {
        if (eliminatedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You have already been eliminated from this tournament!");
            return false;
        }

        if (queuedPlayers.size() >= maxPlayers) {
            player.sendMessage(ChatColor.RED + "The tournament is full! (" + queuedPlayers.size() + "/" + maxPlayers + ")");
            return false;
        }

        if (queuedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already in the queue.");
            return false;
        }

        if (state == TournamentState.QUEUE_OPEN) {
            queuedPlayers.add(player.getUniqueId());
            allParticipants.add(player.getUniqueId());
            if (!tournamentScores.containsKey(player.getUniqueId())) {
                tournamentScores.put(player.getUniqueId(), 0);
                updateRankings();
            }
            broadcastJsonJoin(player.getName(), queuedPlayers.size(), maxPlayers);
            return true;
        }
        else if (state == TournamentState.QUEUE_CLOSED || state == TournamentState.READY || state == TournamentState.ROUND_ONE) {
            boolean filled = fillVacancy(player);
            if (filled) {
                queuedPlayers.add(player.getUniqueId());
                allParticipants.add(player.getUniqueId());
                if (!tournamentScores.containsKey(player.getUniqueId())) {
                    tournamentScores.put(player.getUniqueId(), 0);
                    updateRankings();
                }
                broadcast(ChatColor.GREEN + player.getName() + " filled a vacant spot! (" + queuedPlayers.size() + "/" + maxPlayers + ")");

                if (state == TournamentState.ROUND_ONE && !activeMatches.isEmpty()) {
                    Location spec = activeMatches.get(0).getSpectatorLocation();
                    if (spec != null) {
                        setSpectatorMode(player, spec);
                        giveLobbyItems(player);
                        player.sendMessage(ChatColor.YELLOW + "You are waiting for your bracket.");
                    }
                }
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "Unable to find an empty bracket slot to fill.");
                return false;
            }
        }
        else {
            player.sendMessage(ChatColor.RED + "The queue is currently closed.");
            return false;
        }
    }

    private boolean fillVacancy(Player newPlayer) {
        for (List<UUID> group : pendingGroups) {
            for (int i = 0; i < group.size(); i++) {
                UUID id = group.get(i);
                if (!queuedPlayers.contains(id)) {
                    group.set(i, newPlayer.getUniqueId());
                    return true;
                }
            }
        }
        return false;
    }

    public void removePlayer(Player player) {
        if (queuedPlayers.remove(player.getUniqueId())) {
            allParticipants.remove(player.getUniqueId());
            broadcast(ChatColor.YELLOW + player.getName() + " left the queue. (" + queuedPlayers.size() + "/" + maxPlayers + ")");
        } else {
            player.sendMessage(ChatColor.RED + "You are not in the queue.");
        }
    }

    public void handleDisconnect(Player player) {
        if (queuedPlayers.contains(player.getUniqueId())) {
            queuedPlayers.remove(player.getUniqueId());
            TextComponent msg = new TextComponent(ChatColor.RED + player.getName() + " disconnected! Spot open! ");
            TextComponent joinBtn = new TextComponent(ChatColor.GREEN + "" + ChatColor.BOLD + "[CLICK TO FILL]");
            joinBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sw join"));
            joinBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to join!").create()));
            msg.addExtra(joinBtn);
            for (Player p : Bukkit.getOnlinePlayers()) p.spigot().sendMessage(msg);
        }
    }

    public void markEliminated(UUID uuid) {
        eliminatedPlayers.add(uuid);
        queuedPlayers.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            setSpectatorMode(p, p.getLocation());
        }
    }

    public void generateBrackets() {
        if (queuedPlayers.isEmpty()) return;

        bracketResults.clear();
        tournamentScores.clear();
        updateRankings();
        activeTournamentFile.delete();
        this.nextMatchId = 1;

        List<UUID> playerList = new ArrayList<>(queuedPlayers);
        Collections.shuffle(playerList);

        this.brackets = createBalancedGroups(playerList);

        this.state = TournamentState.READY;

        for (UUID uuid : allParticipants) {
            tournamentScores.put(uuid, 0);
        }
        updateRankings();

        broadcastHeader(ChatColor.GOLD + "Brackets Generated",
                ChatColor.AQUA + "Mode: " + mode.toString(),
                ChatColor.GRAY + "Groups prepared: " + brackets.size());
    }

    private List<List<UUID>> createBalancedGroups(List<UUID> players) {
        List<List<UUID>> groups = new ArrayList<>();
        List<List<UUID>> partitions = Lists.partition(players, groupSize);

        for (List<UUID> sublist : partitions) {
            groups.add(new ArrayList<>(sublist));
        }

        if (groups.size() > 1) {
            List<UUID> lastGroup = groups.get(groups.size() - 1);
            if (lastGroup.size() < 2) {
                List<UUID> secondLast = groups.get(groups.size() - 2);

                List<UUID> combined = new ArrayList<>(secondLast);
                combined.addAll(lastGroup);

                groups.remove(groups.size() - 1);
                groups.remove(groups.size() - 1);

                int total = combined.size();
                int splitIndex = (total / 2) + (total % 2);

                List<UUID> newGroup1 = new ArrayList<>(combined.subList(0, splitIndex));
                List<UUID> newGroup2 = new ArrayList<>(combined.subList(splitIndex, total));

                groups.add(newGroup1);
                groups.add(newGroup2);
            }
        }
        return groups;
    }

    public void startRoundOne() {
        pendingGroups.clear();
        for (List<UUID> group : brackets) {
            pendingGroups.add(new ArrayList<>(group));
        }

        this.state = TournamentState.ROUND_ONE;
        this.roundFinishedMessageSent = false;

        broadcastHeader(ChatColor.GREEN + "ROUND ONE STARTED",
                ChatColor.GRAY + "Good luck to all participants!");

        checkAndStartNextMatches();
        updateWaitingPlayers();
    }

    private void updateWaitingPlayers() {
        for (UUID uuid : queuedPlayers) {
            if (getMatch(Bukkit.getPlayer(uuid)) != null) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.setHealth(20);
                p.setFoodLevel(20);
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);

                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showPlayer(p);
                }

                giveLobbyItems(p);

                p.sendMessage(ChatColor.YELLOW + "You are waiting for your bracket.");
            }
        }
    }

    public void giveLobbyItems(Player p) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Spectate Matches " + ChatColor.GRAY + "(Right Click)");
        compass.setItemMeta(meta);
        p.getInventory().setItem(0, compass);
        p.updateInventory();
    }

    public void setSpectatorMode(Player p, Location targetLoc) {
        if (targetLoc != null) {
            p.teleport(targetLoc);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            p.setGameMode(GameMode.SPECTATOR);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            giveLobbyItems(p);
        }, 5L);
    }

    public void openSpectatorGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 18, ChatColor.DARK_AQUA + "Active Matches");

        for (Match match : activeMatches) {
            ItemStack icon = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Match #" + match.getMatchId());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Arena: " + ChatColor.YELLOW + match.getArena().getId());
            lore.add(ChatColor.GRAY + "Alive: " + ChatColor.GREEN + match.getAliveCount());
            lore.add(ChatColor.DARK_GRAY + "Click to spectate");
            meta.setLore(lore);
            icon.setItemMeta(meta);

            inv.addItem(icon);
        }

        p.openInventory(inv);
    }

    public void joinSpectator(Player p, int matchId) {
        Match current = getMatch(p);
        if (current != null && !eliminatedPlayers.contains(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "You cannot spectate while participating in a match!");
            return;
        }

        Match target = null;
        for (Match m : activeMatches) {
            if (m.getMatchId() == matchId) {
                target = m;
                break;
            }
        }

        if (target != null) {
            target.addSpectator(p);
            setSpectatorMode(p, target.getSpectatorLocation());
            p.sendMessage(ChatColor.GREEN + "You are now spectating Match #" + matchId);
            p.sendMessage(ChatColor.GRAY + "To return to lobby, type /sw lobby or wait for match end.");
        } else {
            p.sendMessage(ChatColor.RED + "That match is no longer active.");
        }
    }

    public void startRoundTwo() {
        if (mode != TournamentMode.POINTS) {
            broadcast(ChatColor.RED + "Round 2 is only for POINTS mode!");
            return;
        }

        List<UUID> validPlayers = new ArrayList<>();
        for (UUID uuid : allParticipants) {
            if (!eliminatedPlayers.contains(uuid) && Bukkit.getPlayer(uuid) != null) {
                validPlayers.add(uuid);
            }
        }

        if (validPlayers.isEmpty()) {
            broadcast(ChatColor.RED + "No players left for Round 2!");
            return;
        }

        Collections.shuffle(validPlayers);
        this.brackets = createBalancedGroups(validPlayers);

        pendingGroups.clear();
        for (List<UUID> group : brackets) {
            pendingGroups.add(new ArrayList<>(group));
        }

        this.state = TournamentState.ROUND_TWO;
        this.roundFinishedMessageSent = false;
        bracketResults.clear();

        broadcastHeader(ChatColor.GOLD + "STARTING ROUND TWO",
                ChatColor.GRAY + "Everyone has been reshuffled.",
                ChatColor.YELLOW + "Fight for points!");

        checkAndStartNextMatches();
    }

    public void startFinals() {
        List<UUID> finalists = new ArrayList<>();
        broadcast(ChatColor.YELLOW + "Calculating finalists...");

        if (mode == TournamentMode.ELIMINATION) {
            for (Map.Entry<Integer, List<UUID>> entry : bracketResults.entrySet()) {
                List<UUID> ranking = entry.getValue();
                for (UUID uuid : ranking) {
                    if (Bukkit.getPlayer(uuid) != null) {
                        finalists.add(uuid);
                        break;
                    }
                }
            }
        } else {
            List<Map.Entry<UUID, Integer>> sortedScores = new ArrayList<>(tournamentScores.entrySet());
            sortedScores.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

            int slots = groupSize;

            for (Map.Entry<UUID, Integer> entry : sortedScores) {
                if (finalists.size() >= slots) break;

                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && allParticipants.contains(entry.getKey())) {
                    finalists.add(entry.getKey());
                }
            }
        }

        if (finalists.isEmpty()) {
            broadcast(ChatColor.RED + "No finalists found!");
            return;
        }

        if (finalists.size() < 2) {
            broadcast(ChatColor.RED + "Not enough finalists (" + finalists.size() + ") to start a match.");
            return;
        }

        List<String> names = finalists.stream()
                .map(uuid -> Bukkit.getPlayer(uuid).getName())
                .collect(Collectors.toList());

        broadcast(ChatColor.AQUA + "Finalists: " + ChatColor.YELLOW + String.join(", ", names));

        saveTournamentHistory();

        Arena freeArena = arenaManager.getFreeArena();
        if (freeArena == null) {
            broadcast(ChatColor.RED + "No arena available for Finals! Wait for existing matches to end.");
            return;
        }
        Match finalMatch = new Match(this, freeArena, finalists, 999);
        activeMatches.add(finalMatch);
        finalMatch.start();
        this.state = TournamentState.FINALS;
        this.roundFinishedMessageSent = false;

        broadcastHeader(ChatColor.LIGHT_PURPLE + "THE FINALS HAVE STARTED");
    }

    private void saveTournamentHistory() {
        try {
            File folder = new File(plugin.getDataFolder(), "tournaments");
            if (!folder.exists()) folder.mkdirs();

            String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date());
            File file = new File(folder, "tournament_" + date + ".yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            for (Map.Entry<Integer, List<UUID>> entry : bracketResults.entrySet()) {
                List<String> names = new ArrayList<>();
                for (UUID uuid : entry.getValue()) {
                    names.add(plugin.getServer().getOfflinePlayer(uuid).getName());
                }
                config.set("brackets.match_" + entry.getKey(), names);
            }

            if (mode == TournamentMode.POINTS) {
                for (Map.Entry<UUID, Integer> entry : tournamentScores.entrySet()) {
                    config.set("scores." + plugin.getServer().getOfflinePlayer(entry.getKey()).getName(), entry.getValue());
                }
            }

            config.save(file);
            plugin.getLogger().info("Saved tournament history to " + file.getName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkAndStartNextMatches() {
        while (activeMatches.size() < maxConcurrentMatches && !pendingGroups.isEmpty()) {
            Arena freeArena = arenaManager.getFreeArena();
            if (freeArena == null) break;

            List<UUID> rawGroup = pendingGroups.poll();
            List<UUID> onlineGroup = new ArrayList<>();

            for (UUID uuid : rawGroup) {
                if (Bukkit.getPlayer(uuid) != null) {
                    onlineGroup.add(uuid);
                }
            }

            if (onlineGroup.size() < 2) {
                if (!onlineGroup.isEmpty()) {
                    List<UUID> recoveryGroup = new ArrayList<>(onlineGroup);
                    pendingGroups.add(recoveryGroup);
                    Bukkit.broadcastMessage(ChatColor.RED + "Re-queueing " + onlineGroup.size() + " player(s) waiting for opponent...");
                }
                continue;
            }

            int matchId = nextMatchId++;

            List<String> names = onlineGroup.stream()
                    .map(uuid -> Bukkit.getPlayer(uuid).getName())
                    .collect(Collectors.toList());

            Bukkit.broadcastMessage(ChatColor.GRAY + ">> Bracket #" + matchId + ": " + ChatColor.YELLOW + String.join(" vs ", names));

            Match match = new Match(this, freeArena, onlineGroup, matchId);
            activeMatches.add(match);
            match.start();
        }

        if (activeMatches.isEmpty() && pendingGroups.isEmpty() && !roundFinishedMessageSent) {
            roundFinishedMessageSent = true;

            if (state == TournamentState.ROUND_ONE) {
                if (mode == TournamentMode.POINTS) {
                    broadcastHeader(ChatColor.GOLD + "ROUND ONE COMPLETE",
                            ChatColor.YELLOW + "Waiting for Admin to start Round Two.");
                } else {
                    broadcastHeader(ChatColor.GOLD + "ROUND ONE COMPLETE",
                            ChatColor.YELLOW + "Waiting for Finals.");
                }
            } else if (state == TournamentState.ROUND_TWO) {
                broadcastHeader(ChatColor.GOLD + "ROUND TWO COMPLETE",
                        ChatColor.YELLOW + "Waiting for Finals (Points Calculation).");
            } else if (state == TournamentState.FINALS) {
                broadcastHeader(ChatColor.GREEN + "TOURNAMENT CONCLUDED",
                        ChatColor.AQUA + "Thank you for playing!");

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    broadcast(ChatColor.RED + "Resetting tournament...");
                    stopTournament();
                }, 200L);
            }
        }
    }

    public void onMatchEnd(Match match) {
        bracketResults.put(match.getMatchId(), match.getRankings());

        List<String> serialized = new ArrayList<>();
        for (UUID u : match.getRankings()) serialized.add(u.toString());

        activeTournamentConfig.set("results." + match.getMatchId(), serialized);
        try { activeTournamentConfig.save(activeTournamentFile); } catch (IOException e) { e.printStackTrace(); }

        if (state != TournamentState.IDLE) {
            for (UUID uuid : match.getOriginalPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    giveLobbyItems(p);
                }
            }
        }

        activeMatches.remove(match);
        checkAndStartNextMatches();
    }

    public void stopTournament() {
        pendingGroups.clear();

        for (Match match : new ArrayList<>(activeMatches)) match.terminate();
        activeMatches.clear();

        Location lobby = Bukkit.getWorlds().get(0).getSpawnLocation();

        for (UUID uuid : allParticipants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.teleport(lobby);
                p.setGameMode(GameMode.SURVIVAL);
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);
                p.setFlying(false);
                p.setAllowFlight(false);
                for (Player online : Bukkit.getOnlinePlayers()) online.showPlayer(p);
            }
        }

        queuedPlayers.clear();
        allParticipants.clear();
        eliminatedPlayers.clear();
        bracketResults.clear();
        tournamentScores.clear();
        updateRankings();
        brackets = new ArrayList<>();
        this.state = TournamentState.IDLE;
        this.roundFinishedMessageSent = false;

        activeTournamentFile.delete();

        broadcast(ChatColor.RED + "Tournament has been stopped and reset.");
    }

    public boolean redoMatch(int matchId) {
        Match targetMatch = null;
        for (Match m : activeMatches) {
            if (m.getMatchId() == matchId) {
                targetMatch = m;
                break;
            }
        }

        if (targetMatch == null) return false;

        List<UUID> newRoster = new ArrayList<>();
        for (UUID uuid : targetMatch.getOriginalPlayers()) {
            if (Bukkit.getPlayer(uuid) != null) newRoster.add(uuid);
        }

        if (newRoster.size() < 2) {
            broadcast(ChatColor.RED + "Cannot redo Match #" + matchId + ": Not enough players remaining!");
            return false;
        }

        targetMatch.terminate();
        activeMatches.remove(targetMatch);
        Arena arena = targetMatch.getArena();
        Match newMatch = new Match(this, arena, newRoster, matchId);
        activeMatches.add(newMatch);

        broadcast(ChatColor.RED + "" + ChatColor.BOLD + "Match #" + matchId + " is being restarted due to an issue!");
        Bukkit.getScheduler().runTaskLater(plugin, newMatch::start, 10L);
        return true;
    }

    public Match getMatch(Player player) {
        if (player == null) return null;
        for (Match m : activeMatches) {
            if (m.hasPlayer(player)) return m;
        }
        return null;
    }

    public Match getMatchByArena(Arena arena) {
        for (Match m : activeMatches) if (m.getArena().equals(arena)) return m;
        return null;
    }

    public void addFinalist(Player player) {
        broadcast(ChatColor.LIGHT_PURPLE + player.getName() + " has advanced to the Finals!");
    }

    public void broadcast(String message) { Bukkit.broadcastMessage(ChatColor.AQUA + "[SkyWars] " + message); }

    public void broadcastHeader(String... lines) {
        String bar = ChatColor.STRIKETHROUGH + "---------------------------------------------";
        Bukkit.broadcastMessage(ChatColor.AQUA + bar);
        for (String line : lines) {
            Bukkit.broadcastMessage(centerText(line));
        }
        Bukkit.broadcastMessage(ChatColor.AQUA + bar);
    }

    public String centerText(String text) {
        int maxWidth = 60;
        int spaces = (maxWidth - ChatColor.stripColor(text).length()) / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spaces; i++) sb.append(" ");
        sb.append(text);
        return sb.toString();
    }

    public void broadcastJsonJoin(String playerName, int current, int max) {
        TextComponent msg = new TextComponent(ChatColor.AQUA + "[SkyWars] " + ChatColor.GREEN + playerName + " joined the queue! (" + current + "/" + max + ") ");
        TextComponent btn = new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + "[CLICK TO JOIN]");
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sw join"));
        btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to join queue").create()));
        msg.addExtra(btn);
        for (Player p : Bukkit.getOnlinePlayers()) p.spigot().sendMessage(msg);
    }

    public List<Match> getActiveMatches() {
        return new ArrayList<>(activeMatches);
    }
}