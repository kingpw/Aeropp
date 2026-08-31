package com.testmod.client.renderer;

import com.testmod.entity.Flak_Shell_Entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 高射炮弹渲染器：把 testmod:flak_shell 画成一张 2D 平面（billboard）。
 * 因为贴图是方向无关的圆形弹丸，直接面向相机（billboard）即可，任何飞行方向都不歪。
 */
public class Flak_Shell_Renderer extends EntityRenderer<Flak_Shell_Entity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.parse("testmod:textures/entity/flak_shell.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);

    /** billboard 平面半宽（格）：整体约 0.5 格见方 */
    private static final float HALF = 0.25F;

    public Flak_Shell_Renderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Flak_Shell_Entity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // 面向摄像机：让平面法线(+z)对准相机，形成 billboard
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        VertexConsumer vc = bufferSource.getBuffer(RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        int light = LightTexture.FULL_BRIGHT; // 高亮：贴图不受方块光照，像曳光弹一样发光

        // 一个 2D 平面（局部 x-y 平面，z=0），UV 铺满整张贴图，法线朝 +z（对准相机）
        vertex(vc, pose, light, -HALF, -HALF, 0, 0, 1, 0, 0, 1);
        vertex(vc, pose, light, HALF, -HALF, 0, 1, 1, 0, 0, 1);
        vertex(vc, pose, light, HALF, HALF, 0, 1, 0, 0, 0, 1);
        vertex(vc, pose, light, -HALF, HALF, 0, 0, 0, 0, 0, 1);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    private void vertex(VertexConsumer vc, PoseStack.Pose pose, int light,
                        float x, float y, float z, float u, float v,
                        float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(Flak_Shell_Entity entity) {
        return TEXTURE;
    }
}
