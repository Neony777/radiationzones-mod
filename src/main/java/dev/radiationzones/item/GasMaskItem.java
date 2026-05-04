package dev.radiationzones.item;

import dev.radiationzones.component.InstalledFilter;
import dev.radiationzones.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class GasMaskItem extends ArmorItem {

    public GasMaskItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, Type.HELMET, properties);
    }

    /**
     * Right-click logic:
     *  - sneaking: eject installed filter (drops/gives back to player with stored damage).
     *  - holding mask in main hand, off-hand has a FilterItem and mask has no filter:
     *      install the filter.
     *  - otherwise fall through to normal armor equip.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack mask = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (mask.has(ModDataComponents.INSTALLED_FILTER.get())) {
                if (!level.isClientSide) {
                    ItemStack filterStack = ejectFilterStack(mask);
                    if (filterStack != null && !player.getInventory().add(filterStack)) {
                        player.drop(filterStack, false);
                    }
                    mask.remove(ModDataComponents.INSTALLED_FILTER.get());
                    level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                            SoundSource.PLAYERS, 0.8f, 1.4f);
                }
                return InteractionResultHolder.sidedSuccess(mask, level.isClientSide);
            }
        } else {
            InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack offhand = player.getItemInHand(other);
            if (offhand.getItem() instanceof FilterItem filter
                    && !mask.has(ModDataComponents.INSTALLED_FILTER.get())) {
                if (!level.isClientSide) {
                    int max = filter.tier().maxDurability;
                    int currentDur = max - offhand.getDamageValue();
                    if (currentDur <= 0) {
                        return InteractionResultHolder.fail(mask);
                    }
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(filter);
                    mask.set(ModDataComponents.INSTALLED_FILTER.get(),
                            new InstalledFilter(id, currentDur, max));
                    offhand.shrink(1);
                    level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                            SoundSource.PLAYERS, 0.8f, 1.0f);
                }
                return InteractionResultHolder.sidedSuccess(mask, level.isClientSide);
            }
        }

        // Fallback: vanilla armor swap.
        return super.use(level, player, hand);
    }

    /** Returns a damaged FilterItem stack representing the installed filter, or null. */
    public static ItemStack ejectFilterStack(ItemStack mask) {
        InstalledFilter f = mask.get(ModDataComponents.INSTALLED_FILTER.get());
        if (f == null) return null;
        Item item = BuiltInRegistries.ITEM.get(f.filterId());
        if (!(item instanceof FilterItem)) return null;
        ItemStack stack = new ItemStack(item);
        int dmg = Math.max(0, f.maxDurability() - f.durability());
        stack.setDamageValue(Math.min(stack.getMaxDamage(), dmg));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        InstalledFilter f = stack.get(ModDataComponents.INSTALLED_FILTER.get());
        if (f == null) {
            tooltip.add(Component.translatable("tooltip.radiationzones.gas_mask_empty").withStyle(ChatFormatting.GRAY));
        } else {
            Item filterItem = BuiltInRegistries.ITEM.get(f.filterId());
            tooltip.add(Component.translatable("tooltip.radiationzones.gas_mask_filter",
                            Component.translatable(filterItem.getDescriptionId()),
                            f.durability(), f.maxDurability())
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.radiationzones.gas_mask_help").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** True when the helmet stack has a filter with remaining durability. */
    public static boolean hasUsableFilter(ItemStack stack) {
        InstalledFilter f = stack.get(ModDataComponents.INSTALLED_FILTER.get());
        return f != null && f.durability() > 0;
    }

    /**
     * Resolve and consume one filter tick, returning the tier that absorbed the hit
     * (or null if no usable filter was installed). The caller is responsible for
     * applying any leftover damage based on {@code tier.blockFraction}.
     */
    public static FilterItem.Tier consumeAndGetTier(ItemStack stack, int wear) {
        InstalledFilter f = stack.get(ModDataComponents.INSTALLED_FILTER.get());
        if (f == null || f.durability() <= 0) return null;
        Item item = BuiltInRegistries.ITEM.get(f.filterId());
        if (!(item instanceof FilterItem fi)) return null;
        InstalledFilter updated = f.withDurability(f.durability() - wear);
        if (updated.isEmpty()) {
            stack.remove(ModDataComponents.INSTALLED_FILTER.get());
        } else {
            stack.set(ModDataComponents.INSTALLED_FILTER.get(), updated);
        }
        return fi.tier();
    }

    /** Damage the mask itself. Wraps hurtAndBreak for the head slot. */
    public static void damageMask(ItemStack stack, LivingEntity entity, int amount) {
        if (stack.getMaxDamage() <= 0) return;
        stack.hurtAndBreak(amount, entity, EquipmentSlot.HEAD);
    }
}
