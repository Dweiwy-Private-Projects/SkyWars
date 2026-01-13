package me.siwannie.skywars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public class GameListener implements Listener {

    private final TournamentManager manager;

    public GameListener(TournamentManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        Match match = manager.getMatch(player);
        if (match != null) {
            if (match.getArena().getPos1() != null && !player.getWorld().getName().equals(match.getArena().getPos1().getWorld().getName())) {
                return;
            }

            Location to = event.getTo();
            Location from = event.getFrom();

            if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) return;

            Arena arena = match.getArena();
            if (!arena.isInside(to)) {
                if (to.getY() < from.getY()) return;

                event.setTo(from);
                player.sendMessage(ChatColor.RED + "You cannot leave the arena boundary!");
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        Match match = manager.getMatch(victim);
        if (match == null) {
            if (!victim.isOp()) event.setCancelled(true);
            return;
        }

        if (match.isFrozen()) {
            event.setCancelled(true);
            return;
        }

        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            match.eliminate(victim);
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker == null) return;

        Match victimMatch = manager.getMatch(victim);
        Match attackerMatch = manager.getMatch(attacker);

        if (victimMatch == null || attackerMatch == null) {
            event.setCancelled(true);
            return;
        }

        if (victimMatch != attackerMatch) {
            event.setCancelled(true);
            return;
        }

        if (victimMatch.isFrozen()) {
            event.setCancelled(true);
            return;
        }

        victimMatch.recordHit(victim, attacker);
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Match match = manager.getMatch(player);
        if (match != null) {
            player.sendMessage(ChatColor.RED + "You were in a match but disconnected. You have been eliminated.");
            match.eliminate(player);
            return;
        }

        TournamentState state = manager.getState();
        if (state != TournamentState.IDLE && state != TournamentState.QUEUE_OPEN) {
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            manager.giveLobbyItems(player);
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.sendMessage(ChatColor.YELLOW + "A tournament is in progress. You can spectate active matches.");
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            player.setAllowFlight(false);
            player.setFlying(false);
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Match match = manager.getMatch(player);
        if (match != null) {
            match.handleQuit(player);
        } else {
            manager.handleDisconnect(player);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getItem() != null && event.getItem().getType() == Material.COMPASS) {
                manager.openSpectatorGUI(player);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onSpectatorMenuClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.DARK_AQUA + "Active Matches")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();

            if (clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                if (name.startsWith("Match #")) {
                    try {
                        int matchId = Integer.parseInt(name.replace("Match #", ""));
                        manager.joinSpectator(player, matchId);
                        player.closeInventory();
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        Match match = manager.getMatch(player);
        if (match != null && match.getArena().isInside(event.getBlockClicked().getLocation())) {
            Block block = event.getBlockClicked().getRelative(event.getBlockFace());
            match.recordBlockChange(block.getState());
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        Match match = manager.getMatch(player);
        if (match != null && match.getArena().isInside(event.getBlockClicked().getLocation())) {
            match.recordBlockChange(event.getBlockClicked().getState());
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockForm(BlockFormEvent event) {
        Arena arena = manager.getArenaManager().getArenaAtLocation(event.getBlock().getLocation());
        if (arena == null) return;

        Match match = manager.getMatchByArena(arena);
        if (match != null && !match.isFrozen()) {
            match.recordBlockChange(event.getBlock().getState());
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        Arena arena = manager.getArenaManager().getArenaAtLocation(event.getBlock().getLocation());
        if (arena == null) return;

        Match match = manager.getMatchByArena(arena);
        if (match != null && !match.isFrozen()) {
            match.recordBlockChange(event.getBlock().getState());
        }
    }

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        Arena arena = manager.getArenaManager().getArenaAtLocation(event.getBlock().getLocation());
        if (arena == null) return;

        Match match = manager.getMatchByArena(arena);
        if (match != null && !match.isFrozen()) {
            match.recordBlockChange(event.getBlock().getState());
        }
    }

    @EventHandler
    public void onBlockFade(BlockFadeEvent event) {
        Arena arena = manager.getArenaManager().getArenaAtLocation(event.getBlock().getLocation());
        if (arena == null) return;

        Match match = manager.getMatchByArena(arena);
        if (match != null && !match.isFrozen()) {
            match.recordBlockChange(event.getBlock().getState());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        Match match = manager.getMatch(player);
        if (match == null) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot break blocks in the lobby.");
        } else {
            if (match.isFrozen()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Wait for the game to start!");
                return;
            }
            match.recordBlockChange(event.getBlock().getState());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        Match match = manager.getMatch(player);
        if (match == null) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot place blocks in the lobby.");
        } else {
            if (match.isFrozen()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Wait for the game to start!");
                return;
            }

            if (event.getBlock().getType() == Material.TNT) {
                event.getBlock().setType(Material.AIR);
                Location loc = event.getBlock().getLocation().add(0.5, 0.25, 0.5);
                loc.getWorld().spawn(loc, TNTPrimed.class);
                match.recordBlockChange(event.getBlockReplacedState());
            } else {
                match.recordBlockChange(event.getBlockReplacedState());
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();

            Arena arena = manager.getArenaManager().getArenaAtLocation(b.getLocation());
            if (arena == null) {
                it.remove();
                continue;
            }

            Match match = manager.getMatchByArena(arena);
            if (match == null) {
                it.remove();
                continue;
            }

            match.recordBlockChange(b.getState());
        }
    }

    @EventHandler
    public void onEnchantOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.ENCHANTING) {
            ItemStack lapis = new ItemStack(Material.INK_SACK, 64, (short) 4);
            event.getInventory().setItem(1, lapis);
        }
    }

    @EventHandler
    public void onEnchantClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() == InventoryType.ENCHANTING) {
            event.getInventory().setItem(1, null);
        }
    }

    @EventHandler
    public void onEnchantClick(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.ENCHANTING) {
            if (event.getRawSlot() == 1) {
                event.setCancelled(true);
            }
        }
    }
}