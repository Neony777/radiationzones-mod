package dev.radiationzones.client;

import dev.radiationzones.component.InstalledFilter;
import dev.radiationzones.component.ModDataComponents;
import dev.radiationzones.item.FilterItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Client-side render hook for the gas mask helmet. Returns a custom
 * HumanoidModel whose head bone has the gas mask geometry, and toggles the
 * canister cube based on whether a filter is installed.
 *
 * If the model fails to load (e.g. resource pack missing), we fall back to
 * the default helmet model the renderer passed in so the player still sees
 * a hat instead of a crash.
 */
public final class GasMaskClientExtensions implements IClientItemExtensions {

    public static final GasMaskClientExtensions INSTANCE = new GasMaskClientExtensions();

    private GasMaskModel cachedModel;

    private GasMaskModel getOrCreateModel() {
        if (cachedModel == null) {
            try {
                cachedModel = new GasMaskModel(
                        Minecraft.getInstance().getEntityModels().bakeLayer(GasMaskModel.LAYER));
            } catch (Throwable t) {
                cachedModel = null;
            }
        }
        return cachedModel;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                   EquipmentSlot slot, HumanoidModel<?> defaultModel) {
        if (slot != EquipmentSlot.HEAD) return defaultModel;
        GasMaskModel model = getOrCreateModel();
        if (model == null) return defaultModel;
        // Copy pose from the default humanoid model so the head turns/tilts
        // with the player. The wildcard generics force a raw cast here.
        ((HumanoidModel) defaultModel).copyPropertiesTo(model);
        InstalledFilter filter = stack.get(ModDataComponents.INSTALLED_FILTER.get());
        model.setFiltered(filter != null);
        model.setFilterTint(tintFor(filter));
        return model;
    }

    /** Resolve the canister tint for the given installed filter. Returns
     *  {@code 0xFFFFFFFF} (no tint) when no filter is installed or the
     *  filter id does not resolve to a {@link FilterItem}. */
    private static int tintFor(InstalledFilter filter) {
        if (filter == null) return 0xFFFFFFFF;
        Item item = BuiltInRegistries.ITEM.get(filter.filterId());
        return (item instanceof FilterItem fi) ? fi.tier().tintColor : 0xFFFFFFFF;
    }

    @Override
    public Model getGenericArmorModel(LivingEntity entity, ItemStack stack,
                                       EquipmentSlot slot, HumanoidModel<?> defaultModel) {
        return getHumanoidArmorModel(entity, stack, slot, defaultModel);
    }

    private GasMaskClientExtensions() {}
}
