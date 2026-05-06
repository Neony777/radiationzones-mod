package dev.radiationzones.component;

import com.mojang.serialization.Codec;
import dev.radiationzones.RadiationZones;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, RadiationZones.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<InstalledFilter>> INSTALLED_FILTER =
            COMPONENTS.registerComponentType("installed_filter", builder -> builder
                    .persistent(InstalledFilter.CODEC)
                    .networkSynchronized(InstalledFilter.STREAM_CODEC));

    /** Custom Lugol's protection duration baked into a potion stack (in seconds).
     *  When present, overrides the config's default duration on consume. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LUGOLS_DURATION =
            COMPONENTS.registerComponentType("lugols_duration", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {}
}
