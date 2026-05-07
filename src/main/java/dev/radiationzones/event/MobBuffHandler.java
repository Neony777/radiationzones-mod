package dev.radiationzones.event;

import dev.radiationzones.config.RadiationConfig;
import dev.radiationzones.zone.RadiationZone;
import dev.radiationzones.zone.ZoneManager;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;

public class MobBuffHandler {
    private static final ResourceLocation HEALTH_ID    = ResourceLocation.fromNamespaceAndPath("radiationzones", "rad_health");
    private static final ResourceLocation SPEED_ID     = ResourceLocation.fromNamespaceAndPath("radiationzones", "rad_speed");
    private static final ResourceLocation ATTACK_ID    = ResourceLocation.fromNamespaceAndPath("radiationzones", "rad_attack");
    private static final ResourceLocation SCALE_ID     = ResourceLocation.fromNamespaceAndPath("radiationzones", "rad_scale");
    private static final ResourceLocation KNOCKBACK_ID = ResourceLocation.fromNamespaceAndPath("radiationzones", "rad_knockback");

    private static final DustParticleOptions GREEN_DUST =
            new DustParticleOptions(new Vector3f(0.2f, 1.0f, 0.2f), 0.9f);

    // Aura particles run on a separate, faster cadence than the buff check.
    // Every 8 ticks (~2.5 times/sec) is smooth enough without flooding the
    // network with per-mob particle packets.
    private static final int PARTICLE_INTERVAL = 8;

    // Maximum distance from the mob at which a player must be present for us
    // to bother sending aura particles — beyond this no client could see them.
    private static final double PARTICLE_PLAYER_RANGE = 48.0;

