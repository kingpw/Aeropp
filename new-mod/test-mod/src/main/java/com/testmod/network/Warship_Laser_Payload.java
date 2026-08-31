package com.testmod.network;

import com.testmod.Test_Mod;
import com.testmod.Test_Mod_Client;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：战舰一次激光齐射的三条光束（各 = start(3)+dir(3)+len(1) 共 7 个 float）。
 * 客户端据此在本地画光束粒子（Laser_Beam.spawnBeam），避免每条光束逐颗粒子发包。
 */
public record Warship_Laser_Payload(float[] b0, float[] b1, float[] b2) implements CustomPacketPayload {

    public static final Type<Warship_Laser_Payload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Test_Mod.MODID, "warship_laser"));

    /** 一条光束：7 个 float（start,dir,len）；无 ByteBufCodecs.FLOAT_ARRAY，手动读写 */
    private static final StreamCodec<ByteBuf, float[]> BEAM = StreamCodec.of(
            (buf, arr) -> {
                for (float f : arr) buf.writeFloat(f);
            },
            buf -> {
                float[] a = new float[7];
                for (int i = 0; i < 7; i++) a[i] = buf.readFloat();
                return a;
            });

    public static final StreamCodec<ByteBuf, Warship_Laser_Payload> STREAM_CODEC = StreamCodec.composite(
            BEAM, Warship_Laser_Payload::b0,
            BEAM, Warship_Laser_Payload::b1,
            BEAM, Warship_Laser_Payload::b2,
            Warship_Laser_Payload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** playToClient 只在客户端执行；Test_Mod_Client 引用惰性解析，服务端不加载。 */
    public static void handle(Warship_Laser_Payload payload, IPayloadContext context) {
        context.enqueueWork(() -> Test_Mod_Client.onWarshipLaser(payload));
    }
}
