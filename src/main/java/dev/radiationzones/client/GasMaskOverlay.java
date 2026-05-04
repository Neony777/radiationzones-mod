package dev.radiationzones.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.radiationzones.RadiationZones;
import dev.radiationzones.config.RadiationConfig;
import dev.radiationzones.item.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Semi-transparent first-person overlay drawn while the player is wearing
 * the gas mask. Two soft circular cutouts simulate the lens visibility,
 * the rest is a faint dark vignette suggesting the inside of the rubber
 * face piece. While sprinting, a subtle condensation/fog tint pulses
 * around the lens edges.
 */
public final class GasMaskOverlay implements LayeredDraw.Layer {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RadiationZones.MOD_ID, "textures/gui/gas_mask_overlay.png");
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(RadiationZones.MOD_ID, "gas_mask_overlay");

    public static final GasMaskOverlay INSTANCE = new GasMaskOverlay();

    private float fogPhase = 0f;

    private GasMaskOverlay() {}

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!RadiationConfig.gasMaskOverlayEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        // Don't render in 3rd person — the player can already see the mask on the model.
        if (!mc.options.getCameraType().isFirstPerson()) return;

        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty() || helmet.getItem() != ModItems.GAS_MASK.get()) return;

        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(TEXTURE, 0, 0, 0f, 0f, w, h, w, h);

        // Optional condensation pulse while sprinting: an extra soft dark
        // pass with a low alpha that breathes in/out at ~1Hz.
        if (player.isSprinting()) {
            fogPhase += deltaTracker.getGameTimeDeltaPartialTick(true) / 20f;
            float pulse = 0.5f + 0.5f * (float) Math.sin(fogPhase * Math.PI);
            float alpha = 0.18f + 0.22f * pulse;
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            graphics.blit(TEXTURE, 0, 0, 0f, 0f, w, h, w, h);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            fogPhase *= 0.95f;
        }

        RenderSystem.disableBlend();
    }
}
