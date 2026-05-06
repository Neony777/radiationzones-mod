package dev.radiationzones.item;

import dev.radiationzones.component.ModDataComponents;
import dev.radiationzones.config.RadiationConfig;
import dev.radiationzones.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

public class LugolsPotionItem extends Item {
    public LugolsPotionItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    /** Returns the protection duration baked into a stack, or the config default if none. */
    public static int durationSecondsFor(ItemStack stack) {
        Integer custom = stack.get(ModDataComponents.LUGOLS_DURATION.get());
        if (custom != null && custom > 0) return custom;
        return RadiationConfig.LUGOLS_DEFAULT_DURATION_SECONDS.get();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            int seconds = durationSecondsFor(stack);
            int amplifier = RadiationConfig.lugolsAmplifier();
            boolean showIcon = RadiationConfig.lugolsShowIcon();
            player.addEffect(new MobEffectInstance(ModEffects.LUGOLS_IODINE, seconds * 20, amplifier, false, showIcon, showIcon));
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1f, 1f);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.radiationzones.lugols_potion").withStyle(ChatFormatting.GRAY));
        Integer custom = stack.get(ModDataComponents.LUGOLS_DURATION.get());
        int seconds = custom != null && custom > 0
                ? custom
                : RadiationConfig.LUGOLS_DEFAULT_DURATION_SECONDS.get();
        tooltip.add(Component.translatable("tooltip.radiationzones.lugols_duration", formatDuration(seconds))
                .withStyle(ChatFormatting.BLUE));
    }

    private static String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "s";
        int m = seconds / 60;
        int s = seconds % 60;
        if (m < 60) return s == 0 ? m + "m" : m + "m " + s + "s";
        int h = m / 60;
        m = m % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
