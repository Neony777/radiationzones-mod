package dev.radiationzones.item;

import dev.radiationzones.RadiationZones;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RadiationZones.MOD_ID);

    public static final DeferredItem<Item> RADIATION_WAND = ITEMS.register(
            "radiation_wand",
            () -> new RadiationWandItem(new Item.Properties().stacksTo(1).fireResistant())
    );

    public static final DeferredItem<Item> LUGOLS_POTION = ITEMS.register(
            "lugols_potion",
            () -> new LugolsPotionItem(new Item.Properties().stacksTo(16))
    );

    // Mask durability is independent of filters; it ticks down very slowly while in a zone.
    public static final DeferredItem<Item> GAS_MASK = ITEMS.register(
            "gas_mask",
            () -> new GasMaskItem(
                    ModArmorMaterials.GAS_MASK,
                    new Item.Properties().stacksTo(1).durability(ArmorItem.Type.HELMET.getDurability(20))
            )
    );

    public static final DeferredItem<Item> BASIC_FILTER = ITEMS.register(
            "basic_filter",
            () -> new FilterItem(
                    new Item.Properties().stacksTo(1).durability(FilterItem.Tier.BASIC.maxDurability),
                    FilterItem.Tier.BASIC)
    );

    public static final DeferredItem<Item> INDUSTRIAL_FILTER = ITEMS.register(
            "industrial_filter",
            () -> new FilterItem(
                    new Item.Properties().stacksTo(1).durability(FilterItem.Tier.INDUSTRIAL.maxDurability),
                    FilterItem.Tier.INDUSTRIAL)
    );

    public static final DeferredItem<Item> HAZMAT_FILTER = ITEMS.register(
            "hazmat_filter",
            () -> new FilterItem(
                    new Item.Properties().stacksTo(1).durability(FilterItem.Tier.HAZMAT.maxDurability),
                    FilterItem.Tier.HAZMAT)
    );

    private ModItems() {}
}
