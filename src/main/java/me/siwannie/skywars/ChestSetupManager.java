package me.siwannie.skywars;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChestSetupManager {

    public static Map<UUID, EditorSession> activeEditors = new HashMap<>();

    public enum ChestEditType {
        ISLAND, SEMI_MID, CENTER, REMOVE
    }

    public static class EditorSession {
        private final String arenaName;
        private final ChestEditType type;

        public EditorSession(String arenaName, ChestEditType type) {
            this.arenaName = arenaName;
            this.type = type;
        }

        public String getArenaName() { return arenaName; }
        public ChestEditType getType() { return type; }
    }
}