package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource
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
        val player = source.player ?: return CommandUtils.sendErr(source, "mineauth.only_player_command")
        val uuid = player.uuid
        val playerName = player.name.string
        val ipAddress = MineAuth.getPlayerIpAddress(player)

        // 检查会话锁定
        val session = MineAuth.getPlayerSession(uuid)
        if (session != null && session.isLocked()) {
            val remainingTime = session.getCoolDownRemainingTime()
            val minutes = remainingTime / 60
            val seconds = remainingTime % 60
            return CommandUtils.sendErr(source, "mineauth.too_many_attempts", minutes, seconds)
        }

        // 检查是否已登录
        if (MineAuth.isValidSession(uuid, ipAddress)) {
            return CommandUtils.sendSuc(source, "mineauth.already_logged_in")
        }

        // 检查是否已注册
        if (!MineAuth.isPlayerRegistered(uuid)) {
            return CommandUtils.sendErr(source, "mineauth.not_registered")
        }

        // 验证密码
        if (MineAuth.verifyPassword(uuid, password)) {
            MineAuth.loginPlayer(uuid, playerName, ipAddress)
            return CommandUtils.sendSuc(source, "mineauth.login_success")
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
                            return CommandUtils.sendErr(source, "mineauth.remaining_attempts", remainingAttempts)
                        }
                    }
                }
            }
            return CommandUtils.sendErr(source, "mineauth.password_incorrect")
        }
    }
}