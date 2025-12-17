package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.slf4j.LoggerFactory

object LoginCommand {
    private val logger = LoggerFactory.getLogger("mineauth")

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("login")
                .then(argument("password", StringArgumentType.word())
                    .executes { context ->
                        execute(
                            context.source,
                            StringArgumentType.getString(context, "password")
                        )
                    }
                )
        )
    }

    private fun execute(source: ServerCommandSource, password: String): Int {
        val player = source.player ?: return sendError(source, LanguageManager.tr("mineauth.only_player_command"))

        val uuid = player.uuid
        val playerName = player.name.string
        val ipAddress = MineAuth.getPlayerIpAddress(player)

        // 检查会话锁定
        val session = MineAuth.getPlayerSession(uuid)
        if (session != null && session.isLocked()) {
            val remainingTime = session.getCoolDownRemainingTime()
            val minutes = remainingTime / 60
            val seconds = remainingTime % 60
            return sendError(source, LanguageManager.tr("mineauth.too_many_attempts", minutes, seconds))
        }

        // 检查是否已登录
        if (MineAuth.isValidSession(uuid, ipAddress)) {
            return sendMessage(source, LanguageManager.tr("mineauth.already_logged_in"), Formatting.GREEN)
        }

        // 检查是否已注册
        if (!MineAuth.isPlayerRegistered(uuid)) {
            return sendError(source, LanguageManager.tr("mineauth.not_registered"))
        }

        // 验证密码
        if (MineAuth.verifyPassword(uuid, password)) {
            MineAuth.loginPlayer(uuid, playerName, ipAddress)
            return sendMessage(source, LanguageManager.tr("mineauth.login_success"), Formatting.GREEN)
        } else {
            // 登录失败处理
            if (session != null) {
                val shouldKick = session.incrementFailedAttempts()
                MineAuth.saveSessions()

                if (MineAuthConfig.config.enableLoginLimit) {
                    if (shouldKick) {
                        val remainingTime = session.getCoolDownRemainingTime()
                        val minutes = remainingTime / 60
                        val seconds = remainingTime % 60
                        player.networkHandler.disconnect(LanguageManager.tr("mineauth.too_many_attempts", minutes, seconds))
                        logger.info(LanguageManager.tr("mineauth.kicked_too_many_attempts", minutes, seconds).string)
                        return 0
                    } else {
                        val remainingAttempts = session.getRemainingAttempts()
                        if (remainingAttempts > 0) {
                            return sendError(source, LanguageManager.tr("mineauth.remaining_attempts", remainingAttempts))
                        }
                    }
                }
            }
            return sendError(source, LanguageManager.tr("mineauth.password_incorrect"))
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