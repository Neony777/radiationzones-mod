package dev.radiationzones.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class RadiationConfig {
    public static final ModConfigSpec SPEC;

    // --- per-level player effects ---
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> GRACE_PERIOD_SECONDS;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> DAMAGE_AMOUNT;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> DAMAGE_INTERVAL_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> MOB_HEALTH_MULT;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> MOB_SPEED_MULT;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> MOB_ATTACK_MULT;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> MOB_SCALE_BONUS;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> MOB_KNOCKBACK_RESIST;

    // --- general ---
    public static final ModConfigSpec.IntValue MAX_LEVEL;
    public static final ModConfigSpec.BooleanValue CREATIVE_IMMUNE;
    public static final ModConfigSpec.BooleanValue SPECTATOR_IMMUNE;
    public static final ModConfigSpec.BooleanValue BOSS_BAR_VISIBLE;
    public static final ModConfigSpec.IntValue MIN_DEPTH_AFFECTED;

    // --- lugols ---
    public static final ModConfigSpec.IntValue LUGOLS_DEFAULT_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue LUGOLS_AMPLIFIER;
    public static final ModConfigSpec.BooleanValue LUGOLS_SHOW_ICON;

    // --- mobs ---
    public static final ModConfigSpec.BooleanValue MOB_BUFFS_ENABLED;
    public static final ModConfigSpec.BooleanValue AFFECT_PASSIVE_MOBS;
    public static final ModConfigSpec.IntValue MOB_BUFF_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue MOB_PERSISTENT;
    public static final ModConfigSpec.IntValue MOB_GLOWING_MIN_LEVEL;
    public static final ModConfigSpec.IntValue MOB_STRENGTH_MIN_LEVEL;
    public static final ModConfigSpec.IntValue MOB_RESISTANCE_MIN_LEVEL;
    public static final ModConfigSpec.IntValue MOB_STRENGTH_AMPLIFIER;
    public static final ModConfigSpec.IntValue MOB_RESISTANCE_AMPLIFIER;
    public static final ModConfigSpec.IntValue MOB_ZONE_CAP;

    // --- particles ---
    public static final ModConfigSpec.BooleanValue PARTICLES_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> PARTICLES_PER_TICK;
    public static final ModConfigSpec.DoubleValue PARTICLES_RADIUS;
    public static final ModConfigSpec.DoubleValue PARTICLES_PREVIEW_RADIUS;

    // --- geiger ---
    public static final ModConfigSpec.BooleanValue GEIGER_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> GEIGER_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue GEIGER_VOLUME;
    public static final ModConfigSpec.ConfigValue<String> GEIGER_SOUND_ID;
    public static final ModConfigSpec.BooleanValue GEIGER_PREVIEW_ENABLED;

    // --- particle styling (additional) ---
    public static final ModConfigSpec.ConfigValue<String> PARTICLE_COLOR_RGB;
    public static final ModConfigSpec.BooleanValue PARTICLE_SMOKE_ACCENT;
    public static final ModConfigSpec.BooleanValue PARTICLE_PREVIEW_ENABLED;

    // --- client-side immersion ---
    public static final ModConfigSpec.BooleanValue GAS_MASK_OVERLAY_ENABLED;

    // --- recipe toggles ---
    public static final ModConfigSpec.BooleanValue RECIPE_GAS_MASK;
    public static final ModConfigSpec.BooleanValue RECIPE_BASIC_FILTER;
    public static final ModConfigSpec.BooleanValue RECIPE_INDUSTRIAL_FILTER;
    public static final ModConfigSpec.BooleanValue RECIPE_HAZMAT_FILTER;
    public static final ModConfigSpec.BooleanValue RECIPE_LUGOLS_POTION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Per-level radiation tuning. Index 0 = level 1, Index 1 = level 2, etc.").push("levels");

        GRACE_PERIOD_SECONDS = builder
                .comment("Seconds inside a zone before damage starts (per level).")
                .defineList("gracePeriodSeconds",
                        List.of(60, 45, 30, 15, 5),
                        () -> 30,
                        o -> o instanceof Integer i && i >= 0);

        DAMAGE_AMOUNT = builder
                .comment("Damage in half-hearts per tick application (per level). 1.0 = half a heart.")
                .defineList("damageAmount",
                        List.of(1.0, 2.0, 3.0, 4.0, 6.0),
                        () -> 1.0,
                        o -> o instanceof Double d && d >= 0.0);

        DAMAGE_INTERVAL_TICKS = builder
                .comment("Ticks between damage applications (per level). 20 ticks = 1 second.")
                .defineList("damageIntervalTicks",
                        List.of(60, 40, 30, 20, 10),
                        () -> 40,
                        o -> o instanceof Integer i && i > 0);

        MOB_HEALTH_MULT = builder
                .comment("Multiplier added to hostile mob max health (per level). 0.5 = +50%.")
                .defineList("mobHealthMultiplier",
                        List.of(0.25, 0.5, 1.0, 1.5, 2.0),
                        () -> 0.5,
                        o -> o instanceof Double d && d >= 0.0);

        MOB_SPEED_MULT = builder
                .comment("Multiplier added to hostile mob movement speed (per level).")
                .defineList("mobSpeedMultiplier",
                        List.of(0.1, 0.15, 0.2, 0.3, 0.4),
                        () -> 0.15,
                        o -> o instanceof Double d && d >= 0.0);

        MOB_ATTACK_MULT = builder
                .comment("Multiplier added to hostile mob attack damage (per level).")
                .defineList("mobAttackMultiplier",
                        List.of(0.25, 0.5, 0.75, 1.0, 1.5),
                        () -> 0.5,
                        o -> o instanceof Double d && d >= 0.0);

        MOB_SCALE_BONUS = builder
                .comment("Bonus added to mob scale attribute (per level). 0.05 = +5% size, 0.6 = +60% size. Vanilla 1.0 = normal size.")
                .defineList("mobScaleBonus",
                        List.of(0.05, 0.10, 0.20, 0.35, 0.60),
                        () -> 0.10,
                        o -> o instanceof Double d && d >= -0.5 && d <= 5.0);

        MOB_KNOCKBACK_RESIST = builder
                .comment("Knockback resistance added to mobs (per level). 0.0 = none, 1.0 = immovable. Higher = harder to knock back, mutated mobs feel heavier.")
                .defineList("mobKnockbackResistance",
                        List.of(0.10, 0.20, 0.40, 0.60, 0.80),
                        () -> 0.20,
                        o -> o instanceof Double d && d >= 0.0 && d <= 1.0);

        builder.pop();

        builder.comment("General behavior toggles.").push("general");
        MAX_LEVEL = builder
                .comment("Maximum allowed radiation level. Must match the length of the per-level lists.")
                .defineInRange("maxLevel", 5, 1, 20);
        CREATIVE_IMMUNE = builder
                .comment("If true, players in Creative mode are completely immune to radiation.")
                .define("creativeImmune", true);
        SPECTATOR_IMMUNE = builder
                .comment("If true, players in Spectator mode are completely immune to radiation.")
                .define("spectatorImmune", true);
        BOSS_BAR_VISIBLE = builder
                .comment("If false, the radiation boss bar is never shown to players.")
                .define("bossBarVisible", true);
        MIN_DEPTH_AFFECTED = builder
                .comment("Players standing below this Y level inside a zone get half radiation damage (simulating shielding from terrain). Set to a very low number like -1024 to disable.")
                .defineInRange("shieldingYBelow", -1024, -1024, 1024);
        builder.pop();

        builder.comment("Lugol's Iodine Potion settings.").push("lugols");
        LUGOLS_DEFAULT_DURATION_SECONDS = builder
                .comment("Default protection duration (in seconds) granted by drinking Lugol's Potion.")
                .defineInRange("defaultDurationSeconds", 600, 1, 86400);
        LUGOLS_AMPLIFIER = builder
                .comment("Effect amplifier applied when drinking Lugol's. 0 = level I, 1 = level II, etc. Vanity only — protection is binary either way.")
                .defineInRange("amplifier", 0, 0, 9);
        LUGOLS_SHOW_ICON = builder
                .comment("If true, the Lugol's effect shows an icon in the player's inventory and HUD.")
                .define("showIcon", true);
        builder.pop();

        builder.comment("Hostile-mob buff settings.").push("mobs");
        MOB_BUFFS_ENABLED = builder
                .comment("Master switch for buffing mobs inside radiation zones.")
                .define("enabled", true);
        AFFECT_PASSIVE_MOBS = builder
                .comment("If true, non-hostile mobs (cows, sheep, villagers, ...) are also buffed inside zones. Default: false.")
                .define("affectPassiveMobs", false);
        MOB_BUFF_CHECK_INTERVAL_TICKS = builder
                .comment("Ticks between mob buff (re)checks. Lower = more responsive but more CPU.")
                .defineInRange("checkIntervalTicks", 20, 5, 200);
        MOB_PERSISTENT = builder
                .comment("If true, mobs that have been buffed by a radiation zone are flagged as persistent and will not despawn naturally — they keep haunting the area.")
                .define("persistent", true);
        MOB_GLOWING_MIN_LEVEL = builder
                .comment("Minimum zone level at which buffed mobs gain the Glowing effect (so players can see mutated mobs through walls). 0 disables.")
                .defineInRange("glowingMinLevel", 2, 0, 20);
        MOB_STRENGTH_MIN_LEVEL = builder
                .comment("Minimum zone level at which buffed mobs gain a vanilla Strength effect on top of attack scaling. 0 disables.")
                .defineInRange("strengthMinLevel", 3, 0, 20);
        MOB_STRENGTH_AMPLIFIER = builder
                .comment("Amplifier of the Strength effect (0 = Strength I, 1 = Strength II, ...).")
                .defineInRange("strengthAmplifier", 0, 0, 9);
        MOB_RESISTANCE_MIN_LEVEL = builder
                .comment("Minimum zone level at which buffed mobs gain Resistance (damage reduction). 0 disables.")
                .defineInRange("resistanceMinLevel", 4, 0, 20);
        MOB_RESISTANCE_AMPLIFIER = builder
                .comment("Amplifier of the Resistance effect (0 = Resistance I = -20% damage, 1 = -40%, ...).")
                .defineInRange("resistanceAmplifier", 0, 0, 4);
        MOB_ZONE_CAP = builder
                .comment("Maximum number of persistent buffed mobs allowed per radiation zone. "
                        + "Once the cap is reached, newly buffed mobs remain despawnable so the "
                        + "population stays bounded and doesn't tank server framerate. 0 = unlimited (not recommended).")
                .defineInRange("zonePopulationCap", 30, 0, 500);
        builder.pop();

        builder.comment("Visual ambient particle effect inside radiation zones.").push("particles");
        PARTICLES_ENABLED = builder
                .comment("Whether to spawn ambient particles inside zones.")
                .define("enabled", true);
        PARTICLES_PER_TICK = builder
                .comment("Number of particles to spawn per tick around each player inside a zone (per level).")
                .defineList("particlesPerTick",
                        List.of(1, 2, 3, 5, 8),
                        () -> 2,
                        o -> o instanceof Integer i && i >= 0);
        PARTICLES_RADIUS = builder
                .comment("Radius around the player (in blocks) within which particles spawn. Particles outside the zone bounds are clipped.")
                .defineInRange("radius", 8.0, 0.5, 64.0);
        PARTICLES_PREVIEW_RADIUS = builder
                .comment("Distance (in blocks) from a zone within which a player who is not yet inside still sees a few hint particles near the zone edge. 0 disables the preview.")
                .defineInRange("previewRadius", 24.0, 0.0, 128.0);
        PARTICLE_COLOR_RGB = builder
                .comment("Hex RGB color used to tint the radiation haze particles (without the leading '#'). Default 52C82E is a sickly radioactive green.")
                .define("colorRgb", "52C82E", o -> o instanceof String s && s.matches("(?i)[0-9a-f]{6}"));
        PARTICLE_SMOKE_ACCENT = builder
                .comment("If true, mix in a small amount of low, drifting smoke for a foggy/heavy feel on top of the colored haze.")
                .define("smokeAccent", true);
        PARTICLE_PREVIEW_ENABLED = builder
                .comment("If true, players also see hint haze near zones they have not yet entered (limited by previewRadius).")
                .define("previewEnabled", true);
        builder.pop();

        builder.comment("Geiger-counter style ticking sound played to players inside zones.").push("geiger");
        GEIGER_ENABLED = builder
                .comment("Whether to play Geiger-counter ticking sounds while inside a zone.")
                .define("enabled", true);
        GEIGER_INTERVAL_TICKS = builder
                .comment("Ticks between Geiger clicks (per level). Lower = faster ticking. 20 ticks = 1 second.")
                .defineList("intervalTicks",
                        List.of(20, 14, 10, 7, 4),
                        () -> 14,
                        o -> o instanceof Integer i && i > 0);
        GEIGER_VOLUME = builder
                .comment("Volume of each Geiger click (0.0 - 1.0).")
                .defineInRange("volume", 0.4, 0.0, 1.0);
        GEIGER_SOUND_ID = builder
                .comment("Sound resource ID for the Geiger click. Examples: minecraft:block.stone_button.click_on, minecraft:block.note_block.hat, minecraft:entity.experience_orb.pickup.")
                .define("soundId", "radiationzones:geiger_click");
        GEIGER_PREVIEW_ENABLED = builder
                .comment("If true, players also hear sparse Geiger ticks when they are near (but not yet inside) a zone, scaling with proximity. Uses the same previewRadius as particles.")
                .define("previewEnabled", true);
        builder.pop();

        builder.comment("Client-side immersion options (only the local player is affected).").push("client");
        GAS_MASK_OVERLAY_ENABLED = builder
                .comment("If true, draw a first-person HUD overlay (round lens cutouts plus a faint dark vignette) while the gas mask is in the helmet slot.")
                .define("gasMaskOverlay", true);
        builder.pop();

        builder.comment("Per-recipe toggles. Set any to false to remove that recipe from the crafting table and recipe book. Takes effect on /reload — no restart required.").push("recipes");
        RECIPE_GAS_MASK = builder
                .comment("Whether the Gas Mask crafting recipe is enabled.")
                .define("gasMask", true);
        RECIPE_BASIC_FILTER = builder
                .comment("Whether the Basic Filter crafting recipe is enabled.")
                .define("basicFilter", true);
        RECIPE_INDUSTRIAL_FILTER = builder
                .comment("Whether the Industrial Filter crafting recipe is enabled.")
                .define("industrialFilter", true);
        RECIPE_HAZMAT_FILTER = builder
                .comment("Whether the Hazmat Filter crafting recipe is enabled.")
                .define("hazmatFilter", true);
        RECIPE_LUGOLS_POTION = builder
                .comment("Whether the Lugol's Iodine crafting recipe is enabled (only matters if a Lugol's recipe is registered).")
                .define("lugolsPotion", true);
        builder.pop();

        SPEC = builder.build();
    }

    private RadiationConfig() {}

    private static <T> T atLevel(List<? extends T> list, int level, T fallback) {
        int idx = Math.max(0, Math.min(list.size() - 1, level - 1));
        if (list.isEmpty()) return fallback;
        return list.get(idx);
    }

    public static int gracePeriodSeconds(int level) { return atLevel(GRACE_PERIOD_SECONDS.get(), level, 30); }
    public static double damageAmount(int level) { return atLevel(DAMAGE_AMOUNT.get(), level, 1.0); }
    public static int damageIntervalTicks(int level) { return atLevel(DAMAGE_INTERVAL_TICKS.get(), level, 40); }
    public static double mobHealthMult(int level) { return atLevel(MOB_HEALTH_MULT.get(), level, 0.5); }
    public static double mobSpeedMult(int level) { return atLevel(MOB_SPEED_MULT.get(), level, 0.15); }
    public static double mobAttackMult(int level) { return atLevel(MOB_ATTACK_MULT.get(), level, 0.5); }
    public static double mobScaleBonus(int level) { return atLevel(MOB_SCALE_BONUS.get(), level, 0.10); }
    public static double mobKnockbackResistance(int level) { return atLevel(MOB_KNOCKBACK_RESIST.get(), level, 0.20); }
    public static boolean mobsPersistent() { return MOB_PERSISTENT.get(); }
    public static int mobZoneCap() { return MOB_ZONE_CAP.get(); }
    public static int mobGlowingMinLevel() { return MOB_GLOWING_MIN_LEVEL.get(); }
    public static int mobStrengthMinLevel() { return MOB_STRENGTH_MIN_LEVEL.get(); }
    public static int mobStrengthAmplifier() { return MOB_STRENGTH_AMPLIFIER.get(); }
    public static int mobResistanceMinLevel() { return MOB_RESISTANCE_MIN_LEVEL.get(); }
    public static int mobResistanceAmplifier() { return MOB_RESISTANCE_AMPLIFIER.get(); }

    public static boolean creativeImmune() { return CREATIVE_IMMUNE.get(); }
    public static boolean spectatorImmune() { return SPECTATOR_IMMUNE.get(); }
    public static boolean bossBarVisible() { return BOSS_BAR_VISIBLE.get(); }
    public static int shieldingYBelow() { return MIN_DEPTH_AFFECTED.get(); }

    public static int lugolsAmplifier() { return LUGOLS_AMPLIFIER.get(); }
    public static boolean lugolsShowIcon() { return LUGOLS_SHOW_ICON.get(); }

    public static boolean mobBuffsEnabled() { return MOB_BUFFS_ENABLED.get(); }
    public static boolean affectPassiveMobs() { return AFFECT_PASSIVE_MOBS.get(); }
    public static int mobBuffCheckIntervalTicks() { return MOB_BUFF_CHECK_INTERVAL_TICKS.get(); }

    public static boolean particlesEnabled() { return PARTICLES_ENABLED.get(); }
    public static int particlesPerTick(int level) { return atLevel(PARTICLES_PER_TICK.get(), level, 2); }
    public static double particlesRadius() { return PARTICLES_RADIUS.get(); }
    public static double particlesPreviewRadius() { return PARTICLES_PREVIEW_RADIUS.get(); }
    public static boolean particlePreviewEnabled() { return PARTICLE_PREVIEW_ENABLED.get(); }
    public static boolean particleSmokeAccent() { return PARTICLE_SMOKE_ACCENT.get(); }
    /** Returns the configured tint as packed 0xRRGGBB, or a green fallback if invalid. */
    public static int particleColorPacked() {
        String s = PARTICLE_COLOR_RGB.get();
        try { return Integer.parseInt(s, 16) & 0xFFFFFF; }
        catch (Exception e) { return 0x52C82E; }
    }

    public static boolean geigerEnabled() { return GEIGER_ENABLED.get(); }
    public static int geigerIntervalTicks(int level) { return atLevel(GEIGER_INTERVAL_TICKS.get(), level, 14); }
    public static double geigerVolume() { return GEIGER_VOLUME.get(); }
    public static String geigerSoundId() { return GEIGER_SOUND_ID.get(); }
    public static boolean geigerPreviewEnabled() { return GEIGER_PREVIEW_ENABLED.get(); }

    public static boolean gasMaskOverlayEnabled() { return GAS_MASK_OVERLAY_ENABLED.get(); }

    /** Returns whether the named built-in recipe is enabled in config.
     *  Unknown names default to true so 3rd-party datapacks aren't accidentally gated. */
    public static boolean recipeEnabled(String name) {
        return switch (name) {
            case "gas_mask" -> RECIPE_GAS_MASK.get();
            case "basic_filter" -> RECIPE_BASIC_FILTER.get();
            case "industrial_filter" -> RECIPE_INDUSTRIAL_FILTER.get();
            case "hazmat_filter" -> RECIPE_HAZMAT_FILTER.get();
            case "lugols_potion" -> RECIPE_LUGOLS_POTION.get();
            default -> true;
        };
    }
}
