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

        RadiationZone zone = ZoneManager.zoneAt(mob);
        if (zone == null) {
            removeModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID);
            removeModifier(mob, Attributes.MOVEMENT_SPEED, SPEED_ID);
            removeModifier(mob, Attributes.ATTACK_DAMAGE, ATTACK_ID);
            removeModifier(mob, Attributes.SCALE, SCALE_ID);
            removeModifier(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
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
        int effectDuration = interval + 40;
        int glowMin = RadiationConfig.mobGlowingMinLevel();
        if (glowMin > 0 && level >= glowMin) {
            applyEffect(mob, MobEffects.GLOWING, effectDuration, 0);
        }
        int strMin = RadiationConfig.mobStrengthMinLevel();
        if (strMin > 0 && level >= strMin) {
            applyEffect(mob, MobEffects.DAMAGE_BOOST, effectDuration, RadiationConfig.mobStrengthAmplifier());
        }
        int resMin = RadiationConfig.mobResistanceMinLevel();
        if (resMin > 0 && level >= resMin) {
            applyEffect(mob, MobEffects.DAMAGE_RESISTANCE, effectDuration, RadiationConfig.mobResistanceAmplifier());
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
}
