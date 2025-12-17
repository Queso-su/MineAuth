package com.quesox.mineauth

import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object CommandUtils {
    // 发送成功消息
    fun sendSuc(source: ServerCommandSource, message: String, vararg args: Any) {
        val translated = LanguageManager.tr(message, *args)
        source.sendMessage(translated)
    }

    // 发送错误消息
    fun sendErr(source: ServerCommandSource, message: String, vararg args: Any) {
        val translated = LanguageManager.tr(message, *args)
        source.sendMessage(translated.formatted(Formatting.RED))
    }

    // 发送信息消息
    fun sendInf(source: ServerCommandSource, message: String, vararg args: Any) {
        val translated = LanguageManager.tr(message, *args)
        source.sendMessage(translated.formatted(Formatting.YELLOW))
    }

    // 检查是否为玩家
    fun isPlayer(source: ServerCommandSource): Boolean {
        return source.entity != null && source.entity is net.minecraft.server.network.ServerPlayerEntity
    }

    // 获取玩家（如果存在）
    fun getPlayer(source: ServerCommandSource): net.minecraft.server.network.ServerPlayerEntity? {
        return if (isPlayer(source)) source.player as net.minecraft.server.network.ServerPlayerEntity else null
    }
}