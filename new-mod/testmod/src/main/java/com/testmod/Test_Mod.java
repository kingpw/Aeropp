package com.testmod;
import com.testmod.entity.Large_Monster_Airship_Entity;
import com.testmod.entity.Undead_Sky_City_Entity;
import com.testmod.entity.Warship_Entity;
import com.testmod.entity.ShellEntity;
import com.testmod.entity.Flak_Shell_Entity;
import com.testmod.entity.Flak_Cannon_Entity;
import com.testmod.entity.Laser_Zombie_Entity;
import com.testmod.item.laser_gun;
import com.testmod.item.Flak_Shell_Item;
import com.testmod.network.Laser_Fired_Payload;
import com.testmod.network.Laser_Reload_Payload;
import com.testmod.network.Warship_Laser_Payload;
import com.testmod.network.Laser_Zombie_Fired_Payload;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.possible_triangle.create_jetpack.Constants;
import com.possible_triangle.create_jetpack.item.JetpackItem;
import com.possible_triangle.flightlib.api.IJetpack;
import com.possible_triangle.flightlib.neoforge.api.NeoForgeFlightLib;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.equipment.armor.BacktankBlock;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Test_Mod.MODID)
public class Test_Mod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "testmod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "testmod" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "testmod" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "testmod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    // Create a Deferred Register to hold BlockEntityTypes
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    // Create a Deferred Register to hold EntityTypes
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    // Create a Deferred Register to hold ParticleTypes（自定义粒子：激光等）
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);

    // ========== 新增：激光枪 ==========
    public static final DeferredItem<laser_gun> LASER_GUN =
            ITEMS.register("laser_gun",
                    () -> new laser_gun(new Item.Properties().stacksTo(1).durability(laser_gun.MAX_AMMO)));
    /** 激光粒子（客户端绑定 Laser_Particle.Provider，寿命 6 tick 快速消散） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LASER_PARTICLE =
            PARTICLES.register("laser_particle", () -> new SimpleParticleType(false));
    /** 高射炮弹破片火花粒子（客户端绑定 Flak_Burst_Particle.Provider） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAK_BURST_PARTICLE =
            PARTICLES.register("flak_burst", () -> new SimpleParticleType(false));
    /** 高射炮弹黑色烟雾粒子（爆炸残留约 1s，客户端绑定 Flak_Smoke_Particle.Provider） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAK_SMOKE_PARTICLE =
            PARTICLES.register("flak_smoke", () -> new SimpleParticleType(false));
    // =================================

    // 创造标签页（Test Mod）：放加强喷气背包和各怪物刷怪蛋
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.testmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(EnhancedJetpack::createEnhancedStack)
            .displayItems((parameters, output) -> {
                output.accept(EnhancedJetpack.createEnhancedStack());// 加强喷气背包
                output.accept(createLargeMonsterAirshipEgg());// 大型怪物飞艇刷怪蛋
                output.accept(createUndeadSkyCityEgg());// 亡灵天城刷怪蛋
                output.accept(createWarshipEgg());// 战舰刷怪蛋
                output.accept(LASER_GUN.get());                               // 🆕 激光枪
                output.accept(createFlakShellStack());                         // 🆕 高射炮弹
                output.accept(createFlakCannonEgg());                          // 🆕 高射炮刷怪蛋
                output.accept(createLaserZombieEgg());                         // 🆕 激光僵尸刷怪蛋
            }).build());

    // ---- 加强喷气背包（全新物品 + 独立放置方块）----
    // 放置方块：自己的 EnhancedJetpackBlock（继承 Create 的 BacktankBlock），
    // 使用自己的方块实体类型，避免 create_jetpack 的合法方块校验崩溃。
    public static final DeferredBlock<EnhancedJetpackBlock> ENHANCED_JETPACK_BLOCK = BLOCKS.register("enhanced_jetpack", () -> new EnhancedJetpackBlock(
            BlockBehaviour.Properties.ofFullCopy(SharedProperties.netheriteMetal())
    ));

    // 方块实体类型：BacktankBlockEntity（Create 的油箱/取回逻辑），合法方块 = 咱们的方块。
    // 顶部旋转杆由客户端注册的 KineticBlockEntityRenderer 渲染（见构造器 RegisterRenderersEvent）。
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BacktankBlockEntity>> ENHANCED_JETPACK_BE = BLOCK_ENTITIES.register("enhanced_jetpack", Test_Mod::createEnhancedJetpackBlockEntity);

    // 加强喷气背包：复用 create_jetpack 的 JetpackItem.Layered（飞行逻辑一致），
    // 但用自己的 id / 物品贴图 / 穿戴贴图 / 放置物（第 4 参数）。
    public static final DeferredItem<JetpackItem.Layered> ENHANCED_JETPACK = ITEMS.register("enhanced_jetpack", Test_Mod::createEnhancedJetpack);

    // 放置物：独立的 BacktankBlockItem，actualItem 指向加强喷气背包，
    // 这样把背包放下来再右击取回时，拿回的是加强版而不是原版。
    public static final DeferredItem<BacktankItem.BacktankBlockItem> ENHANCED_JETPACK_PLACEABLE = ITEMS.register("enhanced_jetpack_placeable", () -> new BacktankItem.BacktankBlockItem(
            ENHANCED_JETPACK_BLOCK.get(),
            () -> ENHANCED_JETPACK.get(),
            new Item.Properties().stacksTo(1)
    ));

    // ---- 大型怪物飞艇（12×4×4 多部件飞行要塞 + 两侧炮塔）----
    // 本体 AABB 只用于追踪/剔除，真实碰撞由 5 个 PartEntity 提供（3 段舰体 + 2 炮塔）。
    public static final DeferredHolder<EntityType<?>, EntityType<Large_Monster_Airship_Entity>> LARGE_MONSTER_AIRSHIP = ENTITY_TYPES.register("large_monster_airship",
            () -> EntityType.Builder.of(Large_Monster_Airship_Entity::new, MobCategory.MONSTER)
                    .sized(4.0F, 4.0F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("large_monster_airship"));

    // 刷怪蛋（钢铁灰 → 警示黄）；用 DeferredSpawnEggItem（SpawnEggItem 直接传 EntityType 的构造器已过时）
    public static final DeferredItem<SpawnEggItem> LARGE_MONSTER_AIRSHIP_SPAWN_EGG = ITEMS.register("large_monster_airship_spawn_egg",
            () -> new DeferredSpawnEggItem(LARGE_MONSTER_AIRSHIP, 0x6E7480, 0xCEAC46, new Item.Properties()));

    // ---- 亡灵天城（气囊 20×5×5 + 吊舱纺锤 + 每侧 3 门炮塔；P2 骨架：飞行游荡）----
    public static final DeferredHolder<EntityType<?>, EntityType<Undead_Sky_City_Entity>> UNDEAD_SKY_CITY = ENTITY_TYPES.register("undead_sky_city",
            () -> EntityType.Builder.of(Undead_Sky_City_Entity::new, MobCategory.MONSTER)
                    .sized(6.0F, 8.0F)
                    .clientTrackingRange(10)
                    .build("undead_sky_city"));

    // 刷怪蛋（灰白气囊 → 深铁吊舱）；DeferredSpawnEggItem 理由同上
    public static final DeferredItem<SpawnEggItem> UNDEAD_SKY_CITY_SPAWN_EGG = ITEMS.register("undead_sky_city_spawn_egg",
            () -> new DeferredSpawnEggItem(UNDEAD_SKY_CITY, 0xD2D0CA, 0x3E4148, new Item.Properties()));

    // ---- 战舰（60 格长大型飞行敌对生物：纺锤舰体 + 6 门双联装炮塔 Twin_Turret）----
    // 本体 AABB 负责物理碰撞/追踪，攻击判定由 14 段船体部件 + 6 个炮塔部件提供。
    public static final DeferredHolder<EntityType<?>, EntityType<Warship_Entity>> WARSHIP = ENTITY_TYPES.register("warship",
            () -> EntityType.Builder.of(Warship_Entity::new, MobCategory.MONSTER)
                    .sized(8.0F, 7.0F)
                    .clientTrackingRange(10)
                    .build("warship"));

    // 刷怪蛋（舰体白 → 甲板灰）
    public static final DeferredItem<SpawnEggItem> WARSHIP_SPAWN_EGG = ITEMS.register("warship_spawn_egg",
            () -> new DeferredSpawnEggItem(WARSHIP, 0xE8E8E6, 0x7A7F88, new Item.Properties()));




    // ---- 炮弹投射物（自定义炮弹，行为模仿恶魂火球，撞方块/实体爆炸）----
    // 投射物不是 LivingEntity，无需实体属性；也不是 Mob，不能用 SpawnEggItem。
    // 它由舰载炮塔（Ship_Turret）调用 new ShellEntity(level, 射手, 方向, 威力) 发射。
    public static final DeferredHolder<EntityType<?>, EntityType<ShellEntity>> SHELL = ENTITY_TYPES.register("shell",
            () -> EntityType.Builder.<ShellEntity>of(ShellEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .build("shell"));

    // ---- 高射炮弹（可投掷投射物，参考雪球；直线飞行，撞击爆炸破坏方块）----
    public static final DeferredHolder<EntityType<?>, EntityType<Flak_Shell_Entity>> FLAK_SHELL = ENTITY_TYPES.register("flak_shell",
            () -> EntityType.Builder.<Flak_Shell_Entity>of(Flak_Shell_Entity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(160)
                    .build("flak_shell"));
    /** 投掷发射高射炮弹的物品（参考雪球，便于测试） */
    public static final DeferredItem<Flak_Shell_Item> FLAK_SHELL_ITEM = ITEMS.register("flak_shell",
            () -> new Flak_Shell_Item(new Item.Properties().stacksTo(16)));

    // ---- 高射炮固定炮台（单碰撞箱敌对生物：固定地面、360° 索敌所有敌对目标、发射高射炮弹）----
    // 本体 2×2×2 单箱；炮塔即实体本身，tick 里独立索敌/转向/开火（见 Flak_Cannon_Entity）。
    public static final DeferredHolder<EntityType<?>, EntityType<Flak_Cannon_Entity>> FLAK_CANNON = ENTITY_TYPES.register("flak_cannon",
            () -> EntityType.Builder.of(Flak_Cannon_Entity::new, MobCategory.MONSTER)
                    .sized(2.0F, 2.0F)
                    .clientTrackingRange(160)
                    .build("flak_cannon"));

    // 刷怪蛋（深灰机座 → 亮灰炮管）
    public static final DeferredItem<SpawnEggItem> FLAK_CANNON_SPAWN_EGG = ITEMS.register("flak_cannon_spawn_egg",
            () -> new DeferredSpawnEggItem(FLAK_CANNON, 0x4A4A4F, 0xB8B8BD, new Item.Properties()));

    // ---- 激光僵尸（原版僵尸 + 手持激光枪 + 激光射击；继承 Zombie 保留全部原版能力）----
    public static final DeferredHolder<EntityType<?>, EntityType<Laser_Zombie_Entity>> LASER_ZOMBIE = ENTITY_TYPES.register("laser_zombie",
            () -> EntityType.Builder.of(Laser_Zombie_Entity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(80)
                    .build("laser_zombie"));

    // 刷怪蛋（僵尸绿 → 激光红）
    public static final DeferredItem<SpawnEggItem> LASER_ZOMBIE_SPAWN_EGG = ITEMS.register("laser_zombie_spawn_egg",
            () -> new DeferredSpawnEggItem(LASER_ZOMBIE, 0x4CA24C, 0xD42A1E, new Item.Properties()));



    /** 创造标签页条目用：亡灵天城刷怪蛋 */
    private static ItemStack createUndeadSkyCityEgg() {
        return new ItemStack(UNDEAD_SKY_CITY_SPAWN_EGG.get());
    }

    /** 创造标签页条目用：战舰刷怪蛋 */
    private static ItemStack createWarshipEgg() {
        return new ItemStack(WARSHIP_SPAWN_EGG.get());
    }

    /** 创造标签页条目用：大型怪物飞艇刷怪蛋 */
    private static ItemStack createLargeMonsterAirshipEgg() {
        return new ItemStack(LARGE_MONSTER_AIRSHIP_SPAWN_EGG.get());
    }

    /** 创造标签页条目用：高射炮弹 */
    private static ItemStack createFlakShellStack() {
        return new ItemStack(FLAK_SHELL_ITEM.get());
    }

    /** 创造标签页条目用：高射炮刷怪蛋 */
    private static ItemStack createFlakCannonEgg() {
        return new ItemStack(FLAK_CANNON_SPAWN_EGG.get());
    }

    /** 创造标签页条目用：激光僵尸刷怪蛋 */
    private static ItemStack createLaserZombieEgg() {
        return new ItemStack(LASER_ZOMBIE_SPAWN_EGG.get());
    }

    /** 加强喷气背包工厂：方法体内可前向引用 ENHANCED_JETPACK_PLACEABLE */
    private static JetpackItem.Layered createEnhancedJetpack() {
        return new JetpackItem.Layered(
                new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(),
                ArmorMaterials.NETHERITE,
                ResourceLocation.parse("testmod:enhanced_diving"),
                new ItemEntry<>(Constants.REGISTRATE, ENHANCED_JETPACK_PLACEABLE)
        );
    }

    /** 加强喷气背包方块实体工厂（1.21.1 的 BlockEntitySupplier 是 (pos, state) 两参） */
    private static BlockEntityType<BacktankBlockEntity> createEnhancedJetpackBlockEntity() {
        return BlockEntityType.Builder.of(
                (pos, state) -> new BacktankBlockEntity(ENHANCED_JETPACK_BE.get(), pos, state),
                ENHANCED_JETPACK_BLOCK.get()
        ).build(null);
    }

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Test_Mod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so block entity types get registered
        BLOCK_ENTITIES.register(modEventBus);
        // Register the Deferred Register to the mod event bus so entity types get registered
        ENTITY_TYPES.register(modEventBus);
        PARTICLES.register(modEventBus);

        // 注册实体属性（血量/速度等）
        modEventBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(LARGE_MONSTER_AIRSHIP.get(), Large_Monster_Airship_Entity.createAttributes().build());
            event.put(UNDEAD_SKY_CITY.get(), Undead_Sky_City_Entity.createAttributes().build());
            event.put(WARSHIP.get(), Warship_Entity.createAttributes().build());
            event.put(FLAK_CANNON.get(), Flak_Cannon_Entity.createAttributes().build());
            event.put(LASER_ZOMBIE.get(), Laser_Zombie_Entity.createAttributes().build());
        });

        // Register the flight capability for the enhanced jetpack, exactly like create_jetpack
        // does for its own items (provider casts the Item to IJetpack).
        modEventBus.addListener(RegisterCapabilitiesEvent.class, event ->
                event.registerItem(NeoForgeFlightLib.ITEM_CAPABILITY, (stack, context) -> (IJetpack) stack.getItem(), ENHANCED_JETPACK.get()));

        // 注册网络包：激光枪击发确认（服务端 → 射手客户端，触发光束粒子与后座）+ 换弹（客户端 → 服务端）
        // + 战舰激光齐射（服务端 → 客户端，画三条光束）
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event ->
                event.registrar("1")
                        .playToClient(Laser_Fired_Payload.TYPE, Laser_Fired_Payload.STREAM_CODEC, Laser_Fired_Payload::handle)
                        .playToServer(Laser_Reload_Payload.TYPE, Laser_Reload_Payload.STREAM_CODEC, Laser_Reload_Payload::handle)
                        .playToClient(Warship_Laser_Payload.TYPE, Warship_Laser_Payload.STREAM_CODEC, Warship_Laser_Payload::handle)
                        .playToClient(Laser_Zombie_Fired_Payload.TYPE, Laser_Zombie_Fired_Payload.STREAM_CODEC, Laser_Zombie_Fired_Payload::handle));

        // 注册静态事件处理（加强背包近战加成 + 击退 + tooltip）
        NeoForge.EVENT_BUS.register(ModEvents.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 注册应力冲击值（复制 Create 铜背罐的应力）：这样戴上工程师眼镜
        // 才能显示速度/应力状态（KineticBlockEntity 在应力为 0 时不显示）。
        BlockStressValues.IMPACTS.register(ENHANCED_JETPACK_BLOCK.get(),
                () -> BlockStressValues.getImpact(AllBlocks.COPPER_BACKTANK.get()));
    }

    // ============================================================
    //  内部类（精简文件个数）：加强背包工具 / 放置方块 / 事件
    // ============================================================

    /** 加强喷气背包工具（原 EnhancedJetpack.java） */
    public static final class EnhancedJetpack {
        private EnhancedJetpack() {
        }

        /** 判断物品栈是否是加强喷气背包 */
        public static boolean isEnhanced(ItemStack stack) {
            return stack != null && !stack.isEmpty() && stack.getItem() == Test_Mod.ENHANCED_JETPACK.get();
        }

        /** 生成一个加强喷气背包（创造模式标签页条目用） */
        public static ItemStack createEnhancedStack() {
            return new ItemStack(Test_Mod.ENHANCED_JETPACK.get());
        }
    }

    /** 加强喷气背包的放置方块（原 EnhancedJetpackBlock.java） */
    public static class EnhancedJetpackBlock extends BacktankBlock {

        public EnhancedJetpackBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntityType<? extends BacktankBlockEntity> getBlockEntityType() {
            return Test_Mod.ENHANCED_JETPACK_BE.get();
        }
    }

    /**
     * 事件（原 ModEvents.java）：穿戴加强背包时近战伤害 ×1.2 + 击退 + tooltip。
     * 击退实现借鉴 Create 纸板剑：方向用玩家朝向 yRot，强度经 ×0.5 后传给 knockback。
     */
    public static final class ModEvents {

        /** 近战伤害倍率：基础 ×1.2 */
        public static final float MELEE_DAMAGE_MULTIPLIER = 1.2f;

        /** 击退强度（纸板剑同款基准 2.0：knockback 实际生效 = strength × 0.5 = 约 1 格） */
        public static final float KNOCKBACK_STRENGTH = 2.0f;

        private ModEvents() {
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            var source = event.getSource();
            // 只有玩家主动的直接近战伤害才生效
            if (!(source.getEntity() instanceof Player player)) {
                return;
            }
            if (source.getDirectEntity() != player) {
                return;
            }
            // 玩家胸甲槽必须穿着加强喷气背包
            if (EnhancedJetpack.isEnhanced(player.getItemBySlot(EquipmentSlot.CHEST))) {
                // 伤害 ×1.2
                event.setAmount(event.getAmount() * MELEE_DAMAGE_MULTIPLIER);
                // 击退（借鉴纸板剑）：方向 = 玩家朝向，强度 ×0.5 后生效
                LivingEntity target = event.getEntity();
                float yRot = player.getYRot();
                target.stopRiding();
                target.knockback(
                        KNOCKBACK_STRENGTH * 0.5F,
                        Mth.sin(yRot * Mth.DEG_TO_RAD),
                        -Mth.cos(yRot * Mth.DEG_TO_RAD)
                );
            }
        }

        /** 给加强喷气背包加一行金色说明 */
        @SubscribeEvent
        public static void onItemTooltip(ItemTooltipEvent event) {
            if (EnhancedJetpack.isEnhanced(event.getItemStack())) {
                event.getToolTip().add(
                        Component.translatable("item.testmod.enhanced_jetpack.tooltip")
                                .withStyle(ChatFormatting.GOLD)
                );
            }
        }
    }
}
