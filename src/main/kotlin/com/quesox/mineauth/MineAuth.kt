package com.quesox.mineauth

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.command.permission.Permission
import net.minecraft.command.permission.PermissionLevel
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.*

object MineAuth : ModInitializer {
	val logger = LoggerFactory.getLogger("mineauth")
	private val playerDataFile: Path = Paths.get("config/mineauth/players.json")
	private val sessionFile: Path = Paths.get("config/mineauth/sessions.json")

	// 服务器实例
	private var minecraftServer: net.minecraft.server.MinecraftServer? = null

	// UUID -> 玩家数据
	data class PlayerData(val name: String, val password: String, val ipAddress: String)
	private val playerDataMap = mutableMapOf<UUID, PlayerData>()

	// UUID -> PlayerSession
	private val playerSessions = mutableMapOf<UUID, PlayerSession>()

	// 名字 -> UUID 映射
	private val nameToUuidMap = mutableMapOf<String, UUID>()

	// IP -> UUID 映射（用于 sameIPsameAccount 功能）
	private val ipToUuidMap = mutableMapOf<String, UUID>()

	override fun onInitialize() {
		logger.info(LanguageManager.tr("mineauth.mod_initialized").string)

		// 监听服务器启动
		ServerLifecycleEvents.SERVER_STARTING.register { server ->
			minecraftServer = server
			LanguageManager.initialize(server.runDirectory)

			MineAuthConfig.load(server.runDirectory)

			// 设置语言管理器语言
			val language = MineAuthConfig.config.getLanguage()
			if (language != LanguageManager.Language.AUTO) {
				// 更新语言管理器
				LanguageManager.updateLanguage(language, server.runDirectory)
			}

			// 创建配置文件目录
			val configDir = File("config/mineauth")
			if (!configDir.exists()) {
				configDir.mkdirs()
			}

			// 加载玩家数据
			loadPlayerData()
			loadSessions()

		}

		// 监听服务器停止
		ServerLifecycleEvents.SERVER_STOPPING.register {
			savePlayerData()
			saveSessions()
			minecraftServer = null
			logger.info(LanguageManager.tr("mineauth.data_saved").string)
		}

		// 注册命令
		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			RegisterCommand.register(dispatcher)
			LoginCommand.register(dispatcher)
			LogoutCommand.register(dispatcher)
			ChangePasswordCommand.register(dispatcher)
			PassCommand.register(dispatcher)
		}

		// 监听玩家加入服务器
		ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
			val player = handler.player
			val uuid = player.uuid
			val playerName = player.name.string
			val ipAddress = getSimplePlayerIpAddress(player)

			// 检查同一IP是否已有账号（sameIPsameAccount 功能）
			if (MineAuthConfig.config.ipVerify && MineAuthConfig.config.sameIPSameAccount) {
				val existingUuid = ipToUuidMap[ipAddress]
				if (existingUuid != null && existingUuid != uuid) {
					// 找到同一IP下的其他账号
					val existingPlayerData = playerDataMap[existingUuid]
					if (existingPlayerData != null) {
						// 提示玩家
						player.sendMessage(LanguageManager.tr("mineauth.ip_already_registered", existingPlayerData.name), false)
						player.sendMessage(Text.literal("§e原账号: §7${existingPlayerData.name}"), false)
						player.sendMessage(Text.literal("§e请使用原账号登录，或联系管理员"), false)

						// 通知在线管理员
						notifyAdminsAboutAccountSwitch(
							existingPlayerData.name,
							playerName,
							ipAddress,
							LanguageManager.tr("mineauth.ip_switch_attempt").string
						)

						return@register
					}
				}
			}

			// 检查会话是否有效
			val session = playerSessions[uuid]
			val checkIp = MineAuthConfig.config.ipVerify

			// 检查玩家是否被锁定
			if (session != null && session.isLocked()) {
				val remainingTime = session.getCoolDownRemainingTime()
				val minutes = remainingTime / 60
				val seconds = remainingTime % 60

				val kickMessage = if (minutes > 0) {
					LanguageManager.tr("mineauth.too_many_attempts", minutes, seconds)
				} else {
					LanguageManager.tr("mineauth.too_many_attempts", 0, seconds)
				}

				player.networkHandler.disconnect(kickMessage)
				logger.info(LanguageManager.tr("mineauth.kicked_too_many_attempts", minutes, seconds).string)
				return@register
			}

			// 检查是否可以解除锁定
			session?.checkAndUnlock()

