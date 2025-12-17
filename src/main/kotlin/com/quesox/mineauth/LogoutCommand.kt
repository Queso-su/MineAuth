package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object LogoutCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("logout")
                .executes { context ->
                    execute(context.source)
                }
        )
    }

    private fun execute(source: ServerCommandSource): Int {
        val player = source.player ?: return sendError(source, LanguageManager.tr("mineauth.only_player_command"))

        val uuid = player.uuid

        // 检查玩家是否已登录
        if (!MineAuth.isPlayerLoggedIn(uuid)) {
            return sendError(source, LanguageManager.tr("mineauth.not_logged_in"))
        }

        // 执行登出
        return if (MineAuth.logoutPlayer(uuid)) {
            sendMessage(source, LanguageManager.tr("mineauth.logout_success"), Formatting.GREEN)
        } else {
            sendError(source, LanguageManager.tr("mineauth.logout_failed"))
        }
    }

    private fun sendMessage(source: ServerCommandSource, message: Text, formatting: Formatting): Int {
        source.sendMessage(message.copy().styled { it.withColor(formatting) })
        return 1
    }

    private fun sendError(source: ServerCommandSource, message: Text): Int {
        source.sendMessage(message.copy().styled { it.withColor(Formatting.RED) })
        return 0
    }
}