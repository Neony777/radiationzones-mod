package dev.radiationzones.zone;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A radiation zone. Cuboid bounds may grow (spread) or shrink (decay) over time,
 * drift across the world, and the level may decay periodically. Mutable so the
 * {@link ZoneManager} can update it in place without reallocating the map entry.
 *
 * <p>Two {@link ZoneMode}s:
 * <ul>
 *   <li>{@code INSIDE}  — radiation is dealt inside the bounds (classic hazard).</li>
 *   <li>{@code OUTSIDE} — radiation is dealt everywhere in the dimension EXCEPT
 *       inside the bounds (world-border-style safe bubble).</li>
 * </ul>
 *
 * <p>Two {@link ZoneShape}s:
 * <ul>
 *   <li>{@code SQUARE} — axis-aligned cuboid using min/max.</li>
 *   <li>{@code CIRCLE} — vertical cylinder, horizontal radius derived from
 *       the bounding box; vertical extent is the full bounding-box Y range.</li>
 * </ul>
 */
public final class RadiationZone {
    private final String name;
    private final ResourceKey<Level> dimension;

    private BlockPos min;
    private BlockPos max;
    private int level;

    /** Original bounds at creation, used as reference for {@code maxRadius}. */
    private BlockPos originMin;
    private BlockPos originMax;

    /** Spread rate in blocks per minute outward in each axis (0 = static). */
    private double spreadRate;
    /** Decay rate in blocks per minute inward in each axis (0 = no shrinking). */
    private double decayRate;
    /** Minutes between automatic level reductions (0 = no level decay). */
    private double levelDecayMinutes;
    /** Maximum spread radius (blocks) beyond original bounds in any direction. 0 = unlimited. */
    private int maxRadius;

    /** Wall-clock millis of the last spread/decay update. Persisted. */
    private long lastUpdateMillis;
    /** Wall-clock millis of the last level-decay tick. Persisted. */
    private long lastLevelDecayMillis;

    /** INSIDE = damage inside, OUTSIDE = damage everywhere except inside. */
    private ZoneMode mode;
    /** SQUARE cuboid or CIRCLE cylinder. */
    private ZoneShape shape;

    /** Drift in blocks/minute on each axis (translates min/max + origin). */
    private double driftX;
    private double driftY;
    private double driftZ;
    /** Wall-clock millis of the last drift update. */
    private long lastDriftMillis;

    /** Per-axis fractional block residuals carried across drift ticks so that a
     *  fast axis can't starve a slow one. Persisted so restarts don't lose them. */
    private double driftResidualX;
    private double driftResidualY;
    private double driftResidualZ;

    public RadiationZone(String name, ResourceKey<Level> dimension,
                         BlockPos min, BlockPos max, int level,
                         BlockPos originMin, BlockPos originMax,
                         double spreadRate, double decayRate, double levelDecayMinutes,
                         int maxRadius, long lastUpdateMillis, long lastLevelDecayMillis) {
        this(name, dimension, min, max, level, originMin, originMax,
                spreadRate, decayRate, levelDecayMinutes, maxRadius,
                lastUpdateMillis, lastLevelDecayMillis,
                ZoneMode.INSIDE, ZoneShape.SQUARE, 0.0, 0.0, 0.0, lastUpdateMillis);
    }

    public RadiationZone(String name, ResourceKey<Level> dimension,
                         BlockPos min, BlockPos max, int level,
                         BlockPos originMin, BlockPos originMax,
                         double spreadRate, double decayRate, double levelDecayMinutes,
                         int maxRadius, long lastUpdateMillis, long lastLevelDecayMillis,
                         ZoneMode mode, ZoneShape shape,
                         double driftX, double driftY, double driftZ, long lastDriftMillis) {
        this.name = name;
        this.dimension = dimension;
        this.min = min;
        this.max = max;
        this.level = level;
        this.originMin = originMin;
        this.originMax = originMax;
        this.spreadRate = spreadRate;
        this.decayRate = decayRate;
        this.levelDecayMinutes = levelDecayMinutes;
        this.maxRadius = maxRadius;
        this.lastUpdateMillis = lastUpdateMillis;
        this.lastLevelDecayMillis = lastLevelDecayMillis;
        this.mode = mode == null ? ZoneMode.INSIDE : mode;
        this.shape = shape == null ? ZoneShape.SQUARE : shape;
        this.driftX = driftX;
        this.driftY = driftY;
        this.driftZ = driftZ;
        this.lastDriftMillis = lastDriftMillis;
    }

