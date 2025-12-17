// MineAuthLanguageManager.kt
package com.quesox.mineauth

import com.google.gson.JsonParser
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

object MineAuthLanguageManager {
    // 支持的语种
    enum class Language(val code: String) {
        AUTO("auto"),
        EN_US("en_us"),
        ZH_CN("zh_cn");

        companion object {
            fun fromCode(code: String): Language {
                return entries.find { it.code.equals(code, ignoreCase = true) } ?: AUTO
            }
        }
    }

    private var currentLanguage = Language.AUTO
    private val translations = mutableMapOf<String, String>()
    private var fallbackTranslations = mutableMapOf<String, String>()
    private var serverRunDirectory: Path? = null

    fun initialize(serverRunDirectory: Path) {
        this.serverRunDirectory = serverRunDirectory

        // 从配置文件读取语言设置
        loadLanguageSetting()

        // 加载翻译文件
        loadTranslations()

        // 如果配置为auto，根据系统语言自动选择
        if (currentLanguage == Language.AUTO) {
            val systemLang = Locale.getDefault().language
            currentLanguage = if (systemLang.startsWith("zh")) Language.ZH_CN else Language.EN_US
        }

        // 重新加载所选语言的翻译
        loadTranslations()
    }

    private fun loadLanguageSetting() {
        val configFile = serverRunDirectory?.resolve("config/mineauth/mineauth.conf") ?: return

        if (Files.exists(configFile)) {
            try {
                Files.readAllLines(configFile).forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        // 支持两种格式: key=value 或 key value
                        if (trimmed.contains("=")) {
                            val eqIndex = trimmed.indexOf('=')
                            val key = trimmed.substring(0, eqIndex).trim()
                            val value = trimmed.substring(eqIndex + 1).trim()
                            if (key.equals("language", ignoreCase = true)) {
                                currentLanguage = Language.fromCode(value)
                            }
                        } else if (trimmed.contains(" ")) {
                            val spaceIndex = trimmed.indexOf(' ')
                            val key = trimmed.substring(0, spaceIndex).trim()
                            val value = trimmed.substring(spaceIndex + 1).trim()
                            if (key.equals("language", ignoreCase = true)) {
                                currentLanguage = Language.fromCode(value)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // 使用默认语言
                currentLanguage = Language.AUTO
            }
        }
    }

    private fun loadTranslations() {
        translations.clear()

        // 首先加载回退语言（英文）
        loadTranslationFile(Language.EN_US)
        fallbackTranslations = HashMap(translations)

        // 如果当前语言不是英文，加载当前语言的翻译
        if (currentLanguage != Language.EN_US) {
            translations.clear()
            loadTranslationFile(currentLanguage)
        }
    }

    private fun loadTranslationFile(language: Language) {
        try {
            val resourcePath = "/assets/mineauth/lang/${language.code}.json"
            var inputStream: InputStream? = null

            // 首先尝试从类路径加载（JAR内部）
            inputStream = javaClass.getResourceAsStream(resourcePath)

            if (inputStream == null) {
                // 如果从类路径加载失败，尝试从文件系统加载（开发环境）
                if (serverRunDirectory != null) {
                    val externalFile = serverRunDirectory!!.resolve("config/mineauth/lang/${language.code}.json")
                    if (Files.exists(externalFile)) {
                        inputStream = Files.newInputStream(externalFile)
                    }
                }
            }

            if (inputStream == null) {
                // 使用硬编码翻译
                loadHardcodedTranslations(language)
                return
            }

            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject

            jsonObject.entrySet().forEach { entry ->
                translations[entry.key] = entry.value.asString
            }
            inputStream.close()
        } catch (_: Exception) {
            loadHardcodedTranslations(language)
        }
    }

    private fun loadHardcodedTranslations(language: Language) {
        translations.clear()

        when (language) {
            Language.EN_US -> {
                translations.putAll(mapOf(
                    "mineauth.mod_initialized" to "MineAuth loaded",
                    "mineauth.player_joined_unregistered" to "Welcome! Please use §6/register <password> <confirm> §eto register",
                    "mineauth.player_joined_registered" to "Please use §7/login <password> §eto login",
                    "mineauth.auto_login_success" to "Welcome back! Auto-logged in.",
                    "mineauth.register_success" to "§aRegistration successful! Auto-logged in.",
                    "mineauth.register_failed" to "§cRegistration failed!",
                    "mineauth.already_registered" to "§cYou are already registered, please use /login!",
                    "mineauth.already_logged_in" to "§aYou are already logged in!",
                    "mineauth.password_too_short" to "§cPassword must be at least 6 characters!",
                    "mineauth.password_mismatch" to "§cPasswords do not match!",
                    "mineauth.only_player_command" to "Only players can execute this command!",
                    "mineauth.login_success" to "§aLogin successful!",
                    "mineauth.login_failed" to "§cLogin failed!",
                    "mineauth.not_registered" to "§cYou are not registered, please use /register or /reg!",
                    "mineauth.password_incorrect" to "§cIncorrect password!",
                    "mineauth.remaining_attempts" to "§cIncorrect password! {0} attempts remaining",
                    "mineauth.too_many_attempts" to "§cToo many login attempts, please wait {0}m{1}s and try again!",
                    "mineauth.logout_success" to "§aLogout successful! Rejoin server to log in again.",
                    "mineauth.logout_failed" to "§cLogout failed!",
                    "mineauth.not_logged_in" to "§cYou are not logged in!",
                    "mineauth.change_password_success" to "§aPassword changed successfully!",
                    "mineauth.change_password_failed" to "§cFailed to change password!",
                    "mineauth.old_password_incorrect" to "§cOld password is incorrect!",
                    "mineauth.password_same" to "§cNew password cannot be the same as old password!",
                    "mineauth.new_password_too_short" to "§cNew password must be at least 6 characters!",
                    "mineauth.pass_reset_success" to "§aSuccessfully reset player {0}\n§7- Cleared login cooldown\n§7- Reset registration status (needs re-register)",
                    "mineauth.pass_no_reset_needed" to "§ePlayer {0} does not need reset operation",
                    "mineauth.player_not_found" to "§cPlayer not found: {0}\n§eHint: Player never joined server or name incorrect",
                    "mineauth.ip_already_registered" to "§cThis IP address is already registered!\n§eRegistered account: §7{0}\n§ePlease use the original account to login",
                    "mineauth.move_blocked" to "§cPlease login before moving!",
                    "mineauth.chat_blocked" to "§cPlease login before sending chat messages!",
                    "mineauth.command_blocked" to "§cPlease login before executing commands!",
                    "mineauth.block_break_blocked" to "§cPlease login before breaking blocks!",
                    "mineauth.block_place_blocked" to "§cPlease login before placing blocks!",
                    "mineauth.item_use_blocked" to "§cPlease login before using items!",
                    "mineauth.entity_interact_blocked" to "§cPlease login before interacting with entities!",
                    "mineauth.entity_attack_blocked" to "§cPlease login before attacking entities!"
                ))
            }
            Language.ZH_CN -> {
                translations.putAll(mapOf(
                    "mineauth.mod_initialized" to "MineAuth 模组已加载",
                    "mineauth.player_joined_unregistered" to "§e欢迎！请使用 §6/register <密码> <确认密码> §e注册账户",
                    "mineauth.player_joined_registered" to "§e请使用 §7/login <密码> §e登录账户",
                    "mineauth.auto_login_success" to "§a欢迎回来！已自动登录。",
                    "mineauth.register_success" to "§a注册成功！已自动登录。",
                    "mineauth.register_failed" to "§c注册失败！",
                    "mineauth.already_registered" to "§c您已经注册过了，请使用 /login 登录！",
                    "mineauth.already_logged_in" to "§a您已经登录了！",
                    "mineauth.password_too_short" to "§c密码长度至少为6位！",
                    "mineauth.password_mismatch" to "§c两次输入的密码不一致！",
                    "mineauth.only_player_command" to "只有玩家可以执行此命令！",
                    "mineauth.login_success" to "§a登录成功！",
                    "mineauth.login_failed" to "§c登录失败！",
                    "mineauth.not_registered" to "§c您还没有注册，请使用 /register 或者 /reg 注册账户！",
                    "mineauth.password_incorrect" to "§c密码错误！",
                    "mineauth.remaining_attempts" to "§c密码错误！还剩 {0} 次尝试机会",
                    "mineauth.too_many_attempts" to "§c登录失败次数过多，请等待 {0}分{1}秒后再试！",
                    "mineauth.logout_success" to "§a登出成功！重新加入服务器后需要重新登录。",
                    "mineauth.logout_failed" to "§c登出失败！",
                    "mineauth.not_logged_in" to "§c您还没有登录！",
                    "mineauth.change_password_success" to "§a密码修改成功！",
                    "mineauth.change_password_failed" to "§c密码修改失败！",
                    "mineauth.old_password_incorrect" to "§c原密码错误！",
                    "mineauth.password_same" to "§c新密码不能与旧密码相同！",
                    "mineauth.new_password_too_short" to "§c新密码长度至少为6位！",
                    "mineauth.pass_reset_success" to "§a成功重置玩家 {0} 的状态\n§7- 已清除登录冷却\n§7- 已重置注册状态（需要重新注册）",
                    "mineauth.pass_no_reset_needed" to "§e玩家 {0} 不需要重置操作",
                    "mineauth.player_not_found" to "§c未找到玩家: {0}\n§e提示: 该玩家从未进入过服务器或名称输入错误",
                    "mineauth.ip_already_registered" to "§c该IP地址已经注册过账号！\n§e已注册账号: §7{0}\n§e请使用原账号登录",
                    "mineauth.move_blocked" to "§c请先登录后再移动！",
                    "mineauth.chat_blocked" to "§c请先登录后再发送聊天消息！",
                    "mineauth.command_blocked" to "§c请先登录后再执行命令！",
                    "mineauth.block_break_blocked" to "§c请先登录后再破坏方块！",
                    "mineauth.block_place_blocked" to "§c请先登录后再放置方块！",
                    "mineauth.item_use_blocked" to "§c请先登录后再使用物品！",
                    "mineauth.entity_interact_blocked" to "§c请先登录后再与实体交互！",
                    "mineauth.entity_attack_blocked" to "§c请先登录后再攻击实体！"
                ))
            }
            else -> {
                loadHardcodedTranslations(Language.EN_US)
            }
        }
    }

    fun getCurrentLanguage(): Language {
        return currentLanguage
    }

    // 获取翻译文本（支持占位符）
    fun translate(key: String, vararg args: Any): MutableText {
        var translation = translations[key] ?: fallbackTranslations[key] ?: key

        // 替换占位符 {0}, {1}, {2}...
        if (args.isNotEmpty()) {
            for (i in args.indices) {
                translation = translation.replace("{$i}", args[i].toString())
            }
        }

        // 同时支持 %1$s, %2$s 格式
        if (args.isNotEmpty()) {
            try {
                for (i in args.indices) {
                    translation = translation.replace("%${i + 1}\$s", args[i].toString())
                    translation = translation.replace("%${i + 1}\$d", args[i].toString())
                }
            } catch (_: Exception) {
                // 如果替换失败，返回原始翻译
            }
        }

        return Text.literal(translation)
    }

    // 简化的翻译方法
    fun tr(key: String, vararg args: Any): MutableText {
        return translate(key, *args)
    }

    // 获取原始翻译字符串（不转换为Text）
    fun trString(key: String, vararg args: Any): String {
        var translation = translations[key] ?: fallbackTranslations[key] ?: key

        if (args.isNotEmpty()) {
            for (i in args.indices) {
                translation = translation.replace("{$i}", args[i].toString())
            }
        }

        return translation
    }
}