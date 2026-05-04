package dev.radiationzones.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.radiationzones.RadiationZones;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ZoneManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final LevelResource DATA_FILE = new LevelResource("radiationzones.json");

    private static final Map<String, RadiationZone> ZONES = new ConcurrentHashMap<>();
    private static MinecraftServer SERVER;

    private ZoneManager() {}

    public static void setServer(MinecraftServer server) {
        SERVER = server;
    }

    public static MinecraftServer server() {
        return SERVER;
    }

    public static Collection<RadiationZone> all() {
        return Collections.unmodifiableCollection(ZONES.values());
    }

    public static Optional<RadiationZone> get(String name) {
        return Optional.ofNullable(ZONES.get(name.toLowerCase(Locale.ROOT)));
    }

    public static boolean add(RadiationZone zone) {
        String key = zone.name().toLowerCase(Locale.ROOT);
        if (ZONES.containsKey(key)) return false;
        ZONES.put(key, zone);
        save();
        return true;
    }

    public static boolean remove(String name) {
        boolean removed = ZONES.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }

    /** Returns the highest-level zone containing the entity, or null. */
    public static RadiationZone zoneAt(Entity entity) {
        ResourceKey<Level> dim = entity.level().dimension();
        return zoneAt(dim, entity.getX(), entity.getY(), entity.getZ());
    }

    public static RadiationZone zoneAt(ResourceKey<Level> dim, double x, double y, double z) {
        // INSIDE zones are independent: irradiate when the point is inside the bounds.
        // OUTSIDE zones (safe zones) UNION their safe areas: a player is irradiated by
        // OUTSIDE zones only when standing outside ALL OUTSIDE zones in the dimension.
        RadiationZone bestInside = null;
        RadiationZone bestOutside = null;
        boolean insideAnySafe = false;
        boolean anyOutsideInDim = false;
        for (RadiationZone zone : ZONES.values()) {
            if (!zone.dimension().equals(dim)) continue;
            if (zone.mode() == ZoneMode.INSIDE) {
                if (zone.contains(dim, x, y, z)) {
                    if (bestInside == null || zone.level() > bestInside.level()) bestInside = zone;
                }
            } else {
                anyOutsideInDim = true;
                if (zone.containsBounds(x, y, z)) insideAnySafe = true;
                if (bestOutside == null || zone.level() > bestOutside.level()) bestOutside = zone;
            }
        }
        RadiationZone outsideHit = (anyOutsideInDim && !insideAnySafe) ? bestOutside : null;
        if (bestInside != null && outsideHit != null) {
            return bestInside.level() >= outsideHit.level() ? bestInside : outsideHit;
        }
        return bestInside != null ? bestInside : outsideHit;
    }

    public static List<RadiationZone> zonesAt(ResourceKey<Level> dim, double x, double y, double z) {
        List<RadiationZone> out = new ArrayList<>();
        boolean insideAnySafe = false;
        for (RadiationZone zone : ZONES.values()) {
            if (zone.mode() == ZoneMode.OUTSIDE && zone.dimension().equals(dim)
                    && zone.containsBounds(x, y, z)) {
                insideAnySafe = true; break;
            }
        }
        for (RadiationZone zone : ZONES.values()) {
            if (!zone.dimension().equals(dim)) continue;
            if (zone.mode() == ZoneMode.INSIDE) {
                if (zone.contains(dim, x, y, z)) out.add(zone);
            } else if (!insideAnySafe) {
                out.add(zone);
            }
        }
        return out;
    }

    private static Path dataFile() {
        if (SERVER == null) return null;
        Path worldPath = SERVER.getWorldPath(LevelResource.ROOT);
        return worldPath.resolve("data").resolve("radiationzones.json");
    }

    public static void load() {
        ZONES.clear();
        Path file = dataFile();
        if (file == null || !Files.exists(file)) return;
        try {
            String content = Files.readString(file);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("zones");
            if (arr == null) return;
            for (var el : arr) {
                try {
                    RadiationZone z = RadiationZone.fromJson(el.getAsJsonObject());
                    ZONES.put(z.name().toLowerCase(Locale.ROOT), z);
                } catch (Exception ex) {
                    RadiationZones.LOGGER.warn("Failed to load a zone entry: {}", ex.getMessage());
                }
            }
            RadiationZones.LOGGER.info("Loaded {} radiation zones", ZONES.size());
        } catch (IOException e) {
            RadiationZones.LOGGER.error("Failed to load radiation zones", e);
        }
    }

    public static void save() {
        Path file = dataFile();
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (RadiationZone z : ZONES.values()) arr.add(z.toJson());
            root.add("zones", arr);
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            RadiationZones.LOGGER.error("Failed to save radiation zones", e);
        }
    }

    public static class LifecycleEvents {
        @SubscribeEvent
        public void onStarted(ServerStartedEvent e) {
            setServer(e.getServer());
            load();
        }

        @SubscribeEvent
        public void onStopping(ServerStoppingEvent e) {
            save();
            ZONES.clear();
            SERVER = null;
        }
    }
}
