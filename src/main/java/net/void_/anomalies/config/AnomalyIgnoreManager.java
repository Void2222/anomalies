package net.void_.anomalies.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AnomalyIgnoreManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = FMLPaths.CONFIGDIR.get().resolve("anomalies_ignored_players.json");
    private static final Set<UUID> IGNORED_PLAYERS = new HashSet<>();
    private static boolean loaded = false;

    private static void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    public static boolean isIgnored(Player player) {
        if (player == null) return false;
        return isIgnored(player.getUUID());
    }

    public static boolean isIgnored(UUID playerUuid) {
        ensureLoaded();
        return IGNORED_PLAYERS.contains(playerUuid);
    }

    public static void setIgnored(UUID playerUuid, boolean ignore) {
        ensureLoaded();
        if (ignore) {
            IGNORED_PLAYERS.add(playerUuid);
        } else {
            IGNORED_PLAYERS.remove(playerUuid);
        }
        save();
    }

    private static void load() {
        if (!Files.exists(FILE_PATH)) return;
        try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
            Type type = new TypeToken<Set<UUID>>(){}.getType();
            Set<UUID> loadedSet = GSON.fromJson(reader, type);
            if (loadedSet != null) {
                IGNORED_PLAYERS.clear();
                IGNORED_PLAYERS.addAll(loadedSet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(IGNORED_PLAYERS, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}