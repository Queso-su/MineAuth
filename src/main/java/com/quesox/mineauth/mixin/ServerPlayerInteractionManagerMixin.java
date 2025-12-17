package com.quesox.mineauth.mixin;

import com.quesox.mineauth.MineAuth;
import com.quesox.mineauth.LanguageManager;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {

    // 阻止未登录玩家破坏方块
    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void onTryBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerInteractionManager manager = (ServerPlayerInteractionManager) (Object) this;

        try {
            var playerField = ServerPlayerInteractionManager.class.getDeclaredField("player");
            playerField.setAccessible(true);
            var player = (ServerPlayerEntity) playerField.get(manager);

            if (!MineAuth.isPlayerLoggedIn(player.getUuid())) {
                player.sendMessage(LanguageManager.INSTANCE.tr("mineauth.block_break_blocked"), true);
                cir.setReturnValue(false);
                cir.cancel();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 阻止未登录玩家放置方块
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(ServerPlayerEntity player, World world, ItemStack stack,
                                 Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (!MineAuth.isPlayerLoggedIn(player.getUuid())) {
            player.sendMessage(LanguageManager.INSTANCE.tr("mineauth.block_place_blocked"), true);
            cir.setReturnValue(ActionResult.FAIL);
            cir.cancel();
        }
    }

    // 阻止未登录玩家使用物品（右键点击空气）
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void onInteractItem(ServerPlayerEntity player, World world, ItemStack stack,
                                Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!MineAuth.isPlayerLoggedIn(player.getUuid())) {
            player.sendMessage(LanguageManager.INSTANCE.tr("mineauth.item_use_blocked"), true);
            cir.setReturnValue(ActionResult.FAIL);
            cir.cancel();
        }
    }
}