// MineAuthConfig.kt (修改)
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
    var ipVerify: Boolean = false,                     // 是否启用IP验证
    var sameIPSameAccount: Boolean = false,           // 同一IP是否只能有一个账号（需要ipVerify=true）
    var sessionTime: String = "1d",                   // 会话过期时间
    var enableLoginLimit: Boolean = true,             // 是否启用登录尝试限制
    var coolDownTime: String = "5m",                  // 冷却时间
    var maxLoginAttempts: Int = 10                    // 最大登录尝试次数
) {
    // 检查配置是否有效
    fun isValid(): Boolean {
        // 如果sameIPsameAccount为true，但ipVerify为false，则配置无效
        if (sameIPSameAccount && !ipVerify) {
            return false
        }
        return true
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
            logger.error("解析时间格式错误: $timeStr，使用默认值")
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
    fun load() {
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
                        logger.warn("无法解析配置行: $trimmedLine")
                        continue
                    }
                }

                val key = parts.first
                val value = parts.second

                when (key.lowercase()) {
                    "ipverify" -> {
                        config.ipVerify = value.equals("true", ignoreCase = true)
                        logger.info("IP验证: ${if (config.ipVerify) "已启用" else "已禁用"}")
                    }
                    "sameipsameaccount" -> {
                        config.sameIPSameAccount = value.equals("true", ignoreCase = true)
                        logger.info("同一IP绑定账号: ${if (config.sameIPSameAccount) "已启用" else "已禁用"}")

                        // 检查配置有效性
                        if (config.sameIPSameAccount && !config.ipVerify) {
                            logger.warn("警告: sameIPsameAccount 需要 ipVerify 为 true 才能生效")
                            config.sameIPSameAccount = false
                        }
                    }
                    "sessiontime" -> {
                        config.sessionTime = value
                        logger.info("会话过期时间: ${config.sessionTime}")
                    }
                    "enableloginlimit" -> {
                        config.enableLoginLimit = value.equals("true", ignoreCase = true)
                        logger.info("登录尝试限制: ${if (config.enableLoginLimit) "已启用" else "已禁用"}")
                    }
                    "cooldowntime" -> {
                        config.coolDownTime = value
                        logger.info("冷却时间: ${config.coolDownTime}")
                    }
                    "maxloginattempts" -> {
                        config.maxLoginAttempts = value.toIntOrNull() ?: 10
                        if (config.maxLoginAttempts < 1) config.maxLoginAttempts = 10
                        logger.info("最大登录尝试次数: ${config.maxLoginAttempts}")
                    }
                    else -> {
                        logger.warn("未知配置项: $key")
                    }
                }
            }

            // 最终配置检查
            if (!config.isValid()) {
                logger.warn("配置无效: sameIPsameAccount 需要 ipVerify 为 true")
                config.sameIPSameAccount = false
            }

            logger.info("配置加载完成")
        } catch (e: Exception) {
            logger.error("加载配置失败: ${e.message}")
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
                # MineAuth 配置文件
                # 是否启用IP验证（true/false）
                ipVerify=${config.ipVerify}
                
                # 同一IP是否只能有一个账号（必须开启ipVerify才能生效）
                # 开启后，同一IP注册多个账号会被禁止，并通知管理员
                sameIPSameAccount=${config.sameIPSameAccount}
                
                # 会话过期时间（支持 m=分钟, h=小时, d=天）
                # 例如: 10m, 2h, 30d
                sessionTime=${config.sessionTime}
                
                # 是否启用登录尝试限制（true/false）
                # 如果禁用，则无限尝试次数，不会踢出玩家
                enableLoginLimit=${config.enableLoginLimit}
                
                # 冷却时间（达到最大尝试次数后的等待时间）
                # 支持 m=分钟, h=小时, d=天
                # 例如: 10m, 1h, 2d
                coolDownTime=${config.coolDownTime}
                
                # 最大登录尝试次数（仅在 enableLoginLimit=true 时生效）
                maxLoginAttempts=${config.maxLoginAttempts}
                
                # 注意：修改配置后需要重启服务器生效
            """.trimIndent()

            configFile.writeText(defaultConfig)
            logger.info("已创建默认配置文件: ${configFile.toAbsolutePath()}")
        } catch (e: Exception) {
            logger.error("创建配置文件失败: ${e.message}")
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