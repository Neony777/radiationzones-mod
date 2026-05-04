package dev.radiationzones.zone;

import dev.radiationzones.RadiationZones;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodic driver for zone spread, shrink, drift, and level decay.
 * Runs server-side once per second and applies time-based deltas based on each
 * zone's persisted millis stamps so logic survives lag/restart correctly.
 */
public final class ZoneTicker {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int SAVE_INTERVAL_TICKS = 20 * 60; // save every 60s

    private int tickCounter = 0;
    private int sinceSaveTicks = 0;
    private boolean dirty = false;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL_TICKS) return;
        tickCounter = 0;

        long now = System.currentTimeMillis();
        List<RadiationZone> toRemove = new ArrayList<>();
        for (RadiationZone zone : ZoneManager.all()) {
            if (updateZone(zone, now)) dirty = true;
            // INSIDE zones auto-remove on collapse or level<=0. OUTSIDE (safe) zones
            // are kept on collapse (entire dimension irradiated — admin scorched-
            // earth scenario) but are removed when their level decays to 0 so they
            // don't keep irradiating as a phantom level-1 zone via the config's
            // level<=0 → 1 clamp.
            if (zone.level() <= 0
                    || (zone.shouldAutoRemoveWhenCollapsed() && zone.isCollapsed())) {
                toRemove.add(zone);
            }
        }
        for (RadiationZone z : toRemove) {
            ZoneManager.remove(z.name());
            RadiationZones.LOGGER.info("Radiation zone '{}' fully decayed and was removed.", z.name());
            dirty = true;
        }

        sinceSaveTicks += CHECK_INTERVAL_TICKS;
        if (dirty && sinceSaveTicks >= SAVE_INTERVAL_TICKS) {
            ZoneManager.save();
            dirty = false;
            sinceSaveTicks = 0;
        }
    }

    /** Returns true if the zone changed in any persisted way. */
    private static boolean updateZone(RadiationZone zone, long now) {
        boolean changed = false;
        double minutesElapsed = (now - zone.lastUpdateMillis()) / 60000.0;

        // Spread / shrink (uniform on each face).
        if (zone.spreadRate() > 0 || zone.decayRate() > 0) {
            double netBlocks = (zone.spreadRate() - zone.decayRate()) * minutesElapsed;
            int delta = (int) Math.floor(Math.abs(netBlocks));
            if (delta > 0) {
                if (netBlocks > 0) {
                    grow(zone, delta);
                } else {
                    shrink(zone, delta);
                }
                double consumedMinutes = delta / Math.max(1e-9, Math.abs(zone.spreadRate() - zone.decayRate()));
                zone.setLastUpdateMillis(zone.lastUpdateMillis() + (long) (consumedMinutes * 60000.0));
                changed = true;
            }
        } else {
            zone.setLastUpdateMillis(now);
        }

        // Drift (translate min/max + origin per-axis). Each axis accumulates its own
        // fractional residual so a fast axis can't starve a slow one (per-axis
        // time-based correctness).
        if (zone.driftX() != 0.0 || zone.driftY() != 0.0 || zone.driftZ() != 0.0) {
            double driftMinutes = (now - zone.lastDriftMillis()) / 60000.0;
            if (driftMinutes > 0) {
                double rx = zone.driftResidualX() + zone.driftX() * driftMinutes;
                double ry = zone.driftResidualY() + zone.driftY() * driftMinutes;
                double rz = zone.driftResidualZ() + zone.driftZ() * driftMinutes;
                int dx = (int) (rx >= 0 ? Math.floor(rx) : -Math.floor(-rx));
                int dy = (int) (ry >= 0 ? Math.floor(ry) : -Math.floor(-ry));
                int dz = (int) (rz >= 0 ? Math.floor(rz) : -Math.floor(-rz));
                zone.setDriftResiduals(rx - dx, ry - dy, rz - dz);
                if (dx != 0 || dy != 0 || dz != 0) {
                    drift(zone, dx, dy, dz);
                }
                zone.setLastDriftMillis(now);
                // OR (don't overwrite) so a spread/shrink update from above isn't
                // hidden by a drift tick that didn't cross a whole-block threshold.
                // Mark changed even when only residuals/lastDriftMillis advanced so
                // periodic autosave persists partial progress across crashes.
                changed |= true;
            }
        } else {
            zone.setLastDriftMillis(now);
        }

        // Level decay
        if (zone.levelDecayMinutes() > 0) {
            double levelMinutesElapsed = (now - zone.lastLevelDecayMillis()) / 60000.0;
            int levelDrops = (int) Math.floor(levelMinutesElapsed / zone.levelDecayMinutes());
            if (levelDrops > 0) {
                int newLevel = Math.max(0, zone.level() - levelDrops);
                zone.setLevel(newLevel);
                long consumedMillis = (long) (levelDrops * zone.levelDecayMinutes() * 60000.0);
                zone.setLastLevelDecayMillis(zone.lastLevelDecayMillis() + consumedMillis);
                changed = true;
            }
        } else {
            zone.setLastLevelDecayMillis(now);
        }

        return changed;
    }

    private static void grow(RadiationZone zone, int blocks) {
        BlockPos min = zone.min();
        BlockPos max = zone.max();
        // CIRCLE zones are cylinders that span the full world height — never alter Y.
        boolean keepY = zone.shape() == ZoneShape.CIRCLE;
        int newMinX = min.getX() - blocks;
        int newMinY = keepY ? min.getY() : min.getY() - blocks;
        int newMinZ = min.getZ() - blocks;
        int newMaxX = max.getX() + blocks;
        int newMaxY = keepY ? max.getY() : max.getY() + blocks;
        int newMaxZ = max.getZ() + blocks;

        if (zone.maxRadius() > 0) {
            BlockPos om = zone.originMin();
            BlockPos oM = zone.originMax();
            int r = zone.maxRadius();
            newMinX = Math.max(newMinX, om.getX() - r);
            newMinY = Math.max(newMinY, om.getY() - r);
            newMinZ = Math.max(newMinZ, om.getZ() - r);
            newMaxX = Math.min(newMaxX, oM.getX() + r);
            newMaxY = Math.min(newMaxY, oM.getY() + r);
            newMaxZ = Math.min(newMaxZ, oM.getZ() + r);
        }
        zone.setMin(new BlockPos(newMinX, newMinY, newMinZ));
        zone.setMax(new BlockPos(newMaxX, newMaxY, newMaxZ));
    }

    private static void shrink(RadiationZone zone, int blocks) {
        BlockPos min = zone.min();
        BlockPos max = zone.max();
        // CIRCLE zones are cylinders that span the full world height — never alter Y.
        boolean keepY = zone.shape() == ZoneShape.CIRCLE;
        zone.setMin(new BlockPos(min.getX() + blocks, keepY ? min.getY() : min.getY() + blocks, min.getZ() + blocks));
        zone.setMax(new BlockPos(max.getX() - blocks, keepY ? max.getY() : max.getY() - blocks, max.getZ() - blocks));
    }

    /** Translates the bounds and origin uniformly so drift doesn't fight maxRadius.
     *  CIRCLE zones never drift on Y (cylinder spans full world height). */
    private static void drift(RadiationZone zone, int dx, int dy, int dz) {
        int ady = zone.shape() == ZoneShape.CIRCLE ? 0 : dy;
        zone.setMin(zone.min().offset(dx, ady, dz));
        zone.setMax(zone.max().offset(dx, ady, dz));
        zone.setOriginMin(zone.originMin().offset(dx, ady, dz));
        zone.setOriginMax(zone.originMax().offset(dx, ady, dz));
    }

    private ZoneTicker() { /* package only */ }
    public static ZoneTicker create() { return new ZoneTicker(); }
}
