package com.quesox.mineauth.mixin;

import com.quesox.mineauth.MineAuth;
import com.quesox.mineauth.LanguageManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
                player.sendMessage(LanguageManager.INSTANCE.tr("mineauth.entity_interact_blocked"), true);
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
                player.sendMessage(LanguageManager.INSTANCE.tr("mineauth.entity_attack_blocked"), true);
                ci.cancel();
            }
        }
    }
}