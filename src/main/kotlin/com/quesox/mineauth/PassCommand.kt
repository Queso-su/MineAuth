package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.quesox.mineauth.CommandUtils.permissionLevelFromInt
import net.minecraft.command.permission.Permission
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource
import java.util.*
import java.util.concurrent.CompletableFuture

object PassCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("pass")
                .requires { source ->
                    source.permissions.hasPermission(Permission.Level(permissionLevelFromInt(3)))
                }
                .then(
                    argument("playerName", StringArgumentType.word())
                        .suggests { context, builder ->
                            provideOnlinePlayerSuggestions(context.source, builder)
                        }
                        .executes { context ->
                            execute(
                                context.source,
                                StringArgumentType.getString(context, "playerName")
                            )
                        }
                )
                .executes { context ->
                    executeSelf(context.source)
                }
        )
    }

    private fun provideOnlinePlayerSuggestions(
        source: ServerCommandSource,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase(Locale.ROOT)

        // 获取服务器实例
        val server = source.server

        server.playerManager.playerList
            .filter { !MineAuth.isPlayerRegistered(it.uuid) } // 只建议未注册的玩家
            .map { it.name.string }
            .filter { it.lowercase(Locale.ROOT).startsWith(remaining) }
            .sorted()
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }

    private fun executeSelf(source: ServerCommandSource): Int {
        val player = source.player
        if (player == null) {
            CommandUtils.sendErr(source, "mineauth.only_player_command")
            return 0
        }

        return execute(source, player.name.string)
    }

    private fun execute(source: ServerCommandSource, playerName: String): Int {
        // 检查功能是否启用
        if (!MineAuthConfig.config.enablePassCommand) {
            CommandUtils.sendErr(source, "mineauth.pass_command_disabled")
            return 0
        }

        // 查找玩家
        val targetPlayer = source.server.playerManager.getPlayer(playerName)
        if (targetPlayer == null) {
            CommandUtils.sendErr(source, "mineauth.player_not_found", playerName)
            return 0
        }

        val uuid = targetPlayer.uuid

        // 检查玩家是否已注册
        if (MineAuth.isPlayerRegistered(uuid)) {
            CommandUtils.sendErr(source, "mineauth.already_registered")
            return 0
        }

        // 创建永久会话
        val session = PlayerSession(
            uuid = uuid,
            playerName = playerName,
            ipAddress = MineAuth.getPlayerIpAddress(targetPlayer),
            loggedIn = true,
            lastLoginTime = System.currentTimeMillis(),
            failedAttempts = 0,
            lastFailedTime = 0,
            isKicked = false,
            isPermanent = true  // 标记为永久登录
        )

        // 保存到MineAuth的会话列表
        MineAuth.getPlayerSessionsMap()[uuid] = session
        MineAuth.saveSessions()

        // 如果玩家在线，发送成功消息
        targetPlayer.sendMessage(LanguageManager.tr("mineauth.pass_success"), false)

        CommandUtils.sendSuc(source, "mineauth.pass_command_success", playerName)

        // 记录日志
        val operator = source.player?.name?.string ?: "Console"
        MineAuth.logger.info(LanguageManager.tr("mineauth.pass_command_used", operator, playerName, uuid).string)

        return 1
    }
}