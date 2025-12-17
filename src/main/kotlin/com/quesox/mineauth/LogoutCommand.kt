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
        val player = source.player
        if (player == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令！"))
            return 0
        }

        val uuid = player.uuid

        // 检查玩家是否已登录
        if (!MineAuth.isPlayerLoggedIn(uuid)) {
            source.sendMessage(Text.literal("§c您还没有登录！").styled { it.withColor(Formatting.RED) })
            return 0
        }

        // 执行登出
        if (MineAuth.logoutPlayer(uuid)) {
            source.sendMessage(Text.literal("§a登出成功！重新加入服务器后需要重新登录。").styled { it.withColor(Formatting.GREEN) })
            return 1
        } else {
            source.sendMessage(Text.literal("§c登出失败！").styled { it.withColor(Formatting.RED) })
            return 0
        }
    }
}