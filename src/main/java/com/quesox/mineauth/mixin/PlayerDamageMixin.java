package com.quesox.mineauth.mixin;

import com.quesox.mineauth.MineAuth;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD;

@Mixin(ServerPlayerEntity.class)
public class PlayerDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (!MineAuth.isPlayerLoggedIn(player.getUuid())) {
            // 如果是虚空伤害或来自管理员的伤害，允许伤害（防止卡bug）
            if (source.isOf(OUT_OF_WORLD) || source.isSourceCreativePlayer()) {
                return;
            }

            // 阻止所有其他伤害
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}