// RegisterCommand.kt (添加 sameIPsameAccount 检查)
package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object RegisterCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        listOf("register", "reg").forEach { command ->
            dispatcher.register(
                literal(command)
                    .then(argument("password", StringArgumentType.word())
                        .then(argument("confirmPassword", StringArgumentType.word())
                            .executes { context ->
                                execute(
                                    context.source,
                                    StringArgumentType.getString(context, "password"),
                                    StringArgumentType.getString(context, "confirmPassword")
                                )
                            }
                        )
                    )
            )
        }
    }

    private fun execute(source: ServerCommandSource, password: String, confirmPassword: String): Int {
        val player = source.player ?: return sendError(source, "只有玩家可以执行此命令！")

        if (MineAuth.isPlayerLoggedIn(player.uuid)) {
            return sendMessage(source, "§a您已经登录了！", Formatting.GREEN)
        }

        if (password.length < 6) {
            return sendError(source, "§c密码长度至少为6位！")
        }

        if (password != confirmPassword) {
            return sendError(source, "§c两次输入的密码不一致！")
        }

        if (MineAuth.isPlayerRegistered(player.uuid)) {
            return sendError(source, "§c您已经注册过了，请使用 /login 登录！")
        }

        val ipAddress = MineAuth.getPlayerIpAddress(player)

        // 检查同一IP是否已有其他账号（sameIPsameAccount 功能）
        if (MineAuthConfig.config.ipVerify && MineAuthConfig.config.sameIPSameAccount) {
            val (ipRegistered, existingAccount) = MineAuth.isIpAlreadyRegistered(ipAddress)
            if (ipRegistered) {
                val message = StringBuilder()
                message.append("§c该IP地址已经注册过账号！\n")
                message.append("§e已注册账号: §7$existingAccount\n")
                message.append("§e请使用原账号登录")

                return sendError(source, message.toString())
            }
        }

        return if (MineAuth.registerPlayer(
                player.uuid,
                player.name.string,
                password,
                ipAddress
            )) {
            sendMessage(source, "§a注册成功！已自动登录。", Formatting.GREEN)
        } else {
            sendError(source, "§c注册失败！")
        }
    }

    private fun sendMessage(source: ServerCommandSource, message: String, formatting: Formatting): Int {
        source.sendMessage(Text.literal(message).styled { it.withColor(formatting) })
        return 1
    }

    private fun sendError(source: ServerCommandSource, message: String): Int {
        source.sendMessage(Text.literal(message))
        return 0
    }
}