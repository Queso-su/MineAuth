// ItemEntityMixin.java (新建文件，更好的方法)
package com.quesox.mineauth.mixin;

import com.quesox.mineauth.MineAuth;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    // 当物品与玩家碰撞时触发
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void onPlayerCollision(PlayerEntity player, CallbackInfo ci) {
        // 检查是否是服务端玩家
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {

            if (!MineAuth.isPlayerLoggedIn(serverPlayer.getUuid())) {
                // 阻止物品被拾取
                ci.cancel();
            }
        }
    }
}