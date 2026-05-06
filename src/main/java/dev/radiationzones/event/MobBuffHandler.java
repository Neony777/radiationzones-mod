package dev.radiationzones.event;

import dev.radiationzones.config.RadiationConfig;
import dev.radiationzones.zone.RadiationZone;
import dev.radiationzones.zone.ZoneManager;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class MobBuffHandler {
    private static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath("radiationzones", "rad_health");
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath("radiationzones", "rad_speed");
    private static final ResourceLocation ATTACK_ID = ResourceLocation.fromNamespaceAndPath("radiationzones", "rad_attack");
    private static final ResourceLocation SCALE_ID = ResourceLocation.fromNamespaceAndPath("radiationzones", "rad_scale");
    private static final ResourceLocation KNOCKBACK_ID = ResourceLocation.fromNamespaceAndPath("radiationzones", "rad_knockback");

    @SubscribeEvent
    public void onTick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) return;
        if (!(entity instanceof Mob mob)) return;
        if (entity.level().isClientSide) return;
        if (!RadiationConfig.mobBuffsEnabled()) return;
        boolean hostile = entity instanceof Enemy;
        if (!hostile && !RadiationConfig.affectPassiveMobs()) return;
        // throttle by configurable interval
        int interval = Math.max(1, RadiationConfig.mobBuffCheckIntervalTicks());
        if (entity.tickCount % interval != 0) return;

        // Width of the refresh window — used both for re-applying effects and for
        // recognizing that a lingering effect on a mob is almost certainly ours
        // (so we can clean it up safely on zone exit / threshold drop).
        int effectDuration = interval + 40;

        RadiationZone zone = ZoneManager.zoneAt(mob);
        if (zone == null) {
            removeModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID);
            removeModifier(mob, Attributes.MOVEMENT_SPEED, SPEED_ID);
            removeModifier(mob, Attributes.ATTACK_DAMAGE, ATTACK_ID);
            removeModifier(mob, Attributes.SCALE, SCALE_ID);
            removeModifier(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
            removeOurEffect(mob, MobEffects.GLOWING, effectDuration);
            removeOurEffect(mob, MobEffects.DAMAGE_BOOST, effectDuration);
            removeOurEffect(mob, MobEffects.DAMAGE_RESISTANCE, effectDuration);
            return;
        }

        int level = zone.level();
        applyOrUpdate(mob, Attributes.MAX_HEALTH, HEALTH_ID,
                RadiationConfig.mobHealthMult(level), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyOrUpdate(mob, Attributes.MOVEMENT_SPEED, SPEED_ID,
                RadiationConfig.mobSpeedMult(level), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyOrUpdate(mob, Attributes.ATTACK_DAMAGE, ATTACK_ID,
                RadiationConfig.mobAttackMult(level), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyOrUpdate(mob, Attributes.SCALE, SCALE_ID,
                RadiationConfig.mobScaleBonus(level), AttributeModifier.Operation.ADD_VALUE);
        applyOrUpdate(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID,
                RadiationConfig.mobKnockbackResistance(level), AttributeModifier.Operation.ADD_VALUE);

        // Refresh status effects with a duration that comfortably outlives the next check.
        // If the zone's level dropped below a threshold (or threshold disabled), clean up
        // any lingering effect we previously applied so it doesn't hang on for ~12s.
        int glowMin = RadiationConfig.mobGlowingMinLevel();
        if (glowMin > 0 && level >= glowMin) {
            applyEffect(mob, MobEffects.GLOWING, effectDuration, 0);
        } else {
            removeOurEffect(mob, MobEffects.GLOWING, effectDuration);
        }
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

        // Mark buffed mobs as persistent so they don't despawn and keep haunting the area.
        if (RadiationConfig.mobsPersistent() && !mob.isPersistenceRequired()) {
            mob.setPersistenceRequired();
        }

        // Top up health if max increased
        if (mob.getHealth() < mob.getMaxHealth() && mob.tickCount % 100 == 0) {
            mob.heal(1f);
        }
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
        if (inst.getModifier(id) != null) {
            inst.removeModifier(id);
        }
    }

    private static void applyEffect(Mob mob, Holder<MobEffect> effect, int durationTicks, int amplifier) {
        MobEffectInstance current = mob.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() > durationTicks / 2) return;
        mob.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, true, false, false));
    }

    /**
     * Remove an effect from the mob only when its remaining duration is short enough
     * that it's almost certainly one we applied via {@link #applyEffect}. This avoids
     * stripping unrelated long-lived potions a player might have applied.
     */
    private static void removeOurEffect(Mob mob, Holder<MobEffect> effect, int ourMaxDurationTicks) {
        MobEffectInstance current = mob.getEffect(effect);
        if (current == null) return;
        if (current.getDuration() <= ourMaxDurationTicks) {
            mob.removeEffect(effect);
        }
    }
}
