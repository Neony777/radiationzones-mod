package dev.radiationzones.event;

import dev.radiationzones.config.RadiationConfig;
import dev.radiationzones.effect.ModEffects;
import dev.radiationzones.item.FilterItem;
import dev.radiationzones.item.GasMaskItem;
import dev.radiationzones.item.ModItems;
import dev.radiationzones.zone.RadiationZone;
import dev.radiationzones.zone.ZoneMode;
import dev.radiationzones.zone.ZoneShape;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import dev.radiationzones.zone.WandSelections;
import dev.radiationzones.zone.ZoneManager;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerRadiationHandler {
    private static final ResourceKey<DamageType> RADIATION_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("radiationzones", "radiation"));

    private static final class State {
        ServerBossEvent bar;
        int ticksInside; // ticks accumulated inside any radiation zone
        int ticksSinceDamage;
        int ticksSinceGeiger;
        String currentZoneName;
        /** Highest Lugol's duration (in ticks) we have seen for the current effect instance.
         *  Used as the boss-bar denominator so the bar reflects the granted duration,
         *  not the config default. Reset to 0 when the player loses the effect. */
        int lugolsTotalTicks;
    }

    private final Map<UUID, State> states = new HashMap<>();
    /** Per-player counter used for nearby-zone Geiger ticks while not inside any zone. */
    private final Map<UUID, Integer> nearbyGeigerTicks = new HashMap<>();

    @SubscribeEvent
    public void onTick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if ((player.isSpectator() && RadiationConfig.spectatorImmune())
                || (player.isCreative() && RadiationConfig.creativeImmune())) {
            removeState(player);
            return;
        }

        RadiationZone zone = ZoneManager.zoneAt(player);
        State state = states.get(player.getUUID());

        if (zone == null) {
            if (state != null) removeState(player);
            // Player isn't inside any zone, but show hint particles AND sparse Geiger
            // ticks for nearby zones so danger is visible/audible from a distance.
            spawnNearbyZonePreview(player);
            tickGeigerNearby(player);
            return;
        }
        // Player just entered a zone — clear any stale nearby-geiger counter.
        nearbyGeigerTicks.remove(player.getUUID());

        if (state == null) {
            state = new State();
            state.bar = new ServerBossEvent(
                    Component.literal("Radiation Zone"),
                    BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.PROGRESS);
            if (RadiationConfig.bossBarVisible()) {
                state.bar.addPlayer(player);
            } else {
                state.bar.setVisible(false);
            }
            states.put(player.getUUID(), state);
        }

        if (!zone.name().equals(state.currentZoneName)) {
            state.currentZoneName = zone.name();
            state.ticksInside = 0;
            state.ticksSinceDamage = 0;
            state.ticksSinceGeiger = 0;
        }

        boolean protectedByLugols = player.hasEffect(ModEffects.LUGOLS_IODINE);

        int graceTicks = RadiationConfig.gracePeriodSeconds(zone.level()) * 20;
        state.ticksInside++;
        int remainingTicks = Math.max(0, graceTicks - state.ticksInside);
        int remainingSeconds = (remainingTicks + 19) / 20;

        // Ambient feedback (particles + Geiger ticks) regardless of protection,
        // because the player is still inside an irradiated zone.
        spawnAmbientParticles(player, zone);
        tickGeigerCounter(player, zone, state);

        if (protectedByLugols) {
            int lugolsRemainingTicks = player.getEffect(ModEffects.LUGOLS_IODINE).getDuration();
            int lugolsRemainingSec = (lugolsRemainingTicks + 19) / 20;
            // Track the granted total: the highest remaining we've ever seen for this
            // effect instance is effectively the duration it was applied with.
            if (lugolsRemainingTicks > state.lugolsTotalTicks) {
                state.lugolsTotalTicks = lugolsRemainingTicks;
            }
            int lugolsTotalTicks = Math.max(1, state.lugolsTotalTicks);
            state.bar.setName(Component.translatable(
                    zone.mode() == ZoneMode.OUTSIDE
                            ? "bossbar.radiationzones.outside_protected"
                            : "bossbar.radiationzones.protected",
                    zone.level(), lugolsRemainingSec));
            state.bar.setColor(BossEvent.BossBarColor.BLUE);
            state.bar.setProgress(Math.max(0f, Math.min(1f, (float) lugolsRemainingTicks / lugolsTotalTicks)));
            state.ticksSinceDamage = 0;
            return;
        }
        // Effect lapsed — reset the cached total so the next dose re-anchors the bar.
        state.lugolsTotalTicks = 0;

        if (remainingTicks > 0) {
            state.bar.setName(Component.translatable(
                    zone.mode() == ZoneMode.OUTSIDE
                            ? "bossbar.radiationzones.outside_zone"
                            : "bossbar.radiationzones.zone",
                    zone.level(), remainingSeconds));
            state.bar.setColor(BossEvent.BossBarColor.YELLOW);
            state.bar.setProgress((float) remainingTicks / Math.max(1, graceTicks));
        } else {
            // Refresh the boss bar every post-grace tick so it accurately reflects the
            // current mask state between damage ticks. Use the installed filter's tier
            // so Basic shows partial protection rather than full.
            ItemStack helmetForUi = player.getItemBySlot(EquipmentSlot.HEAD);
            FilterItem.Tier equippedTier = null;
            if (helmetForUi.getItem() == ModItems.GAS_MASK.get() && GasMaskItem.hasUsableFilter(helmetForUi)) {
                var f = helmetForUi.get(dev.radiationzones.component.ModDataComponents.INSTALLED_FILTER.get());
                if (f != null) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(f.filterId());
                    if (item instanceof FilterItem fi) equippedTier = fi.tier();
                }
            }
            if (equippedTier == null) {
                state.bar.setName(Component.translatable(
                        zone.mode() == ZoneMode.OUTSIDE
                                ? "bossbar.radiationzones.outside_damage"
                                : "bossbar.radiationzones.damage",
                        zone.level()));
                state.bar.setColor(BossEvent.BossBarColor.RED);
            } else if (equippedTier.blockFraction >= 1f) {
                state.bar.setName(Component.translatable(
                        zone.mode() == ZoneMode.OUTSIDE
                                ? "bossbar.radiationzones.outside_mask_protected"
                                : "bossbar.radiationzones.mask_protected", zone.level()));
                state.bar.setColor(BossEvent.BossBarColor.GREEN);
            } else {
                state.bar.setName(Component.translatable(
                        zone.mode() == ZoneMode.OUTSIDE
                                ? "bossbar.radiationzones.outside_mask_partial"
                                : "bossbar.radiationzones.mask_partial", zone.level()));
                state.bar.setColor(BossEvent.BossBarColor.YELLOW);
            }
            state.bar.setProgress(1f);

            int interval = RadiationConfig.damageIntervalTicks(zone.level());
            state.ticksSinceDamage++;
            if (state.ticksSinceDamage >= interval) {
                state.ticksSinceDamage = 0;

                // Gas mask interception: if the player is wearing a mask with a working
                // filter, consume filter durability instead of applying damage. The mask
                // also takes a small bit of wear over time. Lugol's already short-circuited
                // above, so it always wins.
                ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
                FilterItem.Tier tier = null;
                if (helmet.getItem() == ModItems.GAS_MASK.get()) {
                    int wear = Math.max(1, zone.level());
                    tier = GasMaskItem.consumeAndGetTier(helmet, wear);
                    if (tier != null) {
                        GasMaskItem.damageMask(helmet, player, 1);
                    }
                }

                float dmg = (float) RadiationConfig.damageAmount(zone.level());
                // Halve damage if the player is below the shielding threshold (e.g. underground).
                if (player.getY() < RadiationConfig.shieldingYBelow()) dmg *= 0.5f;

                if (tier != null) {
                    // Filter absorbs `blockFraction` of the damage. Anything left over
                    // still hits the player (Basic = 40% leak, Industrial/Hazmat = 0%).
                    float leftover = dmg * (1f - tier.blockFraction);
                    if (tier.overflowResistance) {
                        // Hazmat overflow: brief Resistance I window after each tick.
                        player.addEffect(new MobEffectInstance(
                                MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false, true));
                    }
                    if (tier.blockFraction >= 1f) {
                        state.bar.setName(Component.translatable(
                                zone.mode() == ZoneMode.OUTSIDE
                                        ? "bossbar.radiationzones.outside_mask_protected"
                                        : "bossbar.radiationzones.mask_protected", zone.level()));
                        state.bar.setColor(BossEvent.BossBarColor.GREEN);
                    } else {
                        state.bar.setName(Component.translatable(
                                zone.mode() == ZoneMode.OUTSIDE
                                        ? "bossbar.radiationzones.outside_mask_partial"
                                        : "bossbar.radiationzones.mask_partial", zone.level()));
                        state.bar.setColor(BossEvent.BossBarColor.YELLOW);
                    }
                    if (leftover > 0.01f) {
                        DamageSource src = player.level().damageSources().source(RADIATION_DAMAGE, null);
                        player.hurt(src, leftover);
                    }
                    return;
                }

                DamageSource src = player.level().damageSources().source(RADIATION_DAMAGE, null);
                player.hurt(src, dmg);
            }
        }
    }

    private void spawnAmbientParticles(ServerPlayer player, RadiationZone zone) {
        if (!RadiationConfig.particlesEnabled()) return;
        int count = RadiationConfig.particlesPerTick(zone.level());
        if (count <= 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        double radius = RadiationConfig.particlesRadius();
        RandomSource rand = player.getRandom();

        // For OUTSIDE (safe-zone) mode, the irradiated region is everywhere outside
        // the bounds, so haze spawns freely around the player without box clamping.
        boolean clampToBox = zone.mode() == ZoneMode.INSIDE;
        double minX = zone.min().getX();
        double minY = zone.min().getY();
        double minZ = zone.min().getZ();
        double maxX = zone.max().getX() + 1.0;
        double maxY = zone.max().getY() + 1.0;
        double maxZ = zone.max().getZ() + 1.0;

        for (int i = 0; i < count; i++) {
            double x = player.getX() + (rand.nextDouble() - 0.5) * 2.0 * radius;
            double y = player.getY() + (rand.nextDouble() - 0.2) * radius;
            double z = player.getZ() + (rand.nextDouble() - 0.5) * 2.0 * radius;

            if (clampToBox) {
                x = Math.max(minX, Math.min(maxX, x));
                y = Math.max(minY, Math.min(maxY, y));
                z = Math.max(minZ, Math.min(maxZ, z));
            }

            spawnHaze(level, rand, x, y, z, 1.0f);
        }
    }

    /** Spawns one "puff" of radioactive haze at (x,y,z): a colored entity-effect
     *  particle plus an optional drifting smoke accent. Color comes from config. */
    private void spawnHaze(ServerLevel level, RandomSource rand,
                           double x, double y, double z, float densityMul) {
        int rgb = RadiationConfig.particleColorPacked();
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        // Colored haze is the primary visual — visible whether or not smoke is on.
        level.sendParticles(
                ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, r, g, b),
                x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        if (RadiationConfig.particleSmokeAccent() && rand.nextFloat() < 0.5f * densityMul) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, y, z, 1,
                    (rand.nextDouble() - 0.5) * 0.01,
                    0.005 + rand.nextDouble() * 0.01,
                    (rand.nextDouble() - 0.5) * 0.01,
                    0.0);
        }
    }

    private void spawnNearbyZonePreview(ServerPlayer player) {
        if (!RadiationConfig.particlesEnabled()) return;
        if (!RadiationConfig.particlePreviewEnabled()) return;
        double previewRadius = RadiationConfig.particlesPreviewRadius();
        if (previewRadius <= 0.0) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // Throttle: only check every 4 ticks (5 Hz) to keep the iteration cheap.
        if (player.tickCount % 4 != 0) return;

        ResourceKey<Level> dim = level.dimension();
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        double previewSq = previewRadius * previewRadius;
        RandomSource rand = player.getRandom();

        for (RadiationZone zone : ZoneManager.all()) {
            if (!zone.dimension().equals(dim)) continue;

            double[] cp = closestDangerPoint(zone, px, py, pz);
            double cx = cp[0], cy = cp[1], cz = cp[2];
            double distSq = cp[3];
            if (distSq > previewSq) continue;

            // Spawn a small number of hint particles near the closest edge of the zone.
            // Density scales with proximity (closer = more particles) and zone level.
            double proximity = 1.0 - Math.sqrt(distSq) / previewRadius; // 0..1
            int hintCount = Math.max(1, (int) Math.round(
                    RadiationConfig.particlesPerTick(zone.level()) * 0.5 * proximity));

            for (int i = 0; i < hintCount; i++) {
                double sx = cx + (rand.nextDouble() - 0.5) * 2.0;
                double sy = cy + (rand.nextDouble() - 0.5) * 2.0;
                double sz = cz + (rand.nextDouble() - 0.5) * 2.0;
                spawnHaze(level, rand, sx, sy, sz, (float) proximity);
            }
        }
    }

    /** Returns {cx, cy, cz, distSq} — the nearest point in the irradiated region
     *  for the given zone, and squared distance from (px,py,pz). For INSIDE zones
     *  this is the closest point inside the bounds; for OUTSIDE (safe) zones it's
     *  the closest point on the boundary as seen from inside the safe area. */
    private static double[] closestDangerPoint(RadiationZone zone, double px, double py, double pz) {
        double minX = zone.min().getX();
        double minY = zone.min().getY();
        double minZ = zone.min().getZ();
        double maxX = zone.max().getX() + 1.0;
        double maxY = zone.max().getY() + 1.0;
        double maxZ = zone.max().getZ() + 1.0;

        if (zone.mode() == ZoneMode.INSIDE) {
            if (zone.shape() == ZoneShape.CIRCLE) {
                double cxC = (minX + maxX) * 0.5;
                double czC = (minZ + maxZ) * 0.5;
                double r = Math.min(maxX - minX, maxZ - minZ) * 0.5;
                double dx = px - cxC, dz = pz - czC;
                double horiz = Math.sqrt(dx * dx + dz * dz);
                double cy = Math.max(minY, Math.min(maxY, py));
                if (horiz <= r) {
                    double dyC = py - cy;
                    return new double[]{px, cy, pz, dyC * dyC};
                }
                double scale = r / horiz;
                double cxOut = cxC + dx * scale, czOut = czC + dz * scale;
                double ddx = px - cxOut, ddy = py - cy, ddz = pz - czOut;
                return new double[]{cxOut, cy, czOut, ddx * ddx + ddy * ddy + ddz * ddz};
            }
            double cx = Math.max(minX, Math.min(maxX, px));
            double cy = Math.max(minY, Math.min(maxY, py));
            double cz = Math.max(minZ, Math.min(maxZ, pz));
            double dx = px - cx, dy = py - cy, dz = pz - cz;
            return new double[]{cx, cy, cz, dx * dx + dy * dy + dz * dz};
        }

        // OUTSIDE: closest danger is on the boundary as seen from inside the bubble.
        // Y faces are also considered so finite-height safe zones (e.g. zones flipped
        // via /radiationzone setmode outside) have correct preview distances near the
        // top/bottom of their bounds.
        double best = Double.MAX_VALUE;
        double cx = px, cy = py, cz = pz;

        double dyMin = py - minY, dyMax = maxY - py;
        if (dyMin >= 0 && dyMin < best) { best = dyMin; cx = px; cy = minY; cz = pz; }
        if (dyMax >= 0 && dyMax < best) { best = dyMax; cx = px; cy = maxY; cz = pz; }

        if (zone.shape() == ZoneShape.CIRCLE) {
            double cxC = (minX + maxX) * 0.5;
            double czC = (minZ + maxZ) * 0.5;
            double r = Math.min(maxX - minX, maxZ - minZ) * 0.5;
            double dx = px - cxC, dz = pz - czC;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            double horizDist = Math.max(0.0, r - horiz);
            if (horizDist < best) {
                double angX, angZ;
                if (horiz < 1e-6) { angX = 1; angZ = 0; }
                else { angX = dx / horiz; angZ = dz / horiz; }
                double clampedY = Math.max(minY, Math.min(maxY, py));
                best = horizDist; cx = cxC + angX * r; cy = clampedY; cz = czC + angZ * r;
            }
        } else {
            double dxMin = px - minX, dxMax = maxX - px;
            double dzMin = pz - minZ, dzMax = maxZ - pz;
            if (dxMin >= 0 && dxMin < best) { best = dxMin; cx = minX; cy = py; cz = pz; }
            if (dxMax >= 0 && dxMax < best) { best = dxMax; cx = maxX; cy = py; cz = pz; }
            if (dzMin >= 0 && dzMin < best) { best = dzMin; cx = px;   cy = py; cz = minZ; }
            if (dzMax >= 0 && dzMax < best) { best = dzMax; cx = px;   cy = py; cz = maxZ; }
        }
        if (best == Double.MAX_VALUE) best = 0.0;
        return new double[]{cx, cy, cz, best * best};
    }

    /** Plays sparse Geiger ticks when the player is near (but not inside) a zone.
     *  Tick interval scales inversely with proximity — closer zones tick faster. */
    private void tickGeigerNearby(ServerPlayer player) {
        if (!RadiationConfig.geigerEnabled()) return;
        if (!RadiationConfig.geigerPreviewEnabled()) return;
        double previewRadius = RadiationConfig.particlesPreviewRadius();
        if (previewRadius <= 0.0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        ResourceKey<Level> dim = level.dimension();
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double previewSq = previewRadius * previewRadius;

        // Find the strongest nearby zone (highest level, then closest).
        RadiationZone best = null;
        double bestDistSq = Double.MAX_VALUE;
        int bestLevel = 0;
        for (RadiationZone zone : ZoneManager.all()) {
            if (!zone.dimension().equals(dim)) continue;
            double[] cp = closestDangerPoint(zone, px, py, pz);
            double d2 = cp[3];
            if (d2 > previewSq) continue;
            if (zone.level() > bestLevel || (zone.level() == bestLevel && d2 < bestDistSq)) {
                best = zone;
                bestDistSq = d2;
                bestLevel = zone.level();
            }
        }
        if (best == null) {
            nearbyGeigerTicks.remove(player.getUUID());
            return;
        }

        // Proximity 0..1 (1 = at the zone edge). Scale interval up to 5x at the far edge.
        double proximity = 1.0 - Math.sqrt(bestDistSq) / previewRadius;
        int baseInterval = RadiationConfig.geigerIntervalTicks(best.level());
        int interval = Math.max(baseInterval,
                (int) Math.round(baseInterval * (1.0 + (1.0 - proximity) * 4.0)));

        int ticks = nearbyGeigerTicks.getOrDefault(player.getUUID(), 0) + 1;
        if (ticks < interval) {
            nearbyGeigerTicks.put(player.getUUID(), ticks);
            return;
        }
        int jitter = Math.max(1, interval / 4);
        nearbyGeigerTicks.put(player.getUUID(),
                -player.getRandom().nextInt(jitter * 2 + 1) + jitter);

        // Volume also scales with proximity so distant zones tick softly.
        float volume = (float) (RadiationConfig.geigerVolume() * (0.3 + 0.7 * proximity));
        if (volume <= 0f) return;
        sendGeigerClick(player, volume);
    }

    private void tickGeigerCounter(ServerPlayer player, RadiationZone zone, State state) {
        if (!RadiationConfig.geigerEnabled()) return;
        int interval = RadiationConfig.geigerIntervalTicks(zone.level());
        state.ticksSinceGeiger++;
        if (state.ticksSinceGeiger < interval) return;
        // Add a small ±25% timing jitter so the click pattern doesn't feel metronomic.
        int jitter = Math.max(1, interval / 4);
        state.ticksSinceGeiger = -player.getRandom().nextInt(jitter * 2 + 1) + jitter;

        float volume = (float) RadiationConfig.geigerVolume();
        if (volume <= 0f) return;
        sendGeigerClick(player, volume);
    }

    /** Sends a single Geiger-style click to the given player with pitch jitter
     *  and the configured sound (with safe fallback). */
    private void sendGeigerClick(ServerPlayer player, float volume) {
        float pitch = 1.6f + (player.getRandom().nextFloat() - 0.5f) * 0.3f;
        long seed = player.getRandom().nextLong();

        net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound;
        net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> fallback =
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                        .wrapAsHolder(SoundEvents.STONE_BUTTON_CLICK_ON);
        try {
            ResourceLocation soundLoc = ResourceLocation.parse(RadiationConfig.geigerSoundId());
            java.util.Optional<net.minecraft.core.Holder.Reference<net.minecraft.sounds.SoundEvent>> opt =
                    net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getHolder(soundLoc);
            sound = opt.isPresent() ? opt.get() : fallback;
        } catch (Exception ex) {
            sound = fallback;
        }

        player.connection.send(new ClientboundSoundPacket(
                sound,
                SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, seed));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            removeState(sp);
            nearbyGeigerTicks.remove(sp.getUUID());
            WandSelections.clear(sp.getUUID());
        }
    }

    private void removeState(Player player) {
        State s = states.remove(player.getUUID());
        if (s != null && s.bar != null) {
            s.bar.removeAllPlayers();
            s.bar.setVisible(false);
        }
    }
}
