package com.testmod.client.model.generated;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

// Made with Blockbench 5.1.6
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class Undead_Sky_City<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.parse("testmod:undead_sky_city"), "main");
	private final ModelPart body;
	private final ModelPart gasbag;
	private final ModelPart gondola;

	public Undead_Sky_City(ModelPart root) {
		this.body = root.getChild("body");
		this.gasbag = this.body.getChild("gasbag");
		this.gondola = this.body.getChild("gondola");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition gasbag = body.addOrReplaceChild("gasbag", CubeListBuilder.create().texOffs(1568, 0).addBox(-24.0F, 32.0F, -96.0F, 48.0F, 8.0F, 192.0F, new CubeDeformation(0.0F))
		.texOffs(728, 324).addBox(-32.0F, 24.0F, -128.0F, 64.0F, 8.0F, 256.0F, new CubeDeformation(0.0F))
		.texOffs(0, 324).addBox(-36.0F, 16.0F, -146.0F, 72.0F, 8.0F, 292.0F, new CubeDeformation(0.0F))
		.texOffs(792, 0).addBox(-38.0F, 8.0F, -156.0F, 76.0F, 8.0F, 312.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-40.0F, 0.0F, -156.0F, 80.0F, 8.0F, 316.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-40.0F, -8.0F, -156.0F, 80.0F, 8.0F, 316.0F, new CubeDeformation(0.0F))
		.texOffs(792, 0).addBox(-38.0F, -16.0F, -156.0F, 76.0F, 8.0F, 312.0F, new CubeDeformation(0.0F))
		.texOffs(0, 324).addBox(-36.0F, -24.0F, -146.0F, 72.0F, 8.0F, 292.0F, new CubeDeformation(0.0F))
		.texOffs(728, 324).addBox(-32.0F, -32.0F, -128.0F, 64.0F, 8.0F, 256.0F, new CubeDeformation(0.0F))
		.texOffs(1368, 324).addBox(-24.0F, -40.0F, -100.0F, 48.0F, 8.0F, 200.0F, new CubeDeformation(0.0F))
		.texOffs(0, 688).addBox(-2.0F, -40.0F, 138.0F, 4.0F, 48.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(372, 624).addBox(-56.0F, 0.0F, 138.0F, 36.0F, 8.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(372, 624).addBox(20.0F, 0.0F, 138.0F, 36.0F, 8.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(492, 624).addBox(-46.0F, 20.0F, 106.0F, 18.0F, 12.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(492, 624).addBox(28.0F, 20.0F, 106.0F, 18.0F, 12.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(912, 624).addBox(-47.0F, 21.0F, 130.0F, 20.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(912, 624).addBox(27.0F, 21.0F, 130.0F, 20.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(576, 624).addBox(-30.0F, -11.0F, -160.0F, 60.0F, 19.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -128.0F, 0.0F));

		PartDefinition gondola = body.addOrReplaceChild("gondola", CubeListBuilder.create().texOffs(1864, 324).addBox(-32.0F, -24.0F, -8.0F, 64.0F, 48.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(1864, 324).addBox(-32.0F, -24.0F, -24.0F, 64.0F, 48.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(1864, 324).addBox(-32.0F, -24.0F, 8.0F, 64.0F, 48.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(0, 624).addBox(-29.0F, -24.0F, -40.0F, 58.0F, 48.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(0, 624).addBox(-29.0F, -24.0F, 24.0F, 58.0F, 48.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(148, 624).addBox(-24.0F, -24.0F, -56.0F, 48.0F, 48.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(148, 624).addBox(-24.0F, -24.0F, 40.0F, 48.0F, 48.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(276, 624).addBox(-16.0F, -24.0F, -72.0F, 32.0F, 48.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(276, 624).addBox(-16.0F, -24.0F, 56.0F, 32.0F, 48.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(704, 624).addBox(-10.0F, -24.0F, -80.0F, 20.0F, 42.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(704, 624).addBox(-10.0F, -24.0F, 72.0F, 20.0F, 42.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -64.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 2048, 1024);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}