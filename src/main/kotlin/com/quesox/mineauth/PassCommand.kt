// PassCommand.kt (国际化版)
package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.quesox.mineauth.CommandUtils.permissionLevelFromInt
import net.minecraft.command.permission.Permission

import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting

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
                        .suggests { _, builder ->
                            providePlayerNameSuggestions(builder)
                        }
                        .executes { context ->
                            execute(
                                context.source,
                                StringArgumentType.getString(context, "playerName")
                            )
                        }
                )
        )
    }


    private fun providePlayerNameSuggestions(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase()

        // 获取所有玩家名称并过滤
        MineAuth.getAllPlayerNames()
            .filter { it.lowercase().startsWith(remaining) }
            .sorted()
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }

    private fun execute(source: ServerCommandSource, playerName: String): Int {
        // 检查玩家是否存在
        if (!MineAuth.isPlayerRegisteredByName(playerName)) {
            // 从 usercache 检查
            val uuidFromUsercache = UsercacheUtil.getUuidByName(playerName)
            if (uuidFromUsercache == null) {
                sendError(source, LanguageManager.tr("mineauth.player_not_found", playerName))
                return 0
            }
        }

        // 执行重置
        val success = MineAuth.forceResetPlayerByName(playerName)

        if (success) {
            sendSuccess(source, LanguageManager.tr("mineauth.pass_reset_success", playerName))
            sendSuccess(source, LanguageManager.tr("mineauth.player_joined_unregistered"))
        } else {
            sendInfo(source, LanguageManager.tr("mineauth.pass_no_reset_needed", playerName))
        }

        // 记录日志（使用翻译）
        val operator = source.player?.name?.string ?: "Console"
        val logMessage = "Operator $operator executed /pass $playerName, success: $success"
        MineAuth.logger.info(logMessage)

        return if (success) 1 else 0
    }

    private fun sendSuccess(source: ServerCommandSource, message: Text) {
        source.sendMessage(message.copy().styled { it.withColor(Formatting.GREEN) })
    }

    private fun sendError(source: ServerCommandSource, message: Text) {
        source.sendMessage(message.copy().styled { it.withColor(Formatting.RED) })
    }

    private fun sendInfo(source: ServerCommandSource, message: Text) {
        source.sendMessage(message.copy().styled { it.withColor(Formatting.YELLOW) })
    }
}