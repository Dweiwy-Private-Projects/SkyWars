package me.siwannie.skywars;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SetupListener implements Listener {

    private final TournamentManager manager;

    public SetupListener(TournamentManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onChestInteract(PlayerInteractEvent e) {
        if (ChestSetupManager.activeEditors.isEmpty()) return;

        Player p = e.getPlayer();
        if (!ChestSetupManager.activeEditors.containsKey(p.getUniqueId())) return;

        if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST)) return;

        e.setCancelled(true);

        ChestSetupManager.EditorSession session = ChestSetupManager.activeEditors.get(p.getUniqueId());
        String arenaName = session.getArenaName();
        Arena arena = manager.getArenaManager().getArena(arenaName);

        if (arena == null) {
            p.sendMessage(ChatColor.RED + "Arena '" + arenaName + "' no longer exists!");
            ChestSetupManager.activeEditors.remove(p.getUniqueId());
            return;
        }

        if (session.getType() == ChestSetupManager.ChestEditType.REMOVE) {
            boolean removed = arena.removeChest(b.getLocation());
            if (removed) {
                p.sendMessage(ChatColor.RED + "Removed chest from " + arenaName);
            } else {
                p.sendMessage(ChatColor.YELLOW + "That chest was not registered in " + arenaName);
            }
        } else {
            String type = session.getType().toString();
            arena.addChest(b.getLocation(), type);
            p.sendMessage(ChatColor.GREEN + "Added " + type + " chest to " + arenaName);
        }

        manager.getArenaManager().saveArena(arena);
    }
}