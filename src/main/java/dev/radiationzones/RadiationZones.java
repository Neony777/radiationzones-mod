package dev.radiationzones;

import dev.radiationzones.command.ModCommands;
import dev.radiationzones.component.ModDataComponents;
import dev.radiationzones.config.RadiationConfig;
import dev.radiationzones.effect.ModEffects;
import dev.radiationzones.event.MobBuffHandler;
import dev.radiationzones.event.PlayerRadiationHandler;
import dev.radiationzones.item.ModArmorMaterials;
import dev.radiationzones.item.ModCreativeTab;
import dev.radiationzones.item.ModItems;
import dev.radiationzones.recipe.ModRecipeConditions;
import dev.radiationzones.sound.ModSounds;
import dev.radiationzones.zone.ZoneManager;
import dev.radiationzones.zone.ZoneTicker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(RadiationZones.MOD_ID)
public final class RadiationZones {
    public static final String MOD_ID = "radiationzones";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RadiationZones(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModRecipeConditions.CONDITIONS.register(modEventBus);
        ModCreativeTab.TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, RadiationConfig.SPEC, "radiationzones-common.toml");

        NeoForge.EVENT_BUS.register(ModCommands.class);
        NeoForge.EVENT_BUS.register(new PlayerRadiationHandler());
        NeoForge.EVENT_BUS.register(new MobBuffHandler());
        NeoForge.EVENT_BUS.register(new ZoneManager.LifecycleEvents());
        NeoForge.EVENT_BUS.register(ZoneTicker.create());

        LOGGER.info("RadiationZones initialized");
    }
}