			// 检查是否自动登录
			if (session != null && session.isValid(ipAddress, checkIp)) {
				session.loggedIn = true
				session.lastLoginTime = System.currentTimeMillis()
				session.resetFailedAttempts()
				player.sendMessage(LanguageManager.tr("mineauth.auto_login_success"), true)
				logger.info(LanguageManager.tr("mineauth.auto_logged_in").string)
			} else {
				// 需要登录或注册
				if (isPlayerRegistered(uuid)) {
					player.sendMessage(LanguageManager.tr("mineauth.player_joined_registered"), false)
					player.sendMessage(LanguageManager.tr("mineauth.player_joined_registered"), false)

				} else {
					player.sendMessage(LanguageManager.tr("mineauth.player_joined_unregistered"), false)
					player.sendMessage(LanguageManager.tr("mineauth.not_registered"), true)

				}

				// 更新会话
				playerSessions[uuid] = PlayerSession(uuid, playerName, ipAddress, false)
				saveSessions()
			}
		}

		logger.info(LanguageManager.tr("mineauth.initialization_complete").string)
	}

	// 简化IP地址获取
	private fun getSimplePlayerIpAddress(player: net.minecraft.server.network.ServerPlayerEntity): String {
		return try {
			val address = player.networkHandler.connectionAddress
			address.toString().removePrefix("/").split(":").first()
		} catch (e: Exception) {
			"unknown"
		}
	}

	// 获取玩家IP地址（公共方法）
	fun getPlayerIpAddress(player: net.minecraft.server.network.ServerPlayerEntity): String {
		return getSimplePlayerIpAddress(player)
	}

	// 加载玩家数据
	private fun loadPlayerData() {
		try {
			playerDataMap.clear()
			nameToUuidMap.clear()
			ipToUuidMap.clear()

			if (playerDataFile.exists()) {
				val json = playerDataFile.readText()
				if (json.isNotEmpty()) {
					json.lineSequence()
						.filter { it.isNotBlank() }
						.forEach { line ->
							val parts = line.split(":", limit = 4)
							when {
								parts.size >= 4 -> {
									val uuid = UUID.fromString(parts[0])
									val name = parts[1]
									val password = parts[2]
									val ipAddress = parts[3]
									playerDataMap[uuid] = PlayerData(name, password, ipAddress)
									nameToUuidMap[name] = uuid
									if (ipAddress != "unknown" && ipAddress.isNotBlank()) {
										ipToUuidMap[ipAddress] = uuid
									}
								}
								parts.size >= 3 -> {
									// 旧格式兼容：没有IP地址
									val uuid = UUID.fromString(parts[0])
									val name = parts[1]
									val password = parts[2]
									playerDataMap[uuid] = PlayerData(name, password, "unknown")
									nameToUuidMap[name] = uuid
								}
							}
						}
				}
			}
			logger.info(LanguageManager.tr("mineauth.player_data_loaded", playerDataMap.size).string)
		} catch (e: Exception) {
			logger.error(LanguageManager.tr("mineauth.player_data_load_failed", e.message ?: "unknown").string)
		}
	}

	// 加载会话数据
	private fun loadSessions() {
		try {
			if (sessionFile.exists()) {
				val json = sessionFile.readText()
				if (json.isNotEmpty()) {
					json.lineSequence()
						.filter { it.isNotBlank() }
						.forEach { line ->
							val parts = line.split(":", limit = 8)
							if (parts.size >= 8) {
								val uuid = UUID.fromString(parts[0])
								val playerName = parts[1]
								val ipAddress = parts[2]
								val loggedIn = parts[3].toBoolean()
								val lastLoginTime = parts[4].toLongOrNull() ?: 0L
								val failedAttempts = parts[5].toIntOrNull() ?: 0
								val lastFailedTime = parts[6].toLongOrNull() ?: 0L
								val isKicked = parts[7].toBoolean()

								playerSessions[uuid] = PlayerSession(
									uuid, playerName, ipAddress, loggedIn, lastLoginTime,
									failedAttempts, lastFailedTime, isKicked
								)
							}
						}
				}
			}
			logger.info(LanguageManager.tr("mineauth.session_data_loaded", playerSessions.size).string)
		} catch (e: Exception) {
			logger.error(LanguageManager.tr("mineauth.session_data_load_failed", e.message ?: "unknown").string)
		}
	}

	// 保存玩家数据
	private fun savePlayerData() {
		try {
			val data = playerDataMap.map { (uuid, playerData) ->
				"$uuid:${playerData.name}:${playerData.password}:${playerData.ipAddress}"
			}.joinToString("\n")

			playerDataFile.writeText(data)
		} catch (e: Exception) {
			logger.error(LanguageManager.tr("mineauth.player_data_save_failed", e.message ?: "unknown").string)
		}
	}

	// 保存会话数据
	fun saveSessions() {
		try {
			val data = playerSessions.values.joinToString("\n") { session ->
				"${session.uuid}:${session.playerName}:${session.ipAddress}:" +
						"${session.loggedIn}:${session.lastLoginTime}:" +
						"${session.failedAttempts}:${session.lastFailedTime}:${session.isKicked}"
			}

			sessionFile.writeText(data)
		} catch (e: Exception) {
			logger.error(LanguageManager.tr("mineauth.session_data_save_failed", e.message ?: "unknown").string)
		}
	}

	// 通知管理员关于账号切换
	private fun notifyAdminsAboutAccountSwitch(
		originalAccount: String,
		newAccount: String,
		ipAddress: String,
		reason: String
	) {
		try {
			val server = minecraftServer ?: return

			// 使用翻译构建管理员消息
			val adminMessage = LanguageManager.tr(
				"mineauth.ip_account_switch_warn",
				ipAddress,
				originalAccount,
				newAccount,
				reason
			)

			// 发送给所有在线的管理员（权限等级2以上）
			for (player in server.playerManager.playerList) {
				if (player.permissions.hasPermission(Permission.Level(PermissionLevel.GAMEMASTERS))) {
					player.sendMessage(adminMessage)
				}
			}

			// 记录到日志（使用翻译）
			logger.warn(LanguageManager.tr("mineauth.ip_multiple_account_warn", ipAddress, originalAccount, newAccount).string)
		} catch (e: Exception) {
			logger.error(LanguageManager.tr("mineauth.notify_admin_failed", e.message ?: "unknown").string)
		}
	}

	// 检查同一IP是否已有其他账号（用于注册时）
	fun isIpAlreadyRegistered(ipAddress: String, excludeUuid: UUID? = null): Pair<Boolean, String?> {
		if (!MineAuthConfig.config.ipVerify || !MineAuthConfig.config.sameIPSameAccount) {
			return Pair(false, null)
		}

		val existingUuid = ipToUuidMap[ipAddress]
		if (existingUuid != null && existingUuid != excludeUuid) {
			val playerData = playerDataMap[existingUuid]
			return Pair(true, playerData?.name)
		}
		return Pair(false, null)
	}

	// 注册玩家（带IP地址和玩家名字）
	fun registerPlayer(uuid: UUID, playerName: String, password: String, ipAddress: String): Boolean {
		// 检查玩家是否已注册
		if (playerDataMap.containsKey(uuid)) {
			return false
		}

		// 检查同一IP是否已有其他账号（sameIPsameAccount 功能）
		if (MineAuthConfig.config.ipVerify && MineAuthConfig.config.sameIPSameAccount) {
			val (ipRegistered, existingAccount) = isIpAlreadyRegistered(ipAddress)
			if (ipRegistered) {
				// 记录日志但不阻止，因为配置可能被修改
				logger.warn(LanguageManager.tr("mineauth.ip_multiple_account_warn", ipAddress, existingAccount ?: "unknown", playerName).string)
				// 通知管理员
				notifyAdminsAboutAccountSwitch(
					existingAccount ?: LanguageManager.tr("mineauth.unknown").string,
					playerName,
					ipAddress,
					LanguageManager.tr("mineauth.register_new_account").string
				)
			}
		}

		// 使用SHA-256加密密码
		val hashedPassword = hashPassword(password)
		playerDataMap[uuid] = PlayerData(playerName, hashedPassword, ipAddress)
		nameToUuidMap[playerName] = uuid
		if (ipAddress != "unknown" && ipAddress.isNotBlank()) {
			ipToUuidMap[ipAddress] = uuid
		}

		// 创建会话并自动登录
		playerSessions[uuid] = PlayerSession(uuid, playerName, ipAddress, true)

		savePlayerData()
		saveSessions()

		// 记录注册信息（使用翻译）
		logger.info(LanguageManager.tr("mineauth.player_registered", playerName, uuid, ipAddress).string)
		return true
	}

	// 玩家登录（带IP地址和玩家名字）
	fun loginPlayer(uuid: UUID, playerName: String, ipAddress: String): Boolean {
		val session = playerSessions[uuid] ?: PlayerSession(uuid, playerName, ipAddress, true)
		session.loggedIn = true
		session.lastLoginTime = System.currentTimeMillis()
		session.resetFailedAttempts()
		session.ipAddress = ipAddress // 更新IP地址

		// 检查同一IP是否登录了其他账号（sameIPsameAccount 功能）
		if (MineAuthConfig.config.ipVerify && MineAuthConfig.config.sameIPSameAccount) {
			val (ipRegistered, existingAccount) = isIpAlreadyRegistered(ipAddress, uuid)
			if (ipRegistered && existingAccount != playerName) {
				// 通知管理员
				notifyAdminsAboutAccountSwitch(
					existingAccount ?: LanguageManager.tr("mineauth.unknown").string,
					playerName,
					ipAddress,
					LanguageManager.tr("mineauth.login_different_account").string
				)
			}
		}

		playerSessions[uuid] = session
		saveSessions()

		logger.info(LanguageManager.tr("mineauth.player_logged_in", playerName, uuid, ipAddress).string)
		return true
	}

	// 获取所有玩家名称（从多个来源）
	fun getAllPlayerNames(): Set<String> {
		val playerNames = mutableSetOf<String>()

		// 从 usercache.json
		playerNames.addAll(UsercacheUtil.getAllPlayerNamesFromUsercache())

		// 从已注册玩家
		playerNames.addAll(nameToUuidMap.keys)

		return playerNames
	}

	// 重置玩家注册状态（删除密码）
	fun resetPlayerRegistration(uuid: UUID): Boolean {
		val playerData = playerDataMap.remove(uuid)
		if (playerData != null) {
			nameToUuidMap.remove(playerData.name)
			ipToUuidMap.remove(playerData.ipAddress)
			savePlayerData()
		}

		val sessionRemoved = playerSessions.remove(uuid) != null
		if (sessionRemoved) saveSessions()

		return playerData != null || sessionRemoved
	}

	// ===== 原有方法保持不变 =====
	@JvmStatic
	fun isPlayerLoggedIn(uuid: UUID): Boolean {
		val session = playerSessions[uuid] ?: return false
		return session.loggedIn && !MineAuthConfig.isSessionExpired(session.lastLoginTime)
	}

	fun isValidSession(uuid: UUID, ipAddress: String): Boolean {
		val session = playerSessions[uuid] ?: return false
		return session.isValid(ipAddress, MineAuthConfig.config.ipVerify)
	}

	fun logoutPlayer(uuid: UUID): Boolean {
		playerSessions[uuid]?.let {
			it.loggedIn = false
			saveSessions()
			return true
		}
		return false
	}

	fun changePassword(uuid: UUID, oldPassword: String, newPassword: String): Boolean {
		if (!verifyPassword(uuid, oldPassword)) return false

		playerDataMap[uuid]?.let { playerData ->
			val hashedPassword = hashPassword(newPassword)
			playerDataMap[uuid] = PlayerData(playerData.name, hashedPassword, playerData.ipAddress)
			savePlayerData()
			return true
		}
		return false
	}

	fun verifyPassword(uuid: UUID, password: String): Boolean {
		val playerData = playerDataMap[uuid] ?: return false
		return playerData.password == hashPassword(password)
	}

	fun isPlayerRegistered(uuid: UUID): Boolean = playerDataMap.containsKey(uuid)

	fun isPlayerRegisteredByName(playerName: String): Boolean = nameToUuidMap.containsKey(playerName)

	fun getPlayerSession(uuid: UUID): PlayerSession? = playerSessions[uuid]

	fun resetPlayerCooldown(uuid: UUID): Boolean {
		val session = playerSessions[uuid]
		return if (session != null) {
			session.resetFailedAttempts()
			saveSessions()
			true
		} else {
			false
		}
	}

	fun forceResetPlayerByName(playerName: String): Boolean {
		val uuid = findUuidByName(playerName) ?: return false
		var success = false

		if (resetPlayerCooldown(uuid)) success = true
		if (resetPlayerRegistration(uuid)) success = true

		return success
	}

	fun findUuidByName(playerName: String): UUID? {
		// 1. 从已注册玩家查找
		nameToUuidMap[playerName]?.let { return it }

		// 2. 大小写不敏感匹配
		nameToUuidMap.entries.find { it.key.equals(playerName, ignoreCase = true) }
			?.let { return it.value }

		// 3. 从 usercache.json 查找
		return UsercacheUtil.getUuidByName(playerName)
	}

	// 密码哈希函数
	private fun hashPassword(password: String): String {
		return try {
			val digest = MessageDigest.getInstance("SHA-256")
			val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
			hash.joinToString("") { "%02x".format(it) }
		} catch (e: Exception) {
			password // 失败时返回原始密码
		}
	}
}