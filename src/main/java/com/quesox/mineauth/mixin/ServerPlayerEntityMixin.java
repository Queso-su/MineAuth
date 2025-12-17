// ServerPlayerEntityMixin.java (优化版)
package com.quesox.mineauth.mixin;

import com.quesox.mineauth.MineAuth;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @Unique
    private Vec3d mineAuth$initialPosition = null;

    @Unique
    private int mineAuth$messageCooldown = 0;

    @Unique
    private int mineAuth$tickCounter = 0;

    // 每40个tick检查一次
    @Unique
    private static final int CHECK_INTERVAL = 40;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (!MineAuth.isPlayerLoggedIn(player.getUuid())) {
            // 记录初始位置
            if (mineAuth$initialPosition == null) {
                mineAuth$initialPosition = player.getEntityPos();
            }

            // 每5个tick检查一次，减少服务器压力
            mineAuth$tickCounter++;
            if (mineAuth$tickCounter >= CHECK_INTERVAL) {
                mineAuth$tickCounter = 0;

                // 检查玩家是否移动了
                double distance = player.squaredDistanceTo(mineAuth$initialPosition);
                if (distance > 2) { // 如果移动超过0.3格
                    // 传送回初始位置
                    player.teleport(
                            player.getEntityWorld(),
                            mineAuth$initialPosition.x,
                            mineAuth$initialPosition.y,
                            mineAuth$initialPosition.z,
                            Set.of(),
                            player.getYaw(),
                            player.getPitch(),
                            false
                    );

                    // 设置速度为0
                    player.setVelocity(Vec3d.ZERO);

                    // 发送消息（有冷却时间避免刷屏）
                    if (mineAuth$messageCooldown <= 0) {
                        player.sendMessage(net.minecraft.text.Text.literal("§c请先登录后再移动！"), true);
                        mineAuth$messageCooldown = 20; // 1秒冷却（减少为1秒）
                    }
                }
            }

            // 减少消息冷却（仍然每tick执行，但判断很简单）
            if (mineAuth$messageCooldown > 0) {
                mineAuth$messageCooldown--;
            }

            // 这些操作仍然每tick执行，因为它们很简单且重要
            // 确保玩家不会下落
            if (!player.isOnGround() && player.getVelocity().y < 0) {
                player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
                player.fallDistance = 0;
            }

            // 防止玩家跳跃
            player.setVelocity(player.getVelocity().x, Math.max(player.getVelocity().y, 0), player.getVelocity().z);
        } else {
            // 登录后重置
            mineAuth$initialPosition = null;
            mineAuth$messageCooldown = 0;
            mineAuth$tickCounter = 0;
        }
    }

    // 阻止跳跃
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (!MineAuth.isPlayerLoggedIn(player.getUuid())) {
            ci.cancel();
        }
    }
}