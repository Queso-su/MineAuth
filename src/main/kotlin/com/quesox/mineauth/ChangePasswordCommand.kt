package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting

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
        val player = source.player ?: return sendError(source, LanguageManager.tr("mineauth.only_player_command"))

        val uuid = player.uuid

        // 检查玩家是否已登录
        if (!MineAuth.isPlayerLoggedIn(uuid)) {
            return sendError(source, LanguageManager.tr("mineauth.not_logged_in"))
        }

        // 检查新密码长度
        if (newPassword.length < 6) {
            return sendError(source, LanguageManager.tr("mineauth.new_password_too_short"))
        }

        // 检查新旧密码是否相同
        if (oldPassword == newPassword) {
            return sendError(source, LanguageManager.tr("mineauth.password_same"))
        }

        // 修改密码
        return if (MineAuth.changePassword(uuid, oldPassword, newPassword)) {
            sendMessage(source, LanguageManager.tr("mineauth.change_password_success"), Formatting.GREEN)
        } else {
            sendError(source, LanguageManager.tr("mineauth.old_password_incorrect"))
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