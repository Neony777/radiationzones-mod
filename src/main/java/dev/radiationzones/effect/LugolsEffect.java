package dev.radiationzones.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class LugolsEffect extends MobEffect {
    public LugolsEffect() {
        // soft cyan-ish color (#7CC8FF)
        super(MobEffectCategory.BENEFICIAL, 0x7CC8FF);
    }
}
