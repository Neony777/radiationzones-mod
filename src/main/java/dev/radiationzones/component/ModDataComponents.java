package dev.radiationzones.component;

import dev.radiationzones.RadiationZones;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, RadiationZones.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<InstalledFilter>> INSTALLED_FILTER =
            COMPONENTS.registerComponentType("installed_filter", builder -> builder
                    .persistent(InstalledFilter.CODEC)
                    .networkSynchronized(InstalledFilter.STREAM_CODEC));

    private ModDataComponents() {}
}
