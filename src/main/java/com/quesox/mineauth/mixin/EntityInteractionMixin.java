
package com.quesox.mineauth.mixin;

import com.quesox.mineauth.MineAuth;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class EntityInteractionMixin {

    // 阻止与实体交互（右键点击实体）
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteract(Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if ((Object) this instanceof ServerPlayerEntity player) {

            if (!MineAuth.isPlayerLoggedIn(player.getUuid())) {
                player.sendMessage(net.minecraft.text.Text.literal("§c请先登录后再与实体交互！"), true);
                cir.setReturnValue(ActionResult.FAIL);
                cir.cancel();
            }
        }
    }

    // 阻止攻击实体
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttack(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {

            if (!MineAuth.isPlayerLoggedIn(player.getUuid())) {
                player.sendMessage(net.minecraft.text.Text.literal("§c请先登录后再攻击实体！"), true);
                ci.cancel();
            }
        }
    }
}