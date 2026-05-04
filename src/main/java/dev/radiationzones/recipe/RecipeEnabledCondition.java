package dev.radiationzones.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.radiationzones.config.RadiationConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Recipe condition that enables/disables a recipe based on a per-recipe
 *  config toggle. Use in JSON via:
 *  {@code "neoforge:conditions": [{"type":"radiationzones:recipe_enabled","name":"basic_filter"}]}
 *  Recognized names are listed in {@link RadiationConfig#recipeEnabled(String)}. */
public record RecipeEnabledCondition(String name) implements ICondition {
    public static final MapCodec<RecipeEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(i ->
            i.group(Codec.STRING.fieldOf("name").forGetter(RecipeEnabledCondition::name))
             .apply(i, RecipeEnabledCondition::new));

    @Override
    public boolean test(IContext context) {
        return RadiationConfig.recipeEnabled(name);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
