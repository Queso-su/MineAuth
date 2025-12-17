// ChangePasswordCommand.kt
package com.quesox.mineauth

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.server.command.CommandManager.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object ChangePasswordCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("changepassword")
                .then(
                    argument("oldPassword", StringArgumentType.word())
                        .then(
                            argument("newPassword", StringArgumentType.word())
                                .executes { context ->
                                    execute(
                                        context.source,
                                        StringArgumentType.getString(context, "oldPassword"),
                                        StringArgumentType.getString(context, "newPassword")
                                    )
                                }
                        )
                )
        )

        // 添加别名 /cpwd
        dispatcher.register(
            literal("cpwd")
                .then(
                    argument("oldPassword", StringArgumentType.word())
                        .then(
                            argument("newPassword", StringArgumentType.word())
                                .executes { context ->
                                    execute(
                                        context.source,
                                        StringArgumentType.getString(context, "oldPassword"),
                                        StringArgumentType.getString(context, "newPassword")
                                    )
                                }
                        )
                )
        )
    }

    private fun execute(source: ServerCommandSource, oldPassword: String, newPassword: String): Int {
        val player = source.player
        if (player == null) {
            source.sendError(Text.literal("只有玩家可以执行此命令！"))
            return 0
        }

        val uuid = player.uuid

        // 检查玩家是否已登录
        if (!MineAuth.isPlayerLoggedIn(uuid)) {
            source.sendMessage(Text.literal("§c请先登录后再修改密码！").styled { it.withColor(Formatting.RED) })
            return 0
        }

        // 检查新密码长度
        if (newPassword.length < 6) {
            source.sendMessage(Text.literal("§c新密码长度至少为6位！").styled { it.withColor(Formatting.RED) })
            return 0
        }

        // 检查新旧密码是否相同
        if (oldPassword == newPassword) {
            source.sendMessage(Text.literal("§c新密码不能与旧密码相同！").styled { it.withColor(Formatting.RED) })
            return 0
        }

        // 修改密码
        if (MineAuth.changePassword(uuid, oldPassword, newPassword)) {
            source.sendMessage(Text.literal("§a密码修改成功！").styled { it.withColor(Formatting.GREEN) })

            // 密码修改成功后，需要重新登录（安全考虑）
            //MineAuth.logoutPlayer(uuid)
            //player.sendMessage(Text.literal("§e出于安全考虑，请重新登录"), false)

            return 1
        } else {
            source.sendMessage(Text.literal("§c原密码错误！").styled { it.withColor(Formatting.RED) })
            return 0
        }
    }
}