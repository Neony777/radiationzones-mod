package dev.radiationzones.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.radiationzones.config.RadiationConfig;
import dev.radiationzones.item.ModItems;
import dev.radiationzones.zone.RadiationZone;
import dev.radiationzones.zone.WandSelections;
import dev.radiationzones.zone.ZoneManager;
import dev.radiationzones.zone.ZoneMode;
import dev.radiationzones.zone.ZoneShape;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

public final class ModCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        // /radiation, /radiation help, etc. (no permission required)
        HelpCommand.register(d);

        d.register(Commands.literal("radiationwand")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("help")
                        .requires(s -> true)
                        .executes(ctx -> { ctx.getSource().sendSystemMessage(
                                Component.literal("See /radiation for full help").withStyle(ChatFormatting.YELLOW)); return 1; }))
                .then(Commands.literal("give")
                        .executes(ctx -> giveWand(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> giveWand(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));

        d.register(Commands.literal("radiationzone")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("help")
                        .requires(s -> true)
                        .executes(ctx -> { ctx.getSource().sendSystemMessage(
                                Component.literal("See /radiation for full help").withStyle(ChatFormatting.YELLOW)); return 1; }))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> createZone(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("createsafe")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 20))
                                        .then(Commands.argument("shape", StringArgumentType.word())
                                                .then(Commands.argument("size", IntegerArgumentType.integer(1, 1_000_000))
                                                        .executes(ctx -> createSafe(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "name"),
                                                                IntegerArgumentType.getInteger(ctx, "level"),
                                                                StringArgumentType.getString(ctx, "shape"),
                                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                                null))
                                                        .then(Commands.argument("center", BlockPosArgument.blockPos())
                                                                .executes(ctx -> createSafe(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "name"),
                                                                        IntegerArgumentType.getInteger(ctx, "level"),
                                                                        StringArgumentType.getString(ctx, "shape"),
                                                                        IntegerArgumentType.getInteger(ctx, "size"),
                                                                        BlockPosArgument.getBlockPos(ctx, "center")))))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> removeZone(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listZones(ctx.getSource())))
                .then(Commands.literal("setspread")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("blocksPerMinute", DoubleArgumentType.doubleArg(0.0, 1000.0))
                                        .executes(ctx -> setSpread(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                DoubleArgumentType.getDouble(ctx, "blocksPerMinute"))))))
                .then(Commands.literal("setdecay")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("blocksPerMinute", DoubleArgumentType.doubleArg(0.0, 1000.0))
                                        .executes(ctx -> setDecay(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                DoubleArgumentType.getDouble(ctx, "blocksPerMinute"))))))
                .then(Commands.literal("setleveldecay")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("minutesPerLevel", DoubleArgumentType.doubleArg(0.0, 100000.0))
                                        .executes(ctx -> setLevelDecay(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                DoubleArgumentType.getDouble(ctx, "minutesPerLevel"))))))
                .then(Commands.literal("setmaxradius")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 100000))
                                        .executes(ctx -> setMaxRadius(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "blocks"))))))
                .then(Commands.literal("setdrift")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("dx", DoubleArgumentType.doubleArg(-1000.0, 1000.0))
                                        .then(Commands.argument("dy", DoubleArgumentType.doubleArg(-1000.0, 1000.0))
                                                .then(Commands.argument("dz", DoubleArgumentType.doubleArg(-1000.0, 1000.0))
                                                        .executes(ctx -> setDrift(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "name"),
                                                                DoubleArgumentType.getDouble(ctx, "dx"),
                                                                DoubleArgumentType.getDouble(ctx, "dy"),
                                                                DoubleArgumentType.getDouble(ctx, "dz"))))))))
                .then(Commands.literal("setmode")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .executes(ctx -> setMode(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "mode")))))));

        d.register(Commands.literal("lugols")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("help")
                        .requires(s -> true)
                        .executes(ctx -> { ctx.getSource().sendSystemMessage(
                                Component.literal("See /radiation for full help").withStyle(ChatFormatting.YELLOW)); return 1; }))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> givePotion(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        RadiationConfig.LUGOLS_DEFAULT_DURATION_SECONDS.get(),
                                        1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> givePotion(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                RadiationConfig.LUGOLS_DEFAULT_DURATION_SECONDS.get(),
                                                IntegerArgumentType.getInteger(ctx, "count")))))));
    }

    private static int giveWand(CommandSourceStack source, ServerPlayer target) {
        ItemStack stack = new ItemStack(ModItems.RADIATION_WAND.get());
        if (!target.getInventory().add(stack)) target.drop(stack, false);
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.gave_wand", target.getName())
                        .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int givePotion(CommandSourceStack source, ServerPlayer target, int durationSeconds, int count) {
        ItemStack stack = new ItemStack(ModItems.LUGOLS_POTION.get(), count);
        if (!target.getInventory().add(stack)) target.drop(stack, false);
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.gave_potion", target.getName())
                        .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int createZone(CommandSourceStack source, String name, int level) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WandSelections.Selection sel = WandSelections.peek(player.getUUID());
        if (sel == null || sel.pos1 == null || sel.pos2 == null) {
            source.sendFailure(Component.translatable("command.radiationzones.no_selection"));
            return 0;
        }
        if (ZoneManager.get(name).isPresent()) {
            source.sendFailure(Component.translatable("command.radiationzones.zone_exists", name));
            return 0;
        }
        RadiationZone zone = RadiationZone.of(name, player.level().dimension(), sel.pos1, sel.pos2, level);
        ZoneManager.add(zone);
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.zone_created", name, level)
                        .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int createSafe(CommandSourceStack source, String name, int level,
                                  String shapeStr, int size, BlockPos centerOrNull) throws CommandSyntaxException {
        if (ZoneManager.get(name).isPresent()) {
            source.sendFailure(Component.translatable("command.radiationzones.zone_exists", name));
            return 0;
        }
        ServerLevel level3d;
        BlockPos center;
        if (centerOrNull == null) {
            ServerPlayer player = source.getPlayerOrException();
            center = player.blockPosition();
            level3d = player.serverLevel();
        } else {
            center = centerOrNull;
            level3d = source.getLevel();
        }
        ZoneShape shape;
        try { shape = ZoneShape.fromString(shapeStr); }
        catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal(ex.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        int worldMinY = level3d.getMinBuildHeight();
        int worldMaxY = level3d.getMaxBuildHeight() - 1;
        RadiationZone zone = RadiationZone.safeZone(name, level3d.dimension(),
                center, shape, size, level, worldMinY, worldMaxY);
        ZoneManager.add(zone);
        String shapeLabel = shape.name().toLowerCase(Locale.ROOT);
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.zone_safe_created",
                        name, shapeLabel, size, level)
                        .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int removeZone(CommandSourceStack source, String name) {
        if (ZoneManager.remove(name)) {
            source.sendSuccess(() ->
                    Component.translatable("command.radiationzones.zone_removed", name)
                            .withStyle(ChatFormatting.YELLOW), true);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFailure(Component.translatable("command.radiationzones.zone_not_found", name));
        return 0;
    }

    private static int setSpread(CommandSourceStack source, String name, double blocksPerMinute) {
        Optional<RadiationZone> opt = ZoneManager.get(name);
        if (opt.isEmpty()) {
            source.sendFailure(Component.translatable("command.radiationzones.zone_not_found", name));
            return 0;
        }
        RadiationZone z = opt.get();
        z.setSpreadRate(blocksPerMinute);
        z.setLastUpdateMillis(System.currentTimeMillis());
        ZoneManager.save();
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.set_spread", name, blocksPerMinute)
                        .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setDecay(CommandSourceStack source, String name, double blocksPerMinute) {
        Optional<RadiationZone> opt = ZoneManager.get(name);
        if (opt.isEmpty()) {
            source.sendFailure(Component.translatable("command.radiationzones.zone_not_found", name));
            return 0;
        }
        RadiationZone z = opt.get();
        z.setDecayRate(blocksPerMinute);
        z.setLastUpdateMillis(System.currentTimeMillis());
        ZoneManager.save();
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.set_decay", name, blocksPerMinute)
                        .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setLevelDecay(CommandSourceStack source, String name, double minutesPerLevel) {
        Optional<RadiationZone> opt = ZoneManager.get(name);
        if (opt.isEmpty()) {
            source.sendFailure(Component.translatable("command.radiationzones.zone_not_found", name));
            return 0;
        }
        RadiationZone z = opt.get();
        z.setLevelDecayMinutes(minutesPerLevel);
        z.setLastLevelDecayMillis(System.currentTimeMillis());
        ZoneManager.save();
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.set_level_decay", name, minutesPerLevel)
                        .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setMaxRadius(CommandSourceStack source, String name, int blocks) {
        Optional<RadiationZone> opt = ZoneManager.get(name);
        if (opt.isEmpty()) {
            source.sendFailure(Component.translatable("command.radiationzones.zone_not_found", name));
            return 0;
        }
        RadiationZone z = opt.get();
        z.setMaxRadius(blocks);
        ZoneManager.save();
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.set_max_radius", name, blocks)
                        .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setDrift(CommandSourceStack source, String name, double dx, double dy, double dz) {
        Optional<RadiationZone> opt = ZoneManager.get(name);
        if (opt.isEmpty()) {
            source.sendFailure(Component.translatable("command.radiationzones.zone_not_found", name));
            return 0;
        }
        RadiationZone z = opt.get();
        z.setDrift(dx, dy, dz);
        z.setLastDriftMillis(System.currentTimeMillis());
        ZoneManager.save();
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.set_drift", name, dx, dy, dz)
                        .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setMode(CommandSourceStack source, String name, String modeStr) {
        Optional<RadiationZone> opt = ZoneManager.get(name);
        if (opt.isEmpty()) {
            source.sendFailure(Component.translatable("command.radiationzones.zone_not_found", name));
            return 0;
        }
        RadiationZone z = opt.get();
        ZoneMode m;
        try { m = ZoneMode.fromString(modeStr); }
        catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal(ex.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        z.setMode(m);
        ZoneManager.save();
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.set_mode", name, m.name().toLowerCase(Locale.ROOT))
                        .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int listZones(CommandSourceStack source) {
        Collection<RadiationZone> all = ZoneManager.all();
        source.sendSuccess(() ->
                Component.translatable("command.radiationzones.list_header", all.size())
                        .withStyle(ChatFormatting.AQUA), false);
        for (RadiationZone z : all) {
            BlockPos a = z.min();
            BlockPos b = z.max();
            source.sendSuccess(() -> Component.translatable(
                    "command.radiationzones.list_entry",
                    z.name(), z.level(),
                    a.getX(), a.getY(), a.getZ(),
                    b.getX(), b.getY(), b.getZ()
            ), false);
            if (z.mode() != ZoneMode.INSIDE || z.shape() != ZoneShape.SQUARE) {
                source.sendSuccess(() -> Component.translatable(
                        "command.radiationzones.list_safe",
                        z.mode().name().toLowerCase(Locale.ROOT),
                        z.shape().name().toLowerCase(Locale.ROOT)
                ).withStyle(ChatFormatting.GRAY), false);
            }
            if (z.driftX() != 0 || z.driftY() != 0 || z.driftZ() != 0) {
                source.sendSuccess(() -> Component.translatable(
                        "command.radiationzones.list_drift",
                        z.driftX(), z.driftY(), z.driftZ()
                ).withStyle(ChatFormatting.GRAY), false);
            }
            if (z.spreadRate() > 0 || z.decayRate() > 0 || z.levelDecayMinutes() > 0 || z.maxRadius() > 0) {
                source.sendSuccess(() -> Component.translatable(
                        "command.radiationzones.list_dynamic",
                        z.spreadRate(), z.decayRate(), z.levelDecayMinutes(), z.maxRadius()
                ).withStyle(ChatFormatting.GRAY), false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private ModCommands() {}
}
