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
        val player = source.player ?: return sendError(source, LanguageManager.tr("mineauth.only_player_command"))

        if (MineAuth.isPlayerLoggedIn(player.uuid)) {
            return sendMessage(source, LanguageManager.tr("mineauth.already_logged_in"), Formatting.GREEN)
        }

        if (password.length < 6) {
            return sendError(source, LanguageManager.tr("mineauth.password_too_short"))
        }

        if (password != confirmPassword) {
            return sendError(source, LanguageManager.tr("mineauth.password_mismatch"))
        }

        if (MineAuth.isPlayerRegistered(player.uuid)) {
            return sendError(source, LanguageManager.tr("mineauth.already_registered"))
        }

        val ipAddress = MineAuth.getPlayerIpAddress(player)

        // 检查同一IP是否已有其他账号（sameIPsameAccount 功能）
        if (MineAuthConfig.config.ipVerify && MineAuthConfig.config.sameIPSameAccount) {
            val (ipRegistered, existingAccount) = MineAuth.isIpAlreadyRegistered(ipAddress)
            if (ipRegistered) {
                return sendError(source, LanguageManager.tr("mineauth.ip_already_registered", existingAccount ?: LanguageManager.tr("mineauth.unknown").string))
            }
        }

        return if (MineAuth.registerPlayer(
                player.uuid,
                player.name.string,
                password,
                ipAddress
            )) {
            sendMessage(source, LanguageManager.tr("mineauth.register_success"), Formatting.GREEN)
        } else {
            sendError(source, LanguageManager.tr("mineauth.register_failed"))
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