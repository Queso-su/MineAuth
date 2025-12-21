package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource

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
        val player = source.player ?: return CommandUtils.sendErr(source, "mineauth.only_player_command")
        val uuid = player.uuid

        // 检查玩家是否已登录
        if (!MineAuth.isPlayerLoggedIn(uuid)) {
            return CommandUtils.sendErr(source, "mineauth.not_logged_in")
        }

        // 执行登出
        return if (MineAuth.logoutPlayer(uuid)) {
            CommandUtils.sendSuc(source, "mineauth.logout_success")
        } else {
            CommandUtils.sendErr(source, "mineauth.logout_failed")
        }
    }
}