    /** Classic inclusive cuboid zone from two arbitrary corners. */
    public static RadiationZone of(String name, ResourceKey<Level> dimension, BlockPos a, BlockPos b, int level) {
        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ())
        );
        long now = System.currentTimeMillis();
        return new RadiationZone(name, dimension, min, max, level, min, max,
                0.0, 0.0, 0.0, 0, now, now);
    }

    /** Safe zone (OUTSIDE mode) centered on a point with a given size and shape.
     *  For SQUARE, {@code size} is the full side length. For CIRCLE, {@code size}
     *  is the radius. The cylinder spans the full world height. */
    public static RadiationZone safeZone(String name, ResourceKey<Level> dimension,
                                         BlockPos center, ZoneShape shape, int size, int level,
                                         int worldMinY, int worldMaxY) {
        int s = Math.max(1, size);
        BlockPos min, max;
        if (shape == ZoneShape.CIRCLE) {
            // size = horizontal radius; bounding box has full diameter (2*r) wide.
            min = new BlockPos(center.getX() - s, worldMinY, center.getZ() - s);
            max = new BlockPos(center.getX() + s - 1, worldMaxY, center.getZ() + s - 1);
        } else {
            // size = full side length in blocks.
            int half = s / 2;
            int otherHalf = s - half;
            min = new BlockPos(center.getX() - half, worldMinY, center.getZ() - half);
            max = new BlockPos(center.getX() + otherHalf - 1, worldMaxY, center.getZ() + otherHalf - 1);
        }
        long now = System.currentTimeMillis();
        return new RadiationZone(name, dimension, min, max, level, min, max,
                0.0, 0.0, 0.0, 0, now, now,
                ZoneMode.OUTSIDE, shape == null ? ZoneShape.SQUARE : shape,
                0.0, 0.0, 0.0, now);
    }

    public String name() { return name; }
    public ResourceKey<Level> dimension() { return dimension; }
    public BlockPos min() { return min; }
    public BlockPos max() { return max; }
    public int level() { return level; }
    public BlockPos originMin() { return originMin; }
    public BlockPos originMax() { return originMax; }
    public double spreadRate() { return spreadRate; }
    public double decayRate() { return decayRate; }
    public double levelDecayMinutes() { return levelDecayMinutes; }
    public int maxRadius() { return maxRadius; }
    public long lastUpdateMillis() { return lastUpdateMillis; }
    public long lastLevelDecayMillis() { return lastLevelDecayMillis; }
    public ZoneMode mode() { return mode; }
    public ZoneShape shape() { return shape; }
    public double driftX() { return driftX; }
    public double driftY() { return driftY; }
    public double driftZ() { return driftZ; }
    public long lastDriftMillis() { return lastDriftMillis; }
    public double driftResidualX() { return driftResidualX; }
    public double driftResidualY() { return driftResidualY; }
    public double driftResidualZ() { return driftResidualZ; }
    public void setDriftResiduals(double rx, double ry, double rz) {
        this.driftResidualX = rx; this.driftResidualY = ry; this.driftResidualZ = rz;
    }

    public void setMin(BlockPos min) { this.min = min; }
    public void setMax(BlockPos max) { this.max = max; }
    public void setOriginMin(BlockPos p) { this.originMin = p; }
    public void setOriginMax(BlockPos p) { this.originMax = p; }
    public void setLevel(int level) { this.level = level; }
    public void setSpreadRate(double v) { this.spreadRate = Math.max(0, v); }
    public void setDecayRate(double v) { this.decayRate = Math.max(0, v); }
    public void setLevelDecayMinutes(double v) { this.levelDecayMinutes = Math.max(0, v); }
    public void setMaxRadius(int v) { this.maxRadius = Math.max(0, v); }
    public void setLastUpdateMillis(long v) { this.lastUpdateMillis = v; }
    public void setLastLevelDecayMillis(long v) { this.lastLevelDecayMillis = v; }
    public void setMode(ZoneMode m) { this.mode = m == null ? ZoneMode.INSIDE : m; }
    public void setShape(ZoneShape s) { this.shape = s == null ? ZoneShape.SQUARE : s; }
    public void setDrift(double dx, double dy, double dz) {
        this.driftX = dx; this.driftY = dy; this.driftZ = dz;
        // Reset residuals when drift changes so the new rate starts cleanly.
        this.driftResidualX = 0; this.driftResidualY = 0; this.driftResidualZ = 0;
    }
    public void setLastDriftMillis(long v) { this.lastDriftMillis = v; }

    /** True when the cuboid has inverted (size <= 0 on any axis). */
    public boolean isCollapsed() {
        return min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ();
    }

    /** Whether the entity-style point at (x, y, z) is INSIDE the geometric bounds
     *  (independent of mode). Used as a building block for {@link #contains}. */
    private boolean insideBounds(double x, double y, double z) {
        if (isCollapsed()) return false;
        // For CIRCLE we still gate on the bounding box's Y range, and use horizontal radius.
        if (shape == ZoneShape.CIRCLE) {
            double cx = (min.getX() + max.getX() + 1) * 0.5;
            double cz = (min.getZ() + max.getZ() + 1) * 0.5;
            double rx = (max.getX() + 1 - min.getX()) * 0.5;
            double rz = (max.getZ() + 1 - min.getZ()) * 0.5;
            double r = Math.min(rx, rz);
            double dx = x - cx, dz = z - cz;
            if (dx * dx + dz * dz > r * r) return false;
            return y >= min.getY() && y <= max.getY() + 1;
        }
        return x >= min.getX() && x <= max.getX() + 1
                && y >= min.getY() && y <= max.getY() + 1
                && z >= min.getZ() && z <= max.getZ() + 1;
    }

    /** True when the entity-style point at (x, y, z) is in the IRRADIATED region.
     *  For INSIDE mode: the point is inside the bounds.
     *  For OUTSIDE mode: the point is in the same dimension and NOT inside the bounds. */
    public boolean contains(ResourceKey<Level> dim, double x, double y, double z) {
        if (!dim.equals(dimension)) return false;
        boolean inside = insideBounds(x, y, z);
        return mode == ZoneMode.OUTSIDE ? !inside : inside;
    }

    /** True when the point is within the geometric bounds of this zone, ignoring
     *  mode. Used by {@link ZoneManager} to compute the union of safe areas
     *  across overlapping OUTSIDE zones. Dimension is NOT checked here — callers
     *  must filter by dimension. */
    public boolean containsBounds(double x, double y, double z) {
        return insideBounds(x, y, z);
    }

    /** Whether the bounds are still meaningful (non-collapsed). For OUTSIDE zones we
     *  keep them around even if collapsed (the whole dimension is irradiated). */
    public boolean shouldAutoRemoveWhenCollapsed() {
        return mode == ZoneMode.INSIDE;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("dimension", dimension.location().toString());
        o.addProperty("level", level);
        o.add("min", posJson(min));
        o.add("max", posJson(max));
        o.add("originMin", posJson(originMin));
        o.add("originMax", posJson(originMax));
        o.addProperty("spreadRate", spreadRate);
        o.addProperty("decayRate", decayRate);
        o.addProperty("levelDecayMinutes", levelDecayMinutes);
        o.addProperty("maxRadius", maxRadius);
        o.addProperty("lastUpdateMillis", lastUpdateMillis);
        o.addProperty("lastLevelDecayMillis", lastLevelDecayMillis);
        o.addProperty("mode", mode.name());
        o.addProperty("shape", shape.name());
        o.addProperty("driftX", driftX);
        o.addProperty("driftY", driftY);
        o.addProperty("driftZ", driftZ);
        o.addProperty("lastDriftMillis", lastDriftMillis);
        o.addProperty("driftResidualX", driftResidualX);
        o.addProperty("driftResidualY", driftResidualY);
        o.addProperty("driftResidualZ", driftResidualZ);
        return o;
    }

    private static JsonObject posJson(BlockPos p) {
        JsonObject o = new JsonObject();
        o.addProperty("x", p.getX());
        o.addProperty("y", p.getY());
        o.addProperty("z", p.getZ());
        return o;
    }

    private static BlockPos posFromJson(JsonObject o) {
        return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }

    public static RadiationZone fromJson(JsonObject o) {
        String name = o.get("name").getAsString();
        ResourceKey<Level> dim = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.parse(o.get("dimension").getAsString())
        );
        int level = o.get("level").getAsInt();
        BlockPos min = posFromJson(o.getAsJsonObject("min"));
        BlockPos max = posFromJson(o.getAsJsonObject("max"));

        BlockPos originMin = o.has("originMin") ? posFromJson(o.getAsJsonObject("originMin")) : min;
        BlockPos originMax = o.has("originMax") ? posFromJson(o.getAsJsonObject("originMax")) : max;
        double spreadRate = o.has("spreadRate") ? o.get("spreadRate").getAsDouble() : 0.0;
        double decayRate = o.has("decayRate") ? o.get("decayRate").getAsDouble() : 0.0;
        double levelDecayMinutes = o.has("levelDecayMinutes") ? o.get("levelDecayMinutes").getAsDouble() : 0.0;
        int maxRadius = o.has("maxRadius") ? o.get("maxRadius").getAsInt() : 0;
        long now = System.currentTimeMillis();
        long lastUpdate = o.has("lastUpdateMillis") ? o.get("lastUpdateMillis").getAsLong() : now;
        long lastLevelDecay = o.has("lastLevelDecayMillis") ? o.get("lastLevelDecayMillis").getAsLong() : now;

        ZoneMode mode = ZoneMode.INSIDE;
        if (o.has("mode")) {
            try { mode = ZoneMode.fromString(o.get("mode").getAsString()); }
            catch (IllegalArgumentException ignored) { /* keep default */ }
        }
        ZoneShape shape = ZoneShape.SQUARE;
        if (o.has("shape")) {
            try { shape = ZoneShape.fromString(o.get("shape").getAsString()); }
            catch (IllegalArgumentException ignored) { /* keep default */ }
        }
        double dx = o.has("driftX") ? o.get("driftX").getAsDouble() : 0.0;
        double dy = o.has("driftY") ? o.get("driftY").getAsDouble() : 0.0;
        double dz = o.has("driftZ") ? o.get("driftZ").getAsDouble() : 0.0;
        long lastDrift = o.has("lastDriftMillis") ? o.get("lastDriftMillis").getAsLong() : now;
        double rx = o.has("driftResidualX") ? o.get("driftResidualX").getAsDouble() : 0.0;
        double ry = o.has("driftResidualY") ? o.get("driftResidualY").getAsDouble() : 0.0;
        double rz = o.has("driftResidualZ") ? o.get("driftResidualZ").getAsDouble() : 0.0;

        RadiationZone z = new RadiationZone(name, dim, min, max, level, originMin, originMax,
                spreadRate, decayRate, levelDecayMinutes, maxRadius, lastUpdate, lastLevelDecay,
                mode, shape, dx, dy, dz, lastDrift);
        z.driftResidualX = rx;
        z.driftResidualY = ry;
        z.driftResidualZ = rz;
        return z;
    }
}
