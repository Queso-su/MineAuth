// LoginCommand.kt (简化版)
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
        val player = source.player ?: return sendError(source, "只有玩家可以执行此命令！")

        val uuid = player.uuid
        val playerName = player.name.string
        val ipAddress = MineAuth.getPlayerIpAddress(player)

        // 检查会话锁定
        val session = MineAuth.getPlayerSession(uuid)
        if (session != null && session.isLocked()) {
            val remainingTime = session.getCoolDownRemainingTime()
            val minutes = remainingTime / 60
            val seconds = remainingTime % 60
            return sendError(source, "§c登录失败次数过多，请等待 ${minutes}分${seconds}秒后再试！")
        }

        // 检查是否已登录
        if (MineAuth.isValidSession(uuid, ipAddress)) {
            return sendMessage(source, "§a您已经登录了！", Formatting.GREEN)
        }

        // 检查是否已注册
        if (!MineAuth.isPlayerRegistered(uuid)) {
            return sendError(source, "§c您还没有注册，请使用 /register 或者 /reg 注册账户！")
        }

        // 验证密码
        if (MineAuth.verifyPassword(uuid, password)) {
            MineAuth.loginPlayer(uuid, playerName, ipAddress)
            return sendMessage(source, "§a登录成功！", Formatting.GREEN)
        } else {
            // 登录失败处理
            if (session != null) {
                val shouldKick = session.incrementFailedAttempts()
                MineAuth.saveSessions()

                if (MineAuthConfig.config.enableLoginLimit) {
                    if (shouldKick) {
                        player.networkHandler.disconnect(Text.literal("§c登录尝试次数过多，请等待一段时间后再登录！"))
                        logger.info("玩家 $playerName 因登录尝试过多被踢出")
                        return 0
                    } else {
                        val remainingAttempts = session.getRemainingAttempts()
                        if (remainingAttempts > 0) {
                            return sendError(source, "§c密码错误！还剩 $remainingAttempts 次尝试机会")
                        }
                    }
                }
            }
            return sendError(source, "§c密码错误！")
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