package dev.radiationzones.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * In-game help for RadiationZones.
 *
 * Single-page on purpose: an earlier multi-page version (/radiation wand,
 * /radiation zones, ...) collided with the actual admin commands
 * (/radiationwand, /radiationzone) and was confusing.
 *
 * Anyone may run /radiation; admin sections are clearly marked.
 */
public final class HelpCommand {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("radiation")
                .executes(ctx -> sendHelp(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> sendHelp(ctx.getSource()))));

        // Friendly aliases that don't clash with /radiationwand or /radiationzone.
        d.register(Commands.literal("radhelp").executes(ctx -> sendHelp(ctx.getSource())));
        d.register(Commands.literal("radiationhelp").executes(ctx -> sendHelp(ctx.getSource())));
    }

    private static int sendHelp(CommandSourceStack src) {
        send(src, blank());
        send(src, header("RadiationZones — Help"));
        send(src, body("Type ").append(yellow("/radiation"))
                .append(body(" any time to reopen this menu. Vanilla "))
                .append(yellow("/help radiation"))
                .append(body(" works too.")));
        send(src, blank());

        // ---------- PLAYER section ----------
        send(src, section("[ For everyone ]"));
        send(src, body(" • Inside a radiation zone you'll see a boss bar:"));
        send(src, body("     ").append(coloured("YELLOW ", ChatFormatting.YELLOW))
                .append(body("= grace period countdown")));
        send(src, body("     ").append(coloured("RED    ", ChatFormatting.RED))
                .append(body("= taking damage")));
        send(src, body("     ").append(coloured("BLUE   ", ChatFormatting.BLUE))
                .append(body("= protected by Lugol's Iodine")));
        send(src, body(" • Drinking ").append(yellow("Lugol's Potion"))
                .append(body(" stops the damage for ~10 minutes (configurable).")));
        send(src, body(" • Particles & geiger ticks still play while protected — only damage stops."));
        send(src, blank());

        // ---------- ADMIN warning ----------
        send(src, Component.literal("[ Admin commands — REQUIRE OP / permission level 2 ]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)));
        send(src, body("If a command below shows ").append(coloured("\"Unknown or incomplete command\"",
                ChatFormatting.RED)).append(body(", you most likely lack OP permission.")));
        send(src, body("Note: ").append(coloured("<player>", ChatFormatting.YELLOW))
                .append(body(" arguments require the player to be ONLINE on the server.")));
        send(src, blank());

        // ---------- Wand ----------
        send(src, section("Wand & zone selection:"));
        send(src, link("  /radiationwand give",
                "Click to insert. Gives you the wand.",
                "/radiationwand give"));
        send(src, link("  /radiationwand give <player>",
                "Give a wand to another online player",
                "/radiationwand give "));
        send(src, body("  Then ").append(coloured("LEFT-click", ChatFormatting.GOLD))
                .append(body(" any block = pos 1, ")).append(coloured("RIGHT-click", ChatFormatting.AQUA))
                .append(body(" any block = pos 2.")));
        send(src, body("  The wand never breaks blocks. Selections persist until logout.")
                .withStyle(ChatFormatting.DARK_GRAY));
        send(src, blank());

        // ---------- Zone CRUD ----------
        send(src, section("Create / list / remove zones:"));
        send(src, link("  /radiationzone create <name> <level>",
                "Create a hazard zone from your wand selection (radiation INSIDE).",
                "/radiationzone create myzone 3"));
        send(src, link("  /radiationzone createsafe <name> <level> <square|circle> <size> [x y z]",
                "Create a safe zone (radiation OUTSIDE the bounds — world-border style). "
                        + "Size = full side length for square, radius for circle. "
                        + "Defaults to your position when coords omitted.",
                "/radiationzone createsafe spawn 3 square 1000 0 64 0"));
        send(src, link("  /radiationzone list",
                "List every zone in the world",
                "/radiationzone list"));
        send(src, link("  /radiationzone remove <name>",
                "Delete a zone by name",
                "/radiationzone remove "));
        send(src, blank());

        // ---------- Zone dynamics ----------
        send(src, section("Dynamic spread / decay (per zone):"));
        send(src, link("  /radiationzone setspread <name> <blocksPerMinute>",
                "Grow the zone outward over time (expand for safe zones)",
                "/radiationzone setspread myzone 1.0"));
        send(src, link("  /radiationzone setdecay <name> <blocksPerMinute>",
                "Shrink the zone inward over time (close in for safe zones)",
                "/radiationzone setdecay myzone 0.5"));
        send(src, link("  /radiationzone setleveldecay <name> <minutesPerLevel>",
                "Drop the danger level by 1 every N minutes (0 disables)",
                "/radiationzone setleveldecay myzone 30"));
        send(src, link("  /radiationzone setmaxradius <name> <blocks>",
                "Cap how far the zone may spread from its origin (0 = unlimited)",
                "/radiationzone setmaxradius myzone 32"));
        send(src, link("  /radiationzone setdrift <name> <dx> <dy> <dz>",
                "Drift the bounds at <dx,dy,dz> blocks/minute on each axis (e.g. 0.07 0 0 ≈ 100 blocks/day east)",
                "/radiationzone setdrift spawn 0.07 0 0"));
        send(src, link("  /radiationzone setmode <name> <inside|outside>",
                "Flip an existing zone between inclusive (hazard) and exclusive (safe) modes",
                "/radiationzone setmode myzone outside"));
        send(src, body("  Net change = spread − decay. Any zone is removed when its level reaches 0. OUTSIDE zones are kept on geometric collapse (whole-dimension irradiation).")
                .withStyle(ChatFormatting.DARK_GRAY));
        send(src, blank());

        // ---------- Potion ----------
        send(src, section("Lugol's Iodine Potion:"));
        send(src, link("  /lugols give <player>",
                "Give 1 potion (player must be online)",
                "/lugols give "));
        send(src, link("  /lugols give <player> <count>",
                "Give N potions (1-64)",
                "/lugols give "));
        send(src, blank());

        // ---------- Config ----------
        send(src, section("Configuration:"));
        send(src, body("  File: ").append(yellow("<world>/serverconfig/radiationzones-common.toml")));
        send(src, body("  Sections: [levels] [general] [lugols] [mobs] [particles] [geiger]"));
        send(src, body("  Per-level lists must have at least 'maxLevel' entries (default 5).")
                .withStyle(ChatFormatting.DARK_GRAY));

        return Command.SINGLE_SUCCESS;
    }

    /* --------------------------------------------------------------------- */
    /* Helpers                                                               */
    /* --------------------------------------------------------------------- */

    private static MutableComponent header(String text) {
        return Component.literal("=== " + text + " ===")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true));
    }

    private static MutableComponent section(String text) {
        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }

    private static MutableComponent body(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent yellow(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    private static MutableComponent coloured(String text, ChatFormatting c) {
        return Component.literal(text).withStyle(c);
    }

    private static MutableComponent blank() {
        return Component.literal("");
    }

    /** Clickable: shows label, suggests a command on click. */
    private static MutableComponent link(String label, String hover, String commandToInsert) {
        Style style = Style.EMPTY
                .withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandToInsert))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(hover).withStyle(ChatFormatting.GRAY)));
        return Component.literal(label).withStyle(style);
    }

    private static void send(CommandSourceStack src, Component msg) {
        src.sendSuccess(() -> msg, false);
    }

    private HelpCommand() {}
}
