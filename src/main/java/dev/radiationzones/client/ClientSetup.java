package dev.radiationzones.client;

import dev.radiationzones.RadiationZones;
import dev.radiationzones.component.InstalledFilter;
import dev.radiationzones.component.ModDataComponents;
import dev.radiationzones.item.FilterItem;
import dev.radiationzones.item.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = RadiationZones.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.GAS_MASK.get(),
                ResourceLocation.fromNamespaceAndPath(RadiationZones.MOD_ID, "filter_installed"),
                (stack, level, entity, seed) ->
                        stack.has(ModDataComponents.INSTALLED_FILTER.get()) ? 1f : 0f
        ));
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GasMaskModel.LAYER, GasMaskModel::createLayer);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(GasMaskClientExtensions.INSTANCE, ModItems.GAS_MASK.get());
    }

    /**
     * Tints layer 1 of the gas_mask_filtered inventory icon (the canister
     * overlay) according to the installed filter's tier color. Layer 0 (the
     * base mask) is always rendered untinted.
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return 0xFFFFFFFF;
            InstalledFilter filter = stack.get(ModDataComponents.INSTALLED_FILTER.get());
            if (filter == null) return 0xFFFFFFFF;
            Item filterItem = BuiltInRegistries.ITEM.get(filter.filterId());
            return (filterItem instanceof FilterItem fi) ? fi.tier().tintColor : 0xFFFFFFFF;
        }, ModItems.GAS_MASK.get());
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, GasMaskOverlay.ID, GasMaskOverlay.INSTANCE);
    }

    private ClientSetup() {}
}
