package me.siwannie.skywars;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkywarsCommand implements CommandExecutor {

    private final TournamentManager manager;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public SkywarsCommand(TournamentManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /sw <join|leave|spectate|admin>");
            return true;
        }

        if (args[0].equalsIgnoreCase("join")) {
            if (!(sender instanceof Player)) return true;
            manager.addPlayer((Player) sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("leave")) {
            if (!(sender instanceof Player)) return true;
            if (manager.getState() != TournamentState.QUEUE_OPEN) {
                sender.sendMessage(ChatColor.RED + "You cannot leave the queue now! The tournament is locked/started.");
                return true;
            }
            manager.removePlayer((Player) sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("spectate") || args[0].equalsIgnoreCase("spec")) {
            if (!(sender instanceof Player)) return true;
            manager.openSpectatorGUI((Player) sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("setpos1")) {
            if (!(sender instanceof Player) || !sender.hasPermission("skywars.admin")) return true;
            Player p = (Player) sender;
            Location loc = p.getLocation().getBlock().getLocation();

            Location[] sel = selections.getOrDefault(p.getUniqueId(), new Location[2]);
            sel[0] = loc;
            selections.put(p.getUniqueId(), sel);

            p.sendMessage(ChatColor.LIGHT_PURPLE + "Position 1 set at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            return true;
        }

        if (args[0].equalsIgnoreCase("setpos2")) {
            if (!(sender instanceof Player) || !sender.hasPermission("skywars.admin")) return true;
            Player p = (Player) sender;
            Location loc = p.getLocation().getBlock().getLocation();

            Location[] sel = selections.getOrDefault(p.getUniqueId(), new Location[2]);
            sel[1] = loc;
            selections.put(p.getUniqueId(), sel);

            p.sendMessage(ChatColor.LIGHT_PURPLE + "Position 2 set at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("skywars.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /sw admin <open|close|generate|startround|stop|...>");
                return true;
            }

            String sub = args[1].toLowerCase();

            switch (sub) {
                case "open":
                    if (manager.getState() != TournamentState.IDLE && manager.getState() != TournamentState.QUEUE_CLOSED) {
                        sender.sendMessage(ChatColor.RED + "Cannot open queue! Current state: " + manager.getState().name());
                        return true;
                    }
                    manager.setState(TournamentState.QUEUE_OPEN);

                    Bukkit.broadcastMessage(ChatColor.GREEN + "========================================");
                    Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "       SKYWARS TOURNAMENT OPEN!");

                    TextComponent msg = new TextComponent("           ");
                    TextComponent btn = new TextComponent(ChatColor.GREEN + "" + ChatColor.BOLD + "[CLICK TO JOIN]");
                    btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sw join"));
                    btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to enter the queue!").create()));
                    msg.addExtra(btn);

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(ChatColor.GRAY + "       Type /sw join or click below:");
                        p.spigot().sendMessage(msg);
                        p.sendMessage(ChatColor.GREEN + "========================================");
                    }
                    break;

                case "close":
                    if (manager.getState() != TournamentState.QUEUE_OPEN) {
                        sender.sendMessage(ChatColor.RED + "Cannot close queue! It is not open. Current: " + manager.getState().name());
                        return true;
                    }
                    manager.setState(TournamentState.QUEUE_CLOSED);

                    Bukkit.broadcastMessage(ChatColor.RED + "========================================");
                    Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "       QUEUE IS NOW LOCKED");
                    Bukkit.broadcastMessage(ChatColor.GRAY + "     Please wait for brackets generation.");
                    Bukkit.broadcastMessage(ChatColor.RED + "========================================");
                    break;

                case "mode":
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /sw admin mode <points|elimination>");
                        sender.sendMessage(ChatColor.GRAY + "Current: " + manager.getMode().toString());
                        return true;
                    }
                    if (manager.getState() != TournamentState.IDLE && manager.getState() != TournamentState.QUEUE_OPEN) {
                        sender.sendMessage(ChatColor.RED + "Cannot change mode while tournament is running!");
                        return true;
                    }
                    try {
                        TournamentManager.TournamentMode newMode = TournamentManager.TournamentMode.valueOf(args[2].toUpperCase());
                        manager.setMode(newMode);
                        sender.sendMessage(ChatColor.GREEN + "Tournament Mode set to: " + newMode.toString());
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid mode. Use POINTS or ELIMINATION.");
                    }
                    break;

                case "generate":
                    if (manager.getState() != TournamentState.QUEUE_CLOSED) {
                        sender.sendMessage(ChatColor.RED + "Cannot generate brackets! Close the queue first. Current: " + manager.getState().name());
                        return true;
                    }
                    manager.generateBrackets();
                    sender.sendMessage(ChatColor.GREEN + "Brackets generated.");
                    break;

                case "startround":
                    if (manager.getState() != TournamentState.READY) {
                        sender.sendMessage(ChatColor.RED + "Cannot start round! Brackets not generated. Current: " + manager.getState().name());
                        return true;
                    }
                    manager.startRoundOne();
                    sender.sendMessage(ChatColor.GOLD + "Round One batching started!");
                    break;

                case "startround2":
                    if (manager.getState() != TournamentState.ROUND_ONE) {
                        sender.sendMessage(ChatColor.RED + "Cannot start Round 2! Must finish Round 1 first.");
                        return true;
                    }
                    manager.startRoundTwo();
                    break;

                case "startfinals":
                    if (manager.getState() != TournamentState.ROUND_ONE && manager.getState() != TournamentState.ROUND_TWO) {
                        sender.sendMessage(ChatColor.RED + "Cannot start finals yet! Finish qualifiers first.");
                        return true;
                    }
                    manager.startFinals();
                    sender.sendMessage(ChatColor.LIGHT_PURPLE + "Starting Finals!");
                    break;

                case "stop":
                    manager.stopTournament();
                    sender.sendMessage(ChatColor.RED + "Tournament stopped. Reset to IDLE.");
                    break;

                case "createarena":
                    if (args.length < 3) return true;
                    manager.getArenaManager().createArena(args[2]);
                    sender.sendMessage(ChatColor.GREEN + "Created arena: " + args[2]);
                    break;

                case "deletearena":
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /sw admin deletearena <name>");
                        return true;
                    }
                    String dName = args[2];
                    Arena toDelete = manager.getArenaManager().getArena(dName);

                    if (toDelete == null) {
                        sender.sendMessage(ChatColor.RED + "Arena '" + dName + "' not found.");
                        return true;
                    }

                    if (toDelete.isActive()) {
                        sender.sendMessage(ChatColor.RED + "Cannot delete this arena! It is currently in use.");
                        return true;
                    }

                    manager.getArenaManager().removeArena(dName);
                    sender.sendMessage(ChatColor.GREEN + "Arena '" + dName + "' has been deleted.");
                    break;

                case "setspawn":
                    if (!(sender instanceof Player)) return true;
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /sw admin setspawn <arenaName>");
                        return true;
                    }
                    Arena arena = manager.getArenaManager().getArena(args[2]);
                    if (arena == null) {
                        sender.sendMessage(ChatColor.RED + "Arena not found.");
                        return true;
                    }

                    int maxSpawns = manager.getPlugin().getConfig().getInt("settings.group-size", 12);
                    if (arena.getSpawnPoints().size() >= maxSpawns) {
                        sender.sendMessage(ChatColor.RED + "Error: This arena already has " + maxSpawns + " spawns! Use /sw admin clearspawns <name> to reset.");
                        return true;
                    }

                    arena.addSpawn(((Player) sender).getLocation());
                    manager.getArenaManager().saveArena(arena);

                    int current = arena.getSpawnPoints().size();
                    sender.sendMessage(ChatColor.GREEN + "Spawn point added for " + arena.getId() + " (" + current + "/" + maxSpawns + ")");

                    if (current == maxSpawns) {
                        sender.sendMessage(ChatColor.GOLD + "Arena " + arena.getId() + " setup is complete!");
                    }
                    break;

                case "clearspawns":
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /sw admin clearspawns <mapName>");
                        return true;
                    }
                    manager.getArenaManager().clearSpawns(args[2]);
                    sender.sendMessage(ChatColor.GREEN + "Cleared all spawn points for arena: " + args[2]);
                    break;

                case "removespawn":
                    if (args.length < 4) {
                        sender.sendMessage(ChatColor.RED + "Usage: /sw admin removespawn <number> <arenaName>");
                        return true;
                    }
                    int spawnIndex;
                    try {
                        spawnIndex = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid number.");
                        return true;
                    }
                    Arena remArena = manager.getArenaManager().getArena(args[3]);
                    if (remArena == null) {
                        sender.sendMessage(ChatColor.RED + "Arena not found.");
                        return true;
                    }
                    if (spawnIndex < 1 || spawnIndex > remArena.getSpawnPoints().size()) {
                        sender.sendMessage(ChatColor.RED + "Spawn index out of range (1-" + remArena.getSpawnPoints().size() + ").");
                        return true;
                    }
                    remArena.getSpawnPoints().remove(spawnIndex - 1);
                    manager.getArenaManager().saveArena(remArena);
                    sender.sendMessage(ChatColor.GREEN + "Removed spawn point #" + spawnIndex + " from " + remArena.getId());
                    break;

                case "setbounds":
                    if (!(sender instanceof Player)) return true;
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /sw admin setbounds <arenaName>");
                        return true;
                    }

                    Arena bArena = manager.getArenaManager().getArena(args[2]);
                    if (bArena == null) {
                        sender.sendMessage(ChatColor.RED + "Arena not found.");
                        return true;
                    }

                    Location[] bounds = selections.get(((Player)sender).getUniqueId());
                    if (bounds == null || bounds[0] == null || bounds[1] == null) {
                        sender.sendMessage(ChatColor.RED + "You must select Pos1 and Pos2 first! Use /sw setpos1 and /sw setpos2.");
                        return true;
                    }

                    bArena.setBounds(bounds[0], bounds[1]);
                    manager.getArenaManager().saveArena(bArena);
                    sender.sendMessage(ChatColor.GREEN + "Updated MAIN BOUNDARIES for " + bArena.getId());
                    break;

                case "addregion":
                    if (!(sender instanceof Player)) return true;
                    if (args.length < 4) {
                        sender.sendMessage(ChatColor.RED + "Usage: /sw admin addregion <arena> <center|semimid>");
                        return true;
                    }

                    Arena rArena = manager.getArenaManager().getArena(args[2]);
                    if (rArena == null) {
                        sender.sendMessage(ChatColor.RED + "Arena not found.");
                        return true;
                    }

                    Location[] reg = selections.get(((Player)sender).getUniqueId());
                    if (reg == null || reg[0] == null || reg[1] == null) {
                        sender.sendMessage(ChatColor.RED + "You must select Pos1 and Pos2 first! Use /sw setpos1 and /sw setpos2.");
                        return true;
                    }

                    String type = args[3].toLowerCase();
                    if (type.equals("center")) {
                        rArena.addCenterRegion(reg[0], reg[1]);
                        sender.sendMessage(ChatColor.GREEN + "Added CENTER loot region.");
                    } else if (type.equals("semimid")) {
                        rArena.addSemiMidRegion(reg[0], reg[1]);
                        sender.sendMessage(ChatColor.GREEN + "Added SEMI-MID loot region.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Invalid type. Use center or semimid.");
                        return true;
                    }

                    manager.getArenaManager().saveArena(rArena);
                    break;

                case "addchest":
                    if (!(sender instanceof Player)) return true;
                    if (args.length < 4) {
                        sender.sendMessage(ChatColor.RED + "Usage: /sw admin addchest <island|semi_mid|center|remove> <arena> toggle");
                        return true;
                    }

                    String typeStr = args[2].toUpperCase();
                    String arenaName = args[3];
                    Player p = (Player) sender;

                    ChestSetupManager.ChestEditType editType;
                    try {
                        editType = ChestSetupManager.ChestEditType.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        p.sendMessage(ChatColor.RED + "Invalid type. Use: ISLAND, SEMI_MID, CENTER, REMOVE");
                        return true;
                    }

                    if (ChestSetupManager.activeEditors.containsKey(p.getUniqueId())) {
                        ChestSetupManager.activeEditors.remove(p.getUniqueId());
                        p.sendMessage(ChatColor.GREEN + "Chest setup mode DISABLED.");
                    } else {
                        ChestSetupManager.activeEditors.put(p.getUniqueId(), new ChestSetupManager.EditorSession(arenaName, editType));
                        p.sendMessage(ChatColor.GREEN + "Chest setup mode ENABLED for " + typeStr + " in " + arenaName + ".");
                        p.sendMessage(ChatColor.GRAY + "Left-click chests to add/remove them. Run command again to exit.");
                    }
                    break;

                case "redo":
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /sw admin redo <MatchID>");
                        return true;
                    }
                    try {
                        int id = Integer.parseInt(args[2]);
                        boolean success = manager.redoMatch(id);
                        if (success) {
                            sender.sendMessage(ChatColor.GREEN + "Restarting Match #" + id + "...");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Could not find active Match #" + id);
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid ID. Please enter a number.");
                    }
                    break;

                case "win":
                    if (args.length < 3) return true;
                    Player target = Bukkit.getPlayer(args[2]);
                    Match m = manager.getMatch(target);
                    if (m != null) {
                        m.forceWin(target);
                        sender.sendMessage(ChatColor.GREEN + "Forced win.");
                    }
                    break;

                case "reload":
                    manager.getPlugin().reloadConfig();
                    manager.getArenaManager().loadArenas();
                    sender.sendMessage(ChatColor.GREEN + "Configuration and Arenas reloaded!");
                    break;

                default:
                    sender.sendMessage(ChatColor.RED + "Unknown admin action.");
            }
        }
        return true;
    }
}