    @SubscribeEvent
    public void onTick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) return;
        if (!(entity instanceof Mob mob)) return;
        if (entity.level().isClientSide) return;
        if (!RadiationConfig.mobBuffsEnabled()) return;
        boolean hostile = entity instanceof Enemy;
        if (!hostile && !RadiationConfig.affectPassiveMobs()) return;

        int interval = Math.max(1, RadiationConfig.mobBuffCheckIntervalTicks());
        boolean doParticle = (entity.tickCount % PARTICLE_INTERVAL == 0);
        boolean doBuff     = (entity.tickCount % interval == 0);

        // Exit early when neither task is due — avoids the zoneAt() call entirely.
        if (!doParticle && !doBuff) return;

        // ── Single zone lookup shared by both particle and buff logic ─────────
        RadiationZone zone = ZoneManager.zoneAt(mob);

        // ── Green aura particles (every 8 ticks, player-proximity-gated) ─────
        if (doParticle && zone != null && entity.level() instanceof ServerLevel serverLevel) {
            // Skip if no player is within render distance of this mob — no one
            // would see the particles and we would just waste bandwidth.
            if (serverLevel.getNearestPlayer(mob, PARTICLE_PLAYER_RANGE) != null) {
                double hw = mob.getBbWidth() * 0.5;
                double px = mob.getX() + (mob.getRandom().nextDouble() * 2 - 1) * hw;
                double py = mob.getY() + mob.getRandom().nextDouble() * mob.getBbHeight();
                double pz = mob.getZ() + (mob.getRandom().nextDouble() * 2 - 1) * hw;
                serverLevel.sendParticles(GREEN_DUST, px, py, pz, 1, 0, 0, 0, 0);
            }
        }

        // ── Attribute & effect management (throttled by configurable interval) ─
        if (!doBuff) return;

        int effectDuration = interval + 40;

        if (zone == null) {
            removeModifier(mob, Attributes.MAX_HEALTH,         HEALTH_ID);
            removeModifier(mob, Attributes.MOVEMENT_SPEED,    SPEED_ID);
            removeModifier(mob, Attributes.ATTACK_DAMAGE,     ATTACK_ID);
            removeModifier(mob, Attributes.SCALE,             SCALE_ID);
            removeModifier(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
            removeOurEffect(mob, MobEffects.DAMAGE_BOOST,      effectDuration);
            removeOurEffect(mob, MobEffects.DAMAGE_RESISTANCE, effectDuration);
            return;
        }

        int level = zone.level();
        applyOrUpdate(mob, Attributes.MAX_HEALTH,         HEALTH_ID,     RadiationConfig.mobHealthMult(level),         AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyOrUpdate(mob, Attributes.MOVEMENT_SPEED,    SPEED_ID,      RadiationConfig.mobSpeedMult(level),           AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyOrUpdate(mob, Attributes.ATTACK_DAMAGE,     ATTACK_ID,     RadiationConfig.mobAttackMult(level),          AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyOrUpdate(mob, Attributes.SCALE,             SCALE_ID,      RadiationConfig.mobScaleBonus(level),          AttributeModifier.Operation.ADD_VALUE);
        applyOrUpdate(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, RadiationConfig.mobKnockbackResistance(level), AttributeModifier.Operation.ADD_VALUE);

        int strMin = RadiationConfig.mobStrengthMinLevel();
        if (strMin > 0 && level >= strMin) {
            applyEffect(mob, MobEffects.DAMAGE_BOOST, effectDuration, RadiationConfig.mobStrengthAmplifier());
        } else {
            removeOurEffect(mob, MobEffects.DAMAGE_BOOST, effectDuration);
        }

        int resMin = RadiationConfig.mobResistanceMinLevel();
        if (resMin > 0 && level >= resMin) {
            applyEffect(mob, MobEffects.DAMAGE_RESISTANCE, effectDuration, RadiationConfig.mobResistanceAmplifier());
        } else {
            removeOurEffect(mob, MobEffects.DAMAGE_RESISTANCE, effectDuration);
        }

        // Mark mobs persistent only up to the configured per-zone cap.
        // Once the cap is reached new mobs can still despawn normally,
        // keeping the total haunting population from growing without bound.
        if (RadiationConfig.mobsPersistent() && !mob.isPersistenceRequired()) {
            if (mob.level() instanceof ServerLevel serverLevel) {
                int cap = RadiationConfig.mobZoneCap();
                if (cap <= 0 || countBuffedMobs(serverLevel, zone) < cap) {
                    mob.setPersistenceRequired();
                }
            }
        }

        if (mob.getHealth() < mob.getMaxHealth() && mob.tickCount % 100 == 0) {
            mob.heal(1f);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Counts mobs in the zone's bounding box that already carry our health
     * attribute modifier, i.e. mobs we have confirmed-buffed. Used to enforce
     * the per-zone persistent-mob cap without a global counter.
     */
    private static int countBuffedMobs(ServerLevel level, RadiationZone zone) {
        AABB box = AABB.encapsulatingFullBlocks(zone.min(), zone.max());
        return level.getEntitiesOfClass(Mob.class, box, m -> {
            AttributeInstance inst = m.getAttribute(Attributes.MAX_HEALTH);
            return inst != null && inst.getModifier(HEALTH_ID) != null;
        }).size();
    }

    private static void applyOrUpdate(Mob mob, Holder<Attribute> attr,
                                      ResourceLocation id, double amount,
                                      AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(id);
        if (existing != null && existing.amount() == amount && existing.operation() == op) return;
        if (existing != null) inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    private static void removeModifier(Mob mob, Holder<Attribute> attr, ResourceLocation id) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        if (inst.getModifier(id) != null) inst.removeModifier(id);
    }

    private static void applyEffect(Mob mob, Holder<MobEffect> effect, int durationTicks, int amplifier) {
        MobEffectInstance current = mob.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() > durationTicks / 2) return;
        mob.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, true, false, false));
    }

    private static void removeOurEffect(Mob mob, Holder<MobEffect> effect, int ourMaxDurationTicks) {
        MobEffectInstance current = mob.getEffect(effect);
        if (current == null) return;
        if (current.getDuration() <= ourMaxDurationTicks) mob.removeEffect(effect);
    }
}
