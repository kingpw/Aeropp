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


public class Turret_Model<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.parse("testmod:turret_model"), "main");
	private final ModelPart turret_rf;
	private final ModelPart barrel_rf;

	public Turret_Model(ModelPart root) {
		this.turret_rf = root.getChild("turret_rf");
		this.barrel_rf = this.turret_rf.getChild("barrel_rf");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition turret_rf = partdefinition.addOrReplaceChild("turret_rf", CubeListBuilder.create().texOffs(764, 626).addBox(-7.5F, -2.25F, -5.0F, 14.0F, 16.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(1200, 620).addBox(6.5F, -2.25F, -4.5F, 1.0F, 16.0F, 13.0F, new CubeDeformation(0.0F))
		.texOffs(1200, 620).addBox(-8.5F, -2.25F, -4.5F, 1.0F, 16.0F, 13.0F, new CubeDeformation(0.0F))
		.texOffs(1240, 620).addBox(-9.5F, -2.25F, -3.5F, 1.0F, 16.0F, 11.0F, new CubeDeformation(0.0F))
		.texOffs(1240, 620).addBox(7.5F, -2.25F, -3.5F, 1.0F, 16.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 10.25F, -2.0F));

		PartDefinition cube_r1 = turret_rf.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(1240, 620).addBox(0.0F, -16.0F, -1.0F, 1.0F, 16.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, 13.75F, 10.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition cube_r2 = turret_rf.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(1240, 620).addBox(0.0F, -16.0F, -1.0F, 1.0F, 16.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, 13.75F, -7.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition cube_r3 = turret_rf.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(1200, 620).addBox(0.0F, -2.0F, -4.0F, 1.0F, 16.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -0.25F, 9.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition cube_r4 = turret_rf.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(1200, 620).addBox(0.0F, -2.0F, -4.0F, 1.0F, 16.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -0.25F, -6.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition barrel_rf = turret_rf.addOrReplaceChild("barrel_rf", CubeListBuilder.create().texOffs(958, 626).addBox(-5.0F, -5.0F, -5.0F, 8.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(825, 625).addBox(-4.0F, -4.0F, -17.0F, 6.0F, 6.0F, 13.0F, new CubeDeformation(0.0F))
		.texOffs(1018, 625).addBox(-4.5F, -4.5F, -21.0F, 7.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(1017, 624).addBox(-4.5F, -7.0F, -4.0F, 7.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(1017, 624).addBox(-4.5F, 3.0F, -4.0F, 7.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.75F, -6.0F));

		return LayerDefinition.create(meshdefinition, 2048, 1024);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		turret_rf.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}