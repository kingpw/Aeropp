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


public class Twin_Turret<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.parse("testmod:twin_turret"), "main");
	private final ModelPart turret_rf;
	private final ModelPart barrel_rf;

	public Twin_Turret(ModelPart root) {
		this.turret_rf = root.getChild("turret_rf");
		this.barrel_rf = this.turret_rf.getChild("barrel_rf");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition turret_rf = partdefinition.addOrReplaceChild("turret_rf", CubeListBuilder.create().texOffs(90, 0).addBox(-12.0F, 2.0F, -10.0F, 24.0F, 4.0F, 20.0F, new CubeDeformation(0.0F))
		.texOffs(0, 36).addBox(-10.0F, 0.0F, -8.0F, 20.0F, 2.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-11.0F, -12.0F, -9.0F, 22.0F, 12.0F, 21.0F, new CubeDeformation(0.0F))
		.texOffs(180, 0).addBox(-9.0F, -10.0F, -12.0F, 18.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(74, 36).addBox(-9.0F, -14.0F, -6.0F, 18.0F, 2.0F, 16.0F, new CubeDeformation(0.0F))
		.texOffs(144, 36).addBox(-6.0F, -10.0F, 12.0F, 12.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 56).addBox(11.0F, -10.0F, -4.0F, 2.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(30, 56).addBox(-13.0F, -10.0F, -4.0F, 2.0F, 8.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.0F, 0.0F));

		PartDefinition barrel_rf = turret_rf.addOrReplaceChild("barrel_rf", CubeListBuilder.create().texOffs(174, 36).addBox(2.5F, -3.0F, -8.0F, 6.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(196, 36).addBox(-8.5F, -3.0F, -8.0F, 6.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(60, 56).addBox(4.0F, -2.0F, -28.0F, 3.0F, 3.0F, 20.0F, new CubeDeformation(0.0F))
		.texOffs(108, 56).addBox(-7.0F, -2.0F, -28.0F, 3.0F, 3.0F, 20.0F, new CubeDeformation(0.0F))
		.texOffs(156, 56).addBox(3.5F, -2.5F, -32.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(174, 56).addBox(-7.5F, -2.5F, -32.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -6.0F, -8.0F));

		return LayerDefinition.create(meshdefinition, 256, 256);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		turret_rf.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}