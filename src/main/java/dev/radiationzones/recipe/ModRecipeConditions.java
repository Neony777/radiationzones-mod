package dev.radiationzones.recipe;

import com.mojang.serialization.MapCodec;
import dev.radiationzones.RadiationZones;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, RadiationZones.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<RecipeEnabledCondition>> RECIPE_ENABLED =
            CONDITIONS.register("recipe_enabled", () -> RecipeEnabledCondition.CODEC);

    private ModRecipeConditions() {}
}
