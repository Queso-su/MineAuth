package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource

object ChangePasswordCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        listOf("changepassword", "cpwd").forEach { command ->
            dispatcher.register(
                literal(command)
                    .then(
                        argument("oldPassword", StringArgumentType.word())
                            .then(
                                argument("newPassword", StringArgumentType.word())
                                    .executes { context ->
                                        execute(
                                            context.source,
                                            StringArgumentType.getString(context, "oldPassword"),
                                            StringArgumentType.getString(context, "newPassword")
                                        )
                                    }
                            )
                    )
            )
        }
    }

    private fun execute(source: ServerCommandSource, oldPassword: String, newPassword: String): Int {
        val player = source.player ?: return CommandUtils.sendErr(source, "mineauth.only_player_command")

        val uuid = player.uuid

        // 检查玩家是否已登录
        if (!MineAuth.isPlayerLoggedIn(uuid)) {
            return CommandUtils.sendErr(source, "mineauth.not_logged_in")
        }

        // 检查新密码长度
        if (newPassword.length < 6) {
            return CommandUtils.sendErr(source, "mineauth.new_password_too_short")
        }

        // 检查新旧密码是否相同
        if (oldPassword == newPassword) {
            return CommandUtils.sendErr(source, "mineauth.password_same")
        }

        // 修改密码
        return if (MineAuth.changePassword(uuid, oldPassword, newPassword)) {
            CommandUtils.sendSuc(source, "mineauth.change_password_success")
        } else {
            CommandUtils.sendErr(source, "mineauth.old_password_incorrect")
        }
    }
}