// PassCommand.kt (修改)
// PassCommand.kt (简化版)
package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.permission.Permission
import net.minecraft.command.permission.PermissionLevel
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

import java.util.concurrent.CompletableFuture

object PassCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("pass")
                .requires { source -> source.permissions.hasPermission(Permission.Level(PermissionLevel.GAMEMASTERS)) }
                .then(
                    argument("playerName", StringArgumentType.word())
                        .suggests { context, builder ->
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
        val result = StringBuilder()

        // 检查玩家是否存在
        if (!MineAuth.isPlayerRegisteredByName(playerName)) {
            // 从 usercache 检查
            val uuidFromUsercache = UsercacheUtil.getUuidByName(playerName)
            if (uuidFromUsercache == null) {
                result.append("§c未找到玩家: $playerName\n")
                result.append("§e提示: 该玩家从未进入过服务器或名称输入错误")
                source.sendMessage(Text.literal(result.toString()))
                return 0
            }
        }

        // 执行重置
        val success = MineAuth.forceResetPlayerByName(playerName)

        if (success) {
            result.append("§a成功重置玩家 $playerName 的状态\n")
            result.append("§7- 已清除登录冷却\n")
            result.append("§7- 已重置注册状态（需要重新注册）")
        } else {
            result.append("§e玩家 $playerName 不需要重置操作")
        }

        source.sendMessage(Text.literal(result.toString()))

        // 记录日志
        val operator = source.player?.name?.string ?: "控制台"
        MineAuth.logger.info("操作员 $operator 执行了 /pass $playerName, 成功: $success")

        return if (success) 1 else 0
    }
}