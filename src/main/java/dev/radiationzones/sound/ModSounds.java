package dev.radiationzones.sound;

import dev.radiationzones.RadiationZones;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, RadiationZones.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_CLICK = SOUNDS.register(
            "geiger_click",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(RadiationZones.MOD_ID, "geiger_click"))
    );

    private ModSounds() {}
}
