package com.quesox.mineauth

import net.minecraft.text.MutableText
import net.minecraft.text.Text
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import com.google.gson.JsonParser
import java.util.Locale

object LanguageManager {
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
        loadLanguageSetting(serverRunDirectory)

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

    // 更新语言设置
    fun updateLanguage(language: Language, serverRunDirectory: Path) {
        this.serverRunDirectory = serverRunDirectory
        currentLanguage = language
        loadTranslations()
    }

    private fun loadLanguageSetting(serverRunDirectory: Path) {
        val configFile = serverRunDirectory.resolve("config/mineauth/mineauth.conf")
        if (Files.exists(configFile)) {
            try {
                Files.readAllLines(configFile).forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.startsWith("language=")) {
                        val langCode = trimmed.substringAfter("language=").trim()
                        currentLanguage = Language.fromCode(langCode)
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
            var inputStream: InputStream?

            // 首先尝试从类路径加载（JAR内部）
            inputStream = javaClass.getResourceAsStream(resourcePath)

            if (inputStream == null) {
                // 如果从类路径加载失败，尝试从文件系统加载（开发环境）
                if (serverRunDirectory != null) {
                    val externalFile = serverRunDirectory!!.resolve("config/lang/${language.code}.json")
                    if (Files.exists(externalFile)) {
                        inputStream = Files.newInputStream(externalFile)
                    }
                }
            }

            if (inputStream == null) {
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
            Language.ZH_CN -> {
                translations.putAll(mapOf(
                    "mineauth.mod_initialized" to "MineAuth 模组已加载",
                    "mineauth.config_loaded" to "配置加载完成",
                    "mineauth.default_config_created" to "已创建默认配置文件: {0}",
                    "mineauth.config_load_failed" to "加载配置失败: {0}",
                    "mineauth.config_create_failed" to "创建配置文件失败: {0}",
                    "mineauth.player_data_loaded" to "已加载 {0} 个玩家的数据",
                    "mineauth.session_data_loaded" to "已加载 {0} 个会话数据",
                    "mineauth.player_data_save_failed" to "保存玩家数据失败: {0}",
                    "mineauth.session_data_save_failed" to "保存会话数据失败: {0}",
                    "mineauth.player_data_load_failed" to "加载玩家数据失败: {0}",
                    "mineauth.session_data_load_failed" to "加载会话数据失败: {0}",
                    "mineauth.ip_verification_enabled" to "IP验证: 已启用",
                    "mineauth.ip_verification_disabled" to "IP验证: 已禁用",
                    "mineauth.same_ip_account_enabled" to "同一IP绑定账号: 已启用",
                    "mineauth.same_ip_account_disabled" to "同一IP绑定账号: 已禁用",
                    "mineauth.login_limit_enabled" to "登录尝试限制: 已启用",
                    "mineauth.login_limit_disabled" to "登录尝试限制: 已禁用",
                    "mineauth.session_time_set" to "会话过期时间: {0}",
                    "mineauth.cooldown_time_set" to "冷却时间: {0}",
                    "mineauth.max_attempts_set" to "最大登录尝试次数: {0}",
                    "mineauth.config_invalid_warning" to "警告: sameIPsameAccount 需要 ipVerify 为 true 才能生效",
                    "mineauth.ip_multiple_account_warn" to "同一IP注册多个账号: IP={0}, 原账号={1}, 新账号={2}",
                    "mineauth.initialization_complete" to "MineAuth 模组初始化完成！",
                    "mineauth.data_saved" to "MineAuth 数据已保存",
                    "mineauth.kicked_too_many_attempts" to "因登录尝试过多被踢出，冷却时间剩余: {0}分{1}秒",
                    "mineauth.auto_logged_in" to "已自动登录",
                    "mineauth.player_registered" to "玩家 {0} (UUID: {1}, IP: {2}) 注册成功",
                    "mineauth.player_logged_in" to "玩家 {0} (UUID: {1}, IP: {2}) 登录成功",
                    "mineauth.notify_admin_failed" to "通知管理员失败: {0}",
                    "mineauth.unparseable_config_line" to "无法解析配置行: {0}",
                    "mineauth.unknown_config_key" to "未知配置项: {0}",
                    "mineauth.parse_time_error" to "解析时间格式错误: {0}，使用默认值",
                    "mineauth.language_set" to "语言已设置为: {0}",
                    "mineauth.unknown" to "未知",
                    "mineauth.register_new_account" to "同一IP注册新账号",
                    "mineauth.login_different_account" to "同一IP登录不同账号",
                    "mineauth.ip_switch_attempt" to "尝试从同一IP使用新账号",
                    "mineauth.ip_account_switch_warn" to "§6[MineAuth] 账号切换警告\n§fIP地址: §7{0}\n§f原账号: §7{1}\n§f新账号: §7{2}\n§f行为: §7{3}",
                    "mineauth.ip_already_registered" to "§c该IP地址已经注册过账号！\n§e已注册账号: §7{0}\n§e请使用原账号登录",
                    "mineauth.too_many_attempts" to "§c登录失败次数过多，请等待 {0}分{1}秒后再试！",
                    "mineauth.auto_login_success" to "§a欢迎回来！已自动登录。",
                    "mineauth.player_joined_registered" to "§e请使用 §7/login <密码> §e登录账户",
                    "mineauth.player_joined_unregistered" to "§e欢迎！请使用 §6/register <密码> <确认密码> §e注册账户"
                ))
            }
            Language.EN_US -> {
                translations.putAll(mapOf(
                    "mineauth.mod_initialized" to "MineAuth mod loaded",
                    "mineauth.config_loaded" to "Configuration loaded",
                    "mineauth.default_config_created" to "Created default config file: {0}",
                    "mineauth.config_load_failed" to "Failed to load config: {0}",
                    "mineauth.config_create_failed" to "Failed to create config file: {0}",
                    "mineauth.player_data_loaded" to "Loaded {0} player data",
                    "mineauth.session_data_loaded" to "Loaded {0} session data",
                    "mineauth.player_data_save_failed" to "Failed to save player data: {0}",
                    "mineauth.session_data_save_failed" to "Failed to save session data: {0}",
                    "mineauth.player_data_load_failed" to "Failed to load player data: {0}",
                    "mineauth.session_data_load_failed" to "Failed to load session data: {0}",
                    "mineauth.ip_verification_enabled" to "IP Verification: enabled",
                    "mineauth.ip_verification_disabled" to "IP Verification: disabled",
                    "mineauth.same_ip_account_enabled" to "Same IP One Account: enabled",
                    "mineauth.same_ip_account_disabled" to "Same IP One Account: disabled",
                    "mineauth.login_limit_enabled" to "Login Attempt Limit: enabled",
                    "mineauth.login_limit_disabled" to "Login Attempt Limit: disabled",
                    "mineauth.session_time_set" to "Session Time: {0}",
                    "mineauth.cooldown_time_set" to "Cooldown Time: {0}",
                    "mineauth.max_attempts_set" to "Max Login Attempts: {0}",
                    "mineauth.config_invalid_warning" to "Warning: sameIPsameAccount requires ipVerify to be true",
                    "mineauth.ip_multiple_account_warn" to "Multiple accounts on same IP: IP={0}, Original={1}, New={2}",
                    "mineauth.initialization_complete" to "MineAuth initialization complete!",
                    "mineauth.data_saved" to "MineAuth data saved",
                    "mineauth.kicked_too_many_attempts" to "kicked due to too many login attempts, cooldown remaining: {0}m{1}s",
                    "mineauth.auto_logged_in" to "auto logged in",
                    "mineauth.player_registered" to "Player {0} (UUID: {1}, IP: {2}) registered successfully",
                    "mineauth.player_logged_in" to "Player {0} (UUID: {1}, IP: {2}) logged in successfully",
                    "mineauth.notify_admin_failed" to "Failed to notify admin: {0}",
                    "mineauth.unparseable_config_line" to "Cannot parse config line: {0}",
                    "mineauth.unknown_config_key" to "Unknown config key: {0}",
                    "mineauth.parse_time_error" to "Error parsing time format: {0}, using default value",
                    "mineauth.language_set" to "Language set to: {0}",
                    "mineauth.unknown" to "Unknown",
                    "mineauth.register_new_account" to "Register new account on same IP",
                    "mineauth.login_different_account" to "Login different account on same IP",
                    "mineauth.ip_switch_attempt" to "Attempt to use new account from same IP",
                    "mineauth.ip_account_switch_warn" to "§6[MineAuth] Account Switch Warning\n§fIP Address: §7{0}\n§fOriginal Account: §7{1}\n§fNew Account: §7{2}\n§fAction: §7{3}",
                    "mineauth.ip_already_registered" to "§cThis IP address is already registered!\n§eRegistered account: §7{0}\n§ePlease use the original account to login",
                    "mineauth.too_many_attempts" to "§cToo many login attempts, please wait {0}m{1}s and try again!",
                    "mineauth.auto_login_success" to "§aWelcome back! Auto-logged in.",
                    "mineauth.player_joined_registered" to "§ePlease use §7/login <password> §eto login",
                    "mineauth.player_joined_unregistered" to "§eWelcome! Please use §6/register <password> <confirm> §eto register"
                ))
            }
            else -> {
                // 默认使用英文
                loadHardcodedTranslations(Language.EN_US)
            }
        }

        // 确保回退翻译总是英文
        if (language != Language.EN_US) {
            val enTranslations = mutableMapOf<String, String>()
            loadHardcodedTranslations(Language.EN_US)
            enTranslations.putAll(translations)
            fallbackTranslations = enTranslations
        } else {
            fallbackTranslations = HashMap(translations)
        }
    }

    fun getCurrentLanguage(): Language {
        return currentLanguage
    }

    // 获取翻译文本（支持占位符）
    fun translate(key: String, vararg args: Any): MutableText {
        var translation = translations[key] ?: fallbackTranslations[key] ?: key

        // 替换占位符
        if (args.isNotEmpty()) {
            try {
                for (i in args.indices) {
                    translation = translation.replace("{$i}", args[i].toString())
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
}
