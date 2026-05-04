package dev.radiationzones.event;

import dev.radiationzones.config.RadiationConfig;
import dev.radiationzones.zone.RadiationZone;
import dev.radiationzones.zone.ZoneManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
            return;
        }

        applyOrUpdate(mob, Attributes.MAX_HEALTH, HEALTH_ID, RadiationConfig.mobHealthMult(zone.level()));
        applyOrUpdate(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, RadiationConfig.mobSpeedMult(zone.level()));
        applyOrUpdate(mob, Attributes.ATTACK_DAMAGE, ATTACK_ID, RadiationConfig.mobAttackMult(zone.level()));

        // Top up health if max increased
        if (mob.getHealth() < mob.getMaxHealth() && mob.tickCount % 100 == 0) {
            mob.heal(1f);
        }
    }

    private static void applyOrUpdate(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                      ResourceLocation id, double amount) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(id);
        if (existing != null && existing.amount() == amount) return;
        if (existing != null) inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void removeModifier(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                       ResourceLocation id) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        if (inst.getModifier(id) != null) {
            inst.removeModifier(id);
        }
    }
}
