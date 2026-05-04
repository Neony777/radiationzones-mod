package dev.radiationzones.item;

import dev.radiationzones.RadiationZones;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RadiationZones.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RADIATION_TAB =
            TABS.register("radiation_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.radiationzones"))
                    .icon(() -> new ItemStack(ModItems.RADIATION_WAND.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.RADIATION_WAND.get());
                        output.accept(ModItems.LUGOLS_POTION.get());
                        output.accept(ModItems.GAS_MASK.get());
                        output.accept(ModItems.BASIC_FILTER.get());
                        output.accept(ModItems.INDUSTRIAL_FILTER.get());
                        output.accept(ModItems.HAZMAT_FILTER.get());
                    })
                    .build()
            );

    private ModCreativeTab() {}
}
