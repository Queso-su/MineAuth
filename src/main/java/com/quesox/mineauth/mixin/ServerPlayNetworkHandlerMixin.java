package com.quesox.mineauth.mixin;

import com.quesox.mineauth.MineAuth;
import com.quesox.mineauth.LanguageManager;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow public ServerPlayerEntity player;

    // ==================== 聊天消息处理 ====================
    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        if (!MineAuth.isPlayerLoggedIn(player.getUuid())) {
            String message = packet.chatMessage().trim();

            // 检查是否是允许的命令
            if (message.startsWith("/")) {
                boolean allowed = false;
                String[] allowedCommands = {"login", "register", "reg"};
                for (String cmd : allowedCommands) {
                    if (message.toLowerCase().startsWith("/" + cmd + " ") || message.equalsIgnoreCase("/" + cmd)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    player.sendMessage(LanguageManager.INSTANCE.tr("mineauth.command_blocked"), true);
                    ci.cancel();
                }
            } else {
                player.sendMessage(LanguageManager.INSTANCE.tr("mineauth.chat_blocked"), true);
                ci.cancel();
            }
        }
    }

    // ==================== 命令执行处理 ====================
    @Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
    private void onCommandExecution(CommandExecutionC2SPacket packet, CallbackInfo ci) {
        if (!MineAuth.isPlayerLoggedIn(player.getUuid())) {
            String command = packet.command().trim();
            boolean allowed = false;
            String[] allowedCommands = {"login", "register", "reg"};
            for (String cmd : allowedCommands) {
                if (command.toLowerCase().startsWith(cmd + " ") || command.equalsIgnoreCase(cmd)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                player.sendMessage(LanguageManager.INSTANCE.tr("mineauth.command_blocked"), true);
                ci.cancel();
            }
        }
    }
}