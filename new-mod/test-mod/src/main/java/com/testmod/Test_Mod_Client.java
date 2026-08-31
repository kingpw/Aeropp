package com.testmod;

import com.testmod.client.model.generated.Generated_Models;
import com.testmod.client.Laser_Gun_Recoil;
import com.testmod.client.particle.Laser_Particle;
import com.testmod.client.particle.Flak_Burst_Particle;
import com.testmod.client.particle.Flak_Smoke_Particle;
import com.testmod.item.Laser_Beam;
import com.testmod.network.Laser_Fired_Payload;
import com.testmod.network.Laser_Reload_Payload;
import com.testmod.network.Warship_Laser_Payload;
import com.testmod.client.model.Large_Monster_Airship_Model;
import com.testmod.client.model.Ship_Turret_Model;
import com.testmod.client.model.Undead_Sky_City_Model;
import com.testmod.client.model.Warship_Model;
import com.testmod.client.model.Flak_Cannon_Model;
import com.testmod.client.renderer.ShellRenderer;
import com.testmod.client.renderer.Flak_Shell_Renderer;
import com.testmod.client.renderer.Laser_Zombie_Renderer;
import com.testmod.network.Laser_Zombie_Fired_Payload;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.content.equipment.armor.BacktankRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import net.neoforged.neoforge.client.event.RenderHandEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Test_Mod.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Test_Mod.MODID, value = Dist.CLIENT)
public class Test_Mod_Client {

    /** 换弹键（默认 R） */
    public static final KeyMapping RELOAD_KEY = new KeyMapping("key.testmod.reload", GLFW.GLFW_KEY_R, "key.categories.gameplay");

    /** 激光枪手持姿势：手臂像拉弓一样指向视线方向（第三人称/他人视角；第一人称不受影响） */
    @SubscribeEvent
    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
        }, Test_Mod.LASER_GUN.get());
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(RELOAD_KEY);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // ① 普通渲染器（flywheel 关闭时）：Create 的 BacktankRenderer，画顶部旋转杆。
        BlockEntityRenderers.register(Test_Mod.ENHANCED_JETPACK_BE.get(), BacktankRenderer::new);

        // ② flywheel 视觉（flywheel 开启时，默认开启）：SingleAxisRotatingVisual + 下界合金轴的模型。
        //    注意：KineticBlockEntityRenderer 在 flywheel 开启时会直接跳过渲染，
        //    所以必须注册 visual，否则顶部杆不会显示。
        VisualizerRegistry.setVisualizer(
                Test_Mod.ENHANCED_JETPACK_BE.get(),
                SimpleBlockEntityVisualizer.<BacktankBlockEntity>builder(Test_Mod.ENHANCED_JETPACK_BE.get())
                        .factory(SingleAxisRotatingVisual.<BacktankBlockEntity>of(AllPartialModels.NETHERITE_BACKTANK_SHAFT))
                        .neverSkipVanillaRender()
                        .apply()
        );

        // 大型怪物飞艇的实体渲染器
        EntityRenderers.register(Test_Mod.LARGE_MONSTER_AIRSHIP.get(), Large_Monster_Airship_Model.Renderer::new);
        // 亡灵天城的实体渲染器
        EntityRenderers.register(Test_Mod.UNDEAD_SKY_CITY.get(), Undead_Sky_City_Model.Renderer::new);
        // 战舰的实体渲染器
        EntityRenderers.register(Test_Mod.WARSHIP.get(), Warship_Model.Renderer::new);
        // 炮弹投射物的实体渲染器
        EntityRenderers.register(Test_Mod.SHELL.get(), ShellRenderer::new);
        // 高射炮弹（可投掷投射物，billboard 2D 渲染）
        EntityRenderers.register(Test_Mod.FLAK_SHELL.get(), Flak_Shell_Renderer::new);
        // 高射炮固定炮台的实体渲染器
        EntityRenderers.register(Test_Mod.FLAK_CANNON.get(), Flak_Cannon_Model.Renderer::new);
        // 激光僵尸的实体渲染器（复用原版僵尸模型+皮肤，手上画激光枪）
        EntityRenderers.register(Test_Mod.LASER_ZOMBIE.get(), Laser_Zombie_Renderer::new);
    }

    /**
     * 激光枪射击时去掉第一人称"物品下沉"动画：原版 use 成功会调 ItemInHandRenderer.itemUsed
     * 把手部高度清零再回升（就是开枪时枪往下沉再抬起的动作）。持有激光枪时每 tick 把高度钉回 1，
     * 模型全程保持不动（字段经 accesstransformer.cfg 放开）。
     * 注意必须同步渲染器追踪的物品（mainHandItem/offHandItem）：原版换手靠"高度降到 0.1 以下才换渲染物品"，
     * 高度被钉住后这个流程永远走不完，切枪会不显示模型；同步后切枪直接显示，也没有举起动画。
     */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Laser_Gun_Recoil.tick();
        ItemInHandRenderer r = mc.gameRenderer.itemInHandRenderer;
        ItemStack main = mc.player.getMainHandItem();
        if (main.is(Test_Mod.LASER_GUN.get())) {
            r.mainHandItem = main;
            r.mainHandHeight = 1.0F;
            r.oMainHandHeight = 1.0F;
        }
        ItemStack off = mc.player.getOffhandItem();
        if (off.is(Test_Mod.LASER_GUN.get())) {
            r.offHandItem = off;
            r.offHandHeight = 1.0F;
            r.oOffHandHeight = 1.0F;
        }

        // R 换弹：手里有激光枪且弹药不满 → 发包让服务端回满弹药（服务端上 30 tick 换弹冷却挡住射击）
        while (RELOAD_KEY.consumeClick()) {
            ItemStack held = main.is(Test_Mod.LASER_GUN.get()) ? main : off;
            if (held.is(Test_Mod.LASER_GUN.get()) && held.getDamageValue() > 0) {
                PacketDistributor.sendToServer(Laser_Reload_Payload.INSTANCE);
            }
        }
    }

    /**
     * 激光枪后座渲染（第一人称）：RenderHandEvent 在 vanilla 渲染这只手之前触发，
     * 此时改 poseStack 会影响本次渲染。主手是一帧里第一只渲染的手：先清空上一帧的记录
     *（否则副手枪的后座记录会跨帧污染主手，高帧率下"鬼畜"）；渲染副手前抵消主手留下的后座。
     */
    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            Laser_Gun_Recoil.beginFrame();
        } else {
            Laser_Gun_Recoil.unapply(event.getPoseStack());
        }
        if (event.getItemStack().is(Test_Mod.LASER_GUN.get())) {
            Laser_Gun_Recoil.apply(event.getPoseStack(), event.getPartialTick());
        }
    }

    /**
     * 服务端确认击发后的客户端效果（由 Laser_Fired_Payload 回调）：
     * 直接按服务端算好的枪口几何（start+dir+len）画光束——不依赖本地视角，
     * 联机时所有靠近的玩家都看到同一条判定线。只有射手自己额外触发第一人称后座。
     */
    public static void onPlayerLaser(Laser_Fired_Payload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Laser_Beam.spawnBeam(mc.level, new Laser_Beam.Beam(
                new Vec3(payload.startX(), payload.startY(), payload.startZ()),
                new Vec3(payload.dirX(), payload.dirY(), payload.dirZ()),
                payload.len(), null));
        if (mc.player != null && mc.player.getId() == payload.shooterId()) {
            Laser_Gun_Recoil.kick();
        }
    }

    /** 战舰激光齐射（服务端确认后画三条光束，start+dir+len 已附带） */
    public static void onWarshipLaser(Warship_Laser_Payload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        drawWarshipBeam(mc.level, payload.b0());
        drawWarshipBeam(mc.level, payload.b1());
        drawWarshipBeam(mc.level, payload.b2());
    }

    private static void drawWarshipBeam(Level level, float[] b) {
        if (b == null || b.length < 7) return;
        Laser_Beam.spawnBeam(level, new Laser_Beam.Beam(new Vec3(b[0], b[1], b[2]), new Vec3(b[3], b[4], b[5]), b[6], null));
    }

    /** 激光僵尸射击确认（服务端 → 客户端）：本地画光束粒子 */
    public static void onLaserZombieFired(Laser_Zombie_Fired_Payload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        drawWarshipBeam(mc.level, payload.beam());
    }

    /** 注册自定义粒子（激光粒子：寿命 6 tick 快速消散） */
    @SubscribeEvent
    static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(Test_Mod.LASER_PARTICLE.get(), Laser_Particle.Provider::new);
        event.registerSpriteSet(Test_Mod.FLAK_BURST_PARTICLE.get(), Flak_Burst_Particle.Provider::new);
        event.registerSpriteSet(Test_Mod.FLAK_SMOKE_PARTICLE.get(), Flak_Smoke_Particle.Provider::new);
    }

    /** 注册模型层定义 */
    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(Large_Monster_Airship_Model.LAYER_LOCATION, Large_Monster_Airship_Model::createBodyLayer);
        event.registerLayerDefinition(Undead_Sky_City_Model.LAYER_LOCATION, Undead_Sky_City_Model::createBodyLayer);
        event.registerLayerDefinition(Ship_Turret_Model.LAYER_LOCATION, Ship_Turret_Model::createBodyLayer);
        // model/ 文件夹里的 Blockbench 导出模型：自动注册（Generated_Models 由 model_loader.gradle 生成）
        Generated_Models.registerAll(event);
    }
}
