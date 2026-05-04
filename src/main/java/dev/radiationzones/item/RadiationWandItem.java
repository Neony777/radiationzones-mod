package dev.radiationzones.item;

import dev.radiationzones.zone.WandSelections;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public class RadiationWandItem extends Item {
    public RadiationWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null || level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos pos = ctx.getClickedPos();
        WandSelections.Selection sel = WandSelections.get(player.getUUID());
        sel.pos2 = pos;
        player.displayClientMessage(
                Component.translatable("command.radiationzones.wand_pos2", pos.getX(), pos.getY(), pos.getZ())
                        .withStyle(ChatFormatting.AQUA),
                true);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            WandSelections.Selection sel = WandSelections.get(player.getUUID());
            sel.pos1 = pos;
            player.displayClientMessage(
                    Component.translatable("command.radiationzones.wand_pos1", pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GOLD),
                    true);
        }
        return false; // never break the block
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.radiationzones.radiation_wand").withStyle(ChatFormatting.GRAY));
    }
}
