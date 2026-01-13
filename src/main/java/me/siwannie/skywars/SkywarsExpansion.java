package me.siwannie.skywars;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class SkywarsExpansion extends PlaceholderExpansion {

    private final TournamentManager manager;

    public SkywarsExpansion(TournamentManager manager) {
        this.manager = manager;
    }

    @Override
    public String getIdentifier() {
        return "skywars";
    }

    @Override
    public String getAuthor() {
        return "Siwannie";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {

        if (identifier.equals("points")) {
            if (player == null) return "0";
            return String.valueOf(manager.getPoints(player.getUniqueId()));
        }

        if (identifier.equals("rank")) {
            if (player == null) return "---";
            int rank = manager.getPlayerRank(player.getUniqueId());
            if (rank == -1) return "Unranked";
            return String.valueOf(rank);
        }

        if (identifier.startsWith("top_name_")) {
            try {
                int rank = Integer.parseInt(identifier.replace("top_name_", ""));
                return manager.getTopPlayerName(rank);
            } catch (NumberFormatException e) {
                return "Invalid Rank";
            }
        }

        if (identifier.startsWith("top_score_")) {
            try {
                int rank = Integer.parseInt(identifier.replace("top_score_", ""));
                return String.valueOf(manager.getTopPlayerScore(rank));
            } catch (NumberFormatException e) {
                return "0";
            }
        }

        if (player == null) return "";

        Match match = manager.getMatch(player);

        if (identifier.equals("queued")) {
            return String.valueOf(manager.getQueueCount());
        }
        if (identifier.equals("global_state")) {
            return manager.getState().toString();
        }

        if (identifier.equals("status")) {
            if (match != null) return "Ingame";
            return "Lobby";
        }

        if (identifier.equals("alive")) {
            if (match != null) {
                return String.valueOf(match.getAliveCount());
            }
            return "---";
        }

        if (identifier.equals("arena")) {
            if (match != null) {
                return match.getArena().getId();
            }
            return "Lobby";
        }

        if (identifier.equals("kills")) {
            return String.valueOf(manager.getStatsManager().getKills(player));
        }

        return null;
    }
}