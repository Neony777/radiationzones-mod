package dev.radiationzones.effect;

import dev.radiationzones.RadiationZones;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, RadiationZones.MOD_ID);

    public static final DeferredHolder<MobEffect, LugolsEffect> LUGOLS_IODINE =
            EFFECTS.register("lugols_iodine", LugolsEffect::new);

    private ModEffects() {}
}
