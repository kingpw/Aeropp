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


public class Warship<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.parse("testmod:warship"), "main");
	private final ModelPart body;
	private final ModelPart hull;
	private final ModelPart deck;
	private final ModelPart superstructure;
	private final ModelPart pods;
	private final ModelPart fins;
	private final ModelPart gondola;

	public Warship(ModelPart root) {
		this.body = root.getChild("body");
		this.hull = this.body.getChild("hull");
		this.deck = this.body.getChild("deck");
		this.superstructure = this.body.getChild("superstructure");
		this.pods = this.body.getChild("pods");
		this.fins = this.body.getChild("fins");
		this.gondola = this.body.getChild("gondola");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition hull = body.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(-2, 0).addBox(-21.0F, -20.0F, -480.0F, 42.0F, 40.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(-2, 0).addBox(-18.0F, 20.0F, -480.0F, 36.0F, 20.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(-1, 0).addBox(-17.0F, -40.0F, -480.0F, 35.0F, 20.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-11.0F, 40.0F, -480.0F, 22.0F, 16.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(-2, 0).addBox(-12.0F, -56.0F, -480.0F, 24.0F, 16.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-38.0F, -20.0F, -400.0F, 76.0F, 40.0F, 100.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-32.3F, 20.0F, -400.0F, 64.6F, 20.0F, 100.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-32.3F, -40.0F, -400.0F, 64.6F, 20.0F, 100.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, 40.0F, -400.0F, 41.8F, 16.0F, 100.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.9F, -56.0F, -400.0F, 41.8F, 16.0F, 100.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-58.0F, -20.0F, -300.0F, 116.0F, 40.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-49.3F, 20.0F, -300.0F, 98.6F, 20.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-49.3F, -40.0F, -300.0F, 98.6F, 20.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-31.9F, 40.0F, -300.0F, 63.8F, 16.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-31.9F, -56.0F, -300.0F, 63.8F, 16.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-67.0F, -20.0F, -180.0F, 134.0F, 40.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-56.95F, 20.0F, -180.0F, 113.9F, 20.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-56.95F, -40.0F, -180.0F, 113.9F, 20.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-36.85F, 40.0F, -180.0F, 73.7F, 16.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-36.85F, -56.0F, -180.0F, 73.7F, 16.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-67.0F, -20.0F, -60.0F, 134.0F, 40.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-56.95F, 20.0F, -60.0F, 113.9F, 20.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-56.95F, -40.0F, -60.0F, 113.9F, 20.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-36.85F, 40.0F, -60.0F, 73.7F, 16.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-36.85F, -56.0F, -60.0F, 73.7F, 16.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-58.0F, -20.0F, 80.0F, 116.0F, 40.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-49.3F, 20.0F, 80.0F, 98.6F, 20.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-49.3F, -40.0F, 80.0F, 98.6F, 20.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-31.9F, 40.0F, 80.0F, 63.8F, 16.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-31.9F, -56.0F, 80.0F, 63.8F, 16.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-43.0F, -20.0F, 220.0F, 86.0F, 40.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-36.55F, 20.0F, 220.0F, 73.1F, 20.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-36.55F, -40.0F, 220.0F, 73.1F, 20.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-23.65F, 40.0F, 220.0F, 47.3F, 16.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-23.65F, -56.0F, 220.0F, 47.3F, 16.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-24.0F, -20.0F, 340.0F, 48.0F, 40.0F, 100.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.4F, 20.0F, 340.0F, 40.8F, 20.0F, 100.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-20.4F, -40.0F, 340.0F, 40.8F, 20.0F, 100.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-13.2F, 40.0F, 340.0F, 26.4F, 16.0F, 100.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-13.2F, -56.0F, 340.0F, 26.4F, 16.0F, 100.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-12.0F, -20.0F, 440.0F, 24.0F, 40.0F, 40.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition deck = body.addOrReplaceChild("deck", CubeListBuilder.create().texOffs(0, 0).addBox(-30.0F, -60.0F, -360.0F, 60.0F, 4.0F, 720.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -70.0F, -350.0F, 2.0F, 2.0F, 700.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -70.0F, -350.0F, 2.0F, 2.0F, 700.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, -351.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, -351.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, -301.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, -301.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, -251.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, -251.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, -201.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, -201.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, -151.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, -151.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, -101.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, -101.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, -51.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, -51.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, 49.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, 49.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, 99.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, 99.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, 149.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, 149.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, 199.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, 199.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, 249.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, 249.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, 299.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, 299.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(28.0F, -68.0F, 349.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-30.0F, -68.0F, 349.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition superstructure = body.addOrReplaceChild("superstructure", CubeListBuilder.create().texOffs(0, 0).addBox(-20.0F, -78.0F, -60.0F, 40.0F, 18.0F, 90.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-16.0F, -92.0F, -50.0F, 32.0F, 14.0F, 65.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-12.0F, -102.0F, -40.0F, 24.0F, 10.0F, 40.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-13.0F, -105.0F, -42.0F, 26.0F, 3.0F, 44.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-19.0F, -76.0F, -62.0F, 38.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(20.0F, -76.0F, -55.0F, 1.0F, 4.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-21.0F, -76.0F, -55.0F, 1.0F, 4.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-10.0F, -108.0F, -30.0F, 20.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-10.0F, -102.0F, 60.0F, 20.0F, 42.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-11.0F, -92.0F, 60.0F, 22.0F, 6.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-11.0F, -107.0F, 58.0F, 22.0F, 5.0F, 40.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-8.0F, -94.0F, 106.0F, 16.0F, 34.0F, 28.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-9.0F, -86.0F, 106.0F, 18.0F, 6.0F, 28.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-9.0F, -99.0F, 104.0F, 18.0F, 5.0F, 32.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-14.0F, -74.0F, 150.0F, 28.0F, 14.0F, 40.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-10.0F, -80.0F, 160.0F, 20.0F, 6.0F, 20.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.0F, -140.0F, -20.0F, 2.0F, 35.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-14.0F, -130.0F, -20.0F, 28.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-10.0F, -120.0F, -20.0F, 20.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.0F, -96.0F, -260.0F, 2.0F, 36.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-8.0F, -90.0F, -260.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(18.0F, -70.0F, -100.0F, 6.0F, 10.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-24.0F, -70.0F, -100.0F, 6.0F, 10.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-16.0F, -62.0F, -315.0F, 32.0F, 6.0F, 35.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-16.0F, -62.0F, 280.0F, 32.0F, 6.0F, 35.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(36.0F, -42.0F, -138.0F, 36.0F, 6.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(36.0F, -42.0F, 142.0F, 36.0F, 6.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-72.0F, -42.0F, -138.0F, 36.0F, 6.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-72.0F, -42.0F, 142.0F, 36.0F, 6.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-14.0F, 56.0F, -215.0F, 28.0F, 6.0F, 30.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-14.0F, 56.0F, 185.0F, 28.0F, 6.0F, 30.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition pods = body.addOrReplaceChild("pods", CubeListBuilder.create().texOffs(0, 0).addBox(67.0F, -4.0F, -146.0F, 26.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(79.0F, -8.0F, -152.0F, 24.0F, 16.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(90.0F, -3.0F, -158.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-93.0F, -4.0F, -146.0F, 26.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-103.0F, -8.0F, -152.0F, 24.0F, 16.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-96.0F, -3.0F, -158.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(58.0F, -4.0F, 174.0F, 26.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(70.0F, -8.0F, 168.0F, 24.0F, 16.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(81.0F, -3.0F, 162.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-84.0F, -4.0F, 174.0F, 26.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-94.0F, -8.0F, 168.0F, 24.0F, 16.0F, 36.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-87.0F, -3.0F, 162.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition bladeB_1_180_r1 = pods.addOrReplaceChild("bladeB_1_180_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-22.0F, -3.0F, -1.0F, 44.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-84.0F, 0.0F, 161.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition bladeA_1_180_r1 = pods.addOrReplaceChild("bladeA_1_180_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-22.0F, -3.0F, -1.0F, 44.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-84.0F, 0.0F, 161.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition bladeB_m1_180_r1 = pods.addOrReplaceChild("bladeB_m1_180_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-22.0F, -3.0F, -1.0F, 44.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(84.0F, 0.0F, 161.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition bladeA_m1_180_r1 = pods.addOrReplaceChild("bladeA_m1_180_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-22.0F, -3.0F, -1.0F, 44.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(84.0F, 0.0F, 161.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition bladeB_1_m140_r1 = pods.addOrReplaceChild("bladeB_1_m140_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-22.0F, -3.0F, -1.0F, 44.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-93.0F, 0.0F, -159.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition bladeA_1_m140_r1 = pods.addOrReplaceChild("bladeA_1_m140_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-22.0F, -3.0F, -1.0F, 44.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-93.0F, 0.0F, -159.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition bladeB_m1_m140_r1 = pods.addOrReplaceChild("bladeB_m1_m140_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-22.0F, -3.0F, -1.0F, 44.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(93.0F, 0.0F, -159.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition bladeA_m1_m140_r1 = pods.addOrReplaceChild("bladeA_m1_m140_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-22.0F, -3.0F, -1.0F, 44.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(93.0F, 0.0F, -159.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition fins = body.addOrReplaceChild("fins", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -96.0F, 390.0F, 6.0F, 40.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.0F, -132.0F, 420.0F, 6.0F, 36.0F, 50.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.0F, 56.0F, 390.0F, 6.0F, 40.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-3.0F, 96.0F, 420.0F, 6.0F, 36.0F, 50.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(6.0F, -3.0F, 390.0F, 80.0F, 6.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(86.0F, -2.0F, 420.0F, 16.0F, 4.0F, 50.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-86.0F, -3.0F, 390.0F, 80.0F, 6.0F, 80.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-102.0F, -2.0F, 420.0F, 16.0F, 4.0F, 50.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.0F, 56.0F, -420.0F, 4.0F, 32.0F, 80.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition gondola = body.addOrReplaceChild("gondola", CubeListBuilder.create().texOffs(0, 0).addBox(-16.0F, 56.0F, -159.0F, 32.0F, 36.0F, 140.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-17.0F, 76.0F, -148.0F, 34.0F, 8.0F, 120.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.0F, 92.0F, -109.0F, 4.0F, 24.0F, 60.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 2048, 2048);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}