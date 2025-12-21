package com.quesox.mineauth

import kotlin.io.path.*
import java.nio.file.Path
import java.nio.file.Paths
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// 配置数据类
data class MineAuthConfigData(
    var language: String = "auto",                     // 语言设置
    var ipVerify: Boolean = false,                     // 是否启用IP验证
    var sameIPSameAccount: Boolean = false,           // 同一IP是否只能有一个账号（需要ipVerify=true）
    var sessionTime: String = "1d",                   // 会话过期时间
    var enableLoginLimit: Boolean = true,             // 是否启用登录尝试限制
    var coolDownTime: String = "5m",                  // 冷却时间
    var maxLoginAttempts: Int = 10,                   // 最大登录尝试次数
    var enablePassCommand: Boolean = false            // 是否启用/pass命令
) {
    // 检查配置是否有效
    fun isValid(): Boolean {
        // 如果sameIPsameAccount为true，但ipVerify为false，则配置无效
        if (sameIPSameAccount && !ipVerify) {
            return false
        }
        return true
    }

    // 获取当前语言
    fun getLanguage(): LanguageManager.Language {
        return LanguageManager.Language.fromCode(language)
    }

    // 将会话时间字符串转换为Duration
    fun getSessionTimeDuration(): Duration {
        return parseDuration(sessionTime, "sessionTime")
    }

    // 将冷却时间字符串转换为Duration
    fun getCoolDownTimeDuration(): Duration {
        return parseDuration(coolDownTime, "coolDownTime")
    }

    // 解析时间字符串 (10m, 10h, 10d)
    private fun parseDuration(timeStr: String, key: String): Duration {
        return try {
            val number = timeStr.filter { it.isDigit() }.toLong()
            val unit = timeStr.filter { it.isLetter() }.lowercase()

            when (unit) {
                "m" -> number.toDuration(DurationUnit.MINUTES)
                "h" -> number.toDuration(DurationUnit.HOURS)
                "d" -> number.toDuration(DurationUnit.DAYS)
                else -> {
                    // 如果单位不明确，尝试智能解析
                    when {
                        timeStr.endsWith("min", ignoreCase = true) -> {
                            val num = timeStr.filter { it.isDigit() }.toLong()
                            num.toDuration(DurationUnit.MINUTES)
                        }
                        timeStr.endsWith("hour", ignoreCase = true) -> {
                            val num = timeStr.filter { it.isDigit() }.toLong()
                            num.toDuration(DurationUnit.HOURS)
                        }
                        timeStr.endsWith("day", ignoreCase = true) -> {
                            val num = timeStr.filter { it.isDigit() }.toLong()
                            num.toDuration(DurationUnit.DAYS)
                        }
                        else -> {
                            // 默认值
                            when (key) {
                                "sessionTime" -> 1.toDuration(DurationUnit.DAYS)
                                "coolDownTime" -> 5.toDuration(DurationUnit.MINUTES)
                                else -> 1.toDuration(DurationUnit.DAYS)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            val logger = LoggerFactory.getLogger("mineauth")
            logger.error(LanguageManager.tr("mineauth.parse_time_error", timeStr).string)
            when (key) {
                "sessionTime" -> 1.toDuration(DurationUnit.DAYS)
                "coolDownTime" -> 5.toDuration(DurationUnit.MINUTES)
                else -> 1.toDuration(DurationUnit.DAYS)
            }
        }
    }
}

object MineAuthConfig {
    private val logger = LoggerFactory.getLogger("mineauth")
    private val configFile: Path = Paths.get("config/mineauth/mineauth.conf")

    // 配置实例
    var config = MineAuthConfigData()

    // 加载配置
    fun load(serverRunDirectory: Path? = null) {
        try {
            if (!configFile.exists()) {
                createDefaultConfig()
                return
            }

            val lines = configFile.readLines()
            for (line in lines) {
                val trimmedLine = line.trim()

                // 跳过空行和注释
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue
                }

                // 解析配置行，支持 = 和 = 前后有空格
                val parts = when {
                    trimmedLine.contains("=") -> {
                        val eqIndex = trimmedLine.indexOf('=')
                        val key = trimmedLine.substring(0, eqIndex).trim()
                        val value = trimmedLine.substring(eqIndex + 1).trim()
                        Pair(key, value)
                    }
                    trimmedLine.contains(" ") -> {
                        val spaceIndex = trimmedLine.indexOf(' ')
                        val key = trimmedLine.substring(0, spaceIndex).trim()
                        val value = trimmedLine.substring(spaceIndex + 1).trim()
                        Pair(key, value)
                    }
                    else -> {
                        logger.warn(LanguageManager.tr("mineauth.unparseable_config_line", trimmedLine).string)
                        continue
                    }
                }

                val key = parts.first
                val value = parts.second

                when (key.lowercase()) {
                    "language" -> {
                        config.language = value
                        logger.info(LanguageManager.tr("mineauth.language_set", value).string)
                    }
                    "ipverify" -> {
                        config.ipVerify = value.equals("true", ignoreCase = true)
                        logger.info(LanguageManager.tr(if (config.ipVerify) "mineauth.ip_verification_enabled" else "mineauth.ip_verification_disabled").string)
                    }
                    "sameipsameaccount" -> {
                        config.sameIPSameAccount = value.equals("true", ignoreCase = true)
                        logger.info(LanguageManager.tr(if (config.sameIPSameAccount) "mineauth.same_ip_account_enabled" else "mineauth.same_ip_account_disabled").string)

                        // 检查配置有效性
                        if (config.sameIPSameAccount && !config.ipVerify) {
                            logger.warn(LanguageManager.tr("mineauth.config_invalid_warning").string)
                            config.sameIPSameAccount = false
                        }
                    }
                    "sessiontime" -> {
                        config.sessionTime = value
                        logger.info(LanguageManager.tr("mineauth.session_time_set", value).string)
                    }
                    "enableloginlimit" -> {
                        config.enableLoginLimit = value.equals("true", ignoreCase = true)
                        logger.info(LanguageManager.tr(if (config.enableLoginLimit) "mineauth.login_limit_enabled" else "mineauth.login_limit_disabled").string)
                    }
                    "cooldowntime" -> {
                        config.coolDownTime = value
                        logger.info(LanguageManager.tr("mineauth.cooldown_time_set", value).string)
                    }
                    "maxloginattempts" -> {
                        config.maxLoginAttempts = value.toIntOrNull() ?: 10
                        if (config.maxLoginAttempts < 1) config.maxLoginAttempts = 10
                        logger.info(LanguageManager.tr("mineauth.max_attempts_set", config.maxLoginAttempts).string)
                    }
                    "enablepasscommand" -> {
                        config.enablePassCommand = value.equals("true", ignoreCase = true)
                        logger.info(LanguageManager.tr(if (config.enablePassCommand) "mineauth.pass_command_enabled" else "mineauth.pass_command_disabled").string)
                    }
                    else -> {
                        logger.warn(LanguageManager.tr("mineauth.unknown_config_key", key).string)
                    }
                }
            }

            // 最终配置检查
            if (!config.isValid()) {
                logger.warn(LanguageManager.tr("mineauth.config_invalid_warning").string)
                config.sameIPSameAccount = false
            }

            logger.info(LanguageManager.tr("mineauth.config_loaded").string)
        } catch (e: Exception) {
            logger.error(LanguageManager.tr("mineauth.config_load_failed", e.message ?: "unknown").string)
            createDefaultConfig()
        }
    }

    // 创建默认配置
    private fun createDefaultConfig() {
        try {
            val configDir = configFile.parent
            if (!configDir.exists()) {
                configDir.createDirectories()
            }

            val defaultConfig = """ 
                
                # ================================================

                #  MineAuth 1.0.0 Configuration File
                #                               Author: QuesoX
                #  注意: 修改配置后需要重启服务器生效
                #  Note: Server restart required after modifying configuration
                # ================================================

                
                # [语言设置 / Language Setting]
                # auto: 自动根据系统语言选择 | auto: Auto-detect based on system language
                # zh_cn: 简体中文 | en_us: English
                
                language=${config.language}
                
                
                
                # [IP验证 / IP Verification]
                # 启用后，不同IP登录同一账号时会要求重新输入密码
                # When enabled, logging in from a different IP will require re-entering the password
                
                ipVerify=${config.ipVerify}
                
                
                
                # [同一IP单账号 / One Account per IP]
                # 必须开启ipVerify才能生效
                # 开启后，同一IP注册多个账号会被禁止，并通知管理员
                # 可使用/refresh 命令重置账号状态
                # When enabled, registering multiple accounts from the same IP will be blocked and admins will be notified
                # Requires ipVerify to be enabled
                # Use /refresh command to reset account status
                
                sameIPSameAccount=${config.sameIPSameAccount}
                
                
                
                # [会话过期时间 / Session Expiration Time]
                # 支持格式: 10m (分钟), 2h (小时), 30d (天)
                # Supported formats: 10m (minutes), 2h (hours), 30d (days)
                
                sessionTime=${config.sessionTime}
                
                
                
                # [登录尝试限制 / Login Attempt Limit]
                # 如果禁用，则无限尝试次数，不会踢出玩家
                # If disabled, unlimited attempts and no player kick
                
                enableLoginLimit=${config.enableLoginLimit}
                
                
                
                # [冷却时间 / CoolDown Time]
                # 达到最大尝试次数后的等待时间
                # 支持格式: 10m (分钟), 1h (小时), 2d (天)
                # /refresh 命令可以重置冷却时间
                # Waiting time after reaching maximum attempts
                # Supported formats: 10m (minutes), 1h (hours), 2d (days)
                # /refresh command can reset cooldown
                
                coolDownTime=${config.coolDownTime}
                
                
                
                # [最大登录尝试次数 / Maximum Login Attempts]
                # 仅在 enableLoginLimit=true 时生效
                # Only effective when enableLoginLimit=true
                
                maxLoginAttempts=${config.maxLoginAttempts}
                
                
                
                # [Pass命令 / Pass Command]
                # 开启后，OP可以使用/pass命令让玩家永久登录，无需注册
                # 使用后该玩家将永久登录，不受会话时间限制
                # When enabled, OP can use /pass command to let players login permanently without registration
                # After using, the player will be permanently logged in, not affected by session time
                
                enablePassCommand=${config.enablePassCommand}
                
                
                

            
            """.trimIndent()

            configFile.writeText(defaultConfig)
            logger.info(LanguageManager.tr("mineauth.default_config_created", configFile.toAbsolutePath()).string)
        } catch (e: Exception) {
            logger.error(LanguageManager.tr("mineauth.config_create_failed", e.message ?: "unknown").string)
        }
    }

    // 检查会话是否过期
    fun isSessionExpired(lastLoginTime: Long): Boolean {
        val sessionTimeDuration = config.getSessionTimeDuration()
        if (sessionTimeDuration == Duration.ZERO) {
            return false // 0表示永不过期
        }

        val now = System.currentTimeMillis()
        val expireMillis = sessionTimeDuration.inWholeMilliseconds
        return (now - lastLoginTime) > expireMillis
    }
}