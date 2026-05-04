package dev.radiationzones.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

import dev.radiationzones.RadiationZones;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom HumanoidModel for the worn gas mask. The cube layout is a port of
 * the QSMP Gas Mask 3D model by skibidi (Sketchfab,
 * <https://sketchfab.com/3d-models/qsmp-gas-mask-3d-model-d33075f4f86a40dea4cc908b2d0ddbfa>),
 * downloaded as glTF and projected onto a Minecraft HumanoidModel head bone.
 * Cube sizes and world-space positions were extracted from the GLB by
 * .local/scripts/gen_gas_mask_textures.py.
 *
 * The texture (assets/radiationzones/textures/models/armor/gas_mask_layer_1.png)
 * is a 64x64 atlas re-painted by that same script in Minecraft's
 * CubeListBuilder box-unwrap convention using the QSMP palette sampled
 * from the original GLB texture. Shipping the GLB's PNG verbatim is NOT
 * possible because the GLB uses arbitrary per-face UVs that don't match
 * Minecraft's fixed cube-unwrap layout — doing so causes every cube face
 * to sample a random region of the atlas. The texOffs values below MUST
 * stay in lockstep with the atlas layout documented in that script.
 *
 * The forward filter canister cube (mesh10 in the GLB) is toggled on only
 * when an InstalledFilter component is present, keeping the existing
 * "filter installed/empty" visual distinction.
 */
public class GasMaskModel extends HumanoidModel<LivingEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(RadiationZones.MOD_ID, "gas_mask"), "main");

    /** Pure-white tint = no color modulation applied to the canister. */
    private static final int NO_TINT = 0xFFFFFFFF;

    /** The forward "screw-on" canister cube — visible only when a filter is installed. */
    private final ModelPart filterCanister;

    /** ARGB tint applied ONLY to the filter canister cube. NO_TINT = neutral. */
    private int filterTint = NO_TINT;

    public GasMaskModel(ModelPart root) {
        super(root);
        this.filterCanister = this.head.getChild("snout_canister");
        // Hide vanilla body parts on the armor layer — only the head matters.
        this.body.visible = false;
        this.leftArm.visible = false;
        this.rightArm.visible = false;
        this.leftLeg.visible = false;
        this.rightLeg.visible = false;
        this.hat.visible = false;
    }

    public void setFiltered(boolean filtered) {
        this.filterCanister.visible = filtered;
    }

    /** Set the ARGB tint applied to the canister cube only. Pass NO_TINT to disable. */
    public void setFilterTint(int argb) {
        this.filterTint = argb;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buf,
                               int packedLight, int packedOverlay, int color) {
        if (!this.filterCanister.visible || this.filterTint == NO_TINT) {
            super.renderToBuffer(pose, buf, packedLight, packedOverlay, color);
            return;
        }
        // Two-pass render so the tint affects ONLY the canister, not the rubber:
        //   pass 1: full mask with the canister hidden, default color
        //   pass 2: canister alone, in head-local space, multiplied by the tint
        this.filterCanister.visible = false;
        try {
            super.renderToBuffer(pose, buf, packedLight, packedOverlay, color);
        } finally {
            this.filterCanister.visible = true;
        }
        pose.pushPose();
        this.head.translateAndRotate(pose);
        this.filterCanister.render(pose, buf, packedLight, packedOverlay, this.filterTint);
        pose.popPose();
    }

    public static LayerDefinition createLayer() {
        // No global head-shell inflation: the QSMP "head_shell" cube IS the
        // 10x10x10 outer rubber shell, already 1px proud of the player skin
        // on each side, so additional inflation would z-fight the player.
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        // The default head bone is empty here — we rebuild it from the QSMP cubes.
        PartDefinition head = root.addOrReplaceChild(
                "head",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // Outer rubber head shell (10x10x10) — covers the player's head 1px
        // on each side.
        head.addOrReplaceChild(
                "head_shell",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-5.0F, -10.0F, -5.0F, 10.0F, 10.0F, 10.0F),
                PartPose.ZERO);

        // Right eye lens (4x4x2). Positioned slightly proud of the face plate.
        head.addOrReplaceChild(
                "lens_right",
                CubeListBuilder.create()
                        .texOffs(0, 20)
                        .addBox(1.20F, -7.48F, -6.71F, 4.0F, 4.0F, 2.0F),
                PartPose.ZERO);

        // Left eye lens (4x4x2). UV is shared with the right lens (mirrored
        // in the original GLB; we accept the slight asymmetry rather than
        // adding mirror() which can flip the highlight direction).
        head.addOrReplaceChild(
                "lens_left",
                CubeListBuilder.create()
                        .texOffs(12, 20)
                        .addBox(-4.97F, -7.53F, -6.78F, 4.0F, 4.0F, 2.0F),
                PartPose.ZERO);

        // Rubber face plate (8x5x3) — the dark cheek/jaw piece that sits
        // between the lenses and the snout.
        head.addOrReplaceChild(
                "face_plate",
                CubeListBuilder.create()
                        .texOffs(24, 28)
                        .addBox(-4.0F, -3.82F, -6.20F, 8.0F, 5.0F, 3.0F),
                PartPose.ZERO);

        // Right cheek strap mount (3x2x2).
        head.addOrReplaceChild(
                "strap_right_inner",
                CubeListBuilder.create()
                        .texOffs(44, 20)
                        .addBox(3.95F, -1.73F, -6.03F, 3.0F, 2.0F, 2.0F),
                PartPose.ZERO);

        // Right side filter cap (2x3x3).
        head.addOrReplaceChild(
                "filter_right",
                CubeListBuilder.create()
                        .texOffs(24, 20)
                        .addBox(6.88F, -1.80F, -6.96F, 2.0F, 3.0F, 3.0F),
                PartPose.ZERO);

        // Left cheek strap mount (3x2x2).
        head.addOrReplaceChild(
                "strap_left_inner",
                CubeListBuilder.create()
                        .texOffs(54, 20)
                        .addBox(-6.95F, -1.73F, -6.03F, 3.0F, 2.0F, 2.0F),
                PartPose.ZERO);

        // Left side filter cap (2x3x3).
        head.addOrReplaceChild(
                "filter_left",
                CubeListBuilder.create()
                        .texOffs(34, 20)
                        .addBox(-8.88F, -1.80F, -6.96F, 2.0F, 3.0F, 3.0F),
                PartPose.ZERO);

        // Forward snout base (3x3x3) — always visible, the threaded mount
        // for the filter canister.
        head.addOrReplaceChild(
                "snout_base",
                CubeListBuilder.create()
                        .texOffs(0, 28)
                        .addBox(-1.50F, -2.54F, -8.33F, 3.0F, 3.0F, 3.0F),
                PartPose.ZERO);

        // Forward filter canister (2x4x4) — TOGGLEABLE: hidden when no
        // filter is installed.
        head.addOrReplaceChild(
                "snout_canister",
                CubeListBuilder.create()
                        .texOffs(12, 28)
                        .addBox(-1.00F, -1.52F, -10.82F, 2.0F, 4.0F, 4.0F),
                PartPose.ZERO);

        // Atlas is 64x64, painted by .local/scripts/gen_gas_mask_textures.py
        // in Minecraft cube-unwrap order using the QSMP palette.
        return LayerDefinition.create(mesh, 64, 64);
    }
}
