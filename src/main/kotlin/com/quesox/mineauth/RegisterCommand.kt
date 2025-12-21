package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource

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
        val player = source.player ?: return CommandUtils.sendErr(source, "mineauth.only_player_command")

        if (MineAuth.isPlayerLoggedIn(player.uuid)) {
            return CommandUtils.sendSuc(source, "mineauth.already_logged_in")
        }

        if (password.length < 6) {
            return CommandUtils.sendErr(source, "mineauth.password_too_short")
        }

        if (password != confirmPassword) {
            return CommandUtils.sendErr(source, "mineauth.password_mismatch")
        }

        if (MineAuth.isPlayerRegistered(player.uuid)) {
            return CommandUtils.sendErr(source, "mineauth.already_registered")
        }

        val ipAddress = MineAuth.getPlayerIpAddress(player)

        // 检查同一IP是否已有其他账号（sameIPsameAccount 功能）
        if (MineAuthConfig.config.ipVerify && MineAuthConfig.config.sameIPSameAccount) {
            val (ipRegistered, existingAccount) = MineAuth.isIpAlreadyRegistered(ipAddress)
            if (ipRegistered) {
                return CommandUtils.sendErr(source, "mineauth.ip_already_registered", existingAccount ?: LanguageManager.tr("mineauth.unknown").string)
            }
        }

        return if (MineAuth.registerPlayer(
                player.uuid,
                player.name.string,
                password,
                ipAddress
            )) {
            CommandUtils.sendSuc(source, "mineauth.register_success")
        } else {
            CommandUtils.sendErr(source, "mineauth.register_failed")
        }
    }
}