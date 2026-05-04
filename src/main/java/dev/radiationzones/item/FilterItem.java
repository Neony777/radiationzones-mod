package dev.radiationzones.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class FilterItem extends Item {
    public enum Tier {
        /** Cheap. Blocks 60% of radiation damage; 40% still leaks through. */
        BASIC(200, 0.6f, false, 0xFFB5B5B5),
        /** Mid-tier. Full radiation protection. */
        INDUSTRIAL(800, 1.0f, false, 0xFFE08A2E),
        /** Best in slot. Full radiation protection + brief Resistance I "overflow"
         *  damage reduction on every filter tick (helps versus collateral hits). */
        HAZMAT(2400, 1.0f, true, 0xFFBFE83A);

        public final int maxDurability;
        /** Fraction of radiation damage absorbed by the filter (0..1). */
        public final float blockFraction;
        /** True when this filter grants a brief Resistance I bonus every tick of use. */
        public final boolean overflowResistance;
        /** ARGB color used to tint the worn-mask filter canister and the
         *  inventory icon's canister overlay layer. Pure white (0xFFFFFFFF)
         *  means "no tint". */
        public final int tintColor;

        Tier(int maxDurability, float blockFraction, boolean overflowResistance, int tintColor) {
            this.maxDurability = maxDurability;
            this.blockFraction = blockFraction;
            this.overflowResistance = overflowResistance;
            this.tintColor = tintColor;
        }
    }

    private final Tier tier;

    public FilterItem(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
    }

    public Tier tier() { return tier; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.radiationzones.filter." + tier.name().toLowerCase())
                .withStyle(ChatFormatting.GRAY));
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        tooltip.add(Component.translatable("tooltip.radiationzones.filter_durability",
                remaining, stack.getMaxDamage()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
