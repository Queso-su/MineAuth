// PlayerSession.kt (修改)
package com.quesox.mineauth

import java.util.*

data class PlayerSession(
    val uuid: UUID,
    val playerName: String, // 新增：玩家名字
    var ipAddress: String,
    var loggedIn: Boolean = false,
    var lastLoginTime: Long = System.currentTimeMillis(),
    var failedAttempts: Int = 0, // 登录失败次数
    var lastFailedTime: Long = 0, // 上次失败时间
    var isKicked: Boolean = false // 是否被踢出
) {
    // 检查会话是否有效（根据配置决定是否检查IP）
    fun isValid(currentIp: String, checkIp: Boolean = true): Boolean {
        if (!loggedIn) return false

        // 检查会话是否过期
        if (MineAuthConfig.isSessionExpired(lastLoginTime)) {
            return false
        }

        // 如果玩家被踢出，检查是否还在冷却期内
        if (isKicked && isInCoolDown()) {
            return false
        }

        // 根据配置决定是否检查IP
        return if (checkIp) {
            ipAddress == currentIp
        } else {
            true // 不检查IP
        }
    }

    fun resetFailedAttempts() {
        failedAttempts = 0
        lastFailedTime = 0
        isKicked = false // 确保重置踢出状态
    }

    // 增加失败计数
    fun incrementFailedAttempts(): Boolean {
        failedAttempts++
        lastFailedTime = System.currentTimeMillis()

        // 检查是否达到最大尝试次数（仅在启用限制时）
        if (MineAuthConfig.config.enableLoginLimit && failedAttempts >= MineAuthConfig.config.maxLoginAttempts) {
            isKicked = true
            return true // 需要踢出玩家
        }
        return false
    }

    // 检查是否达到最大失败次数
    fun isLocked(): Boolean {
        // 如果未启用登录限制，则永远不会锁定
        if (!MineAuthConfig.config.enableLoginLimit) {
            return false
        }
        return isKicked && isInCoolDown()
    }

    // 检查是否在冷却期内
    fun isInCoolDown(): Boolean {
        if (!isKicked) return false

        val coolDownMillis = MineAuthConfig.config.getCoolDownTimeDuration().inWholeMilliseconds
        val now = System.currentTimeMillis()
        val unlockTime = lastFailedTime + coolDownMillis

        return now < unlockTime
    }

    // 获取剩余冷却时间（秒）
    fun getCoolDownRemainingTime(): Long {
        if (!isKicked || !isInCoolDown()) return 0

        val coolDownMillis = MineAuthConfig.config.getCoolDownTimeDuration().inWholeMilliseconds
        val now = System.currentTimeMillis()
        val unlockTime = lastFailedTime + coolDownMillis

        return (unlockTime - now) / 1000
    }

    // 获取剩余尝试次数
    fun getRemainingAttempts(): Int {
        if (!MineAuthConfig.config.enableLoginLimit) {
            return Int.MAX_VALUE // 无限尝试
        }
        return MineAuthConfig.config.maxLoginAttempts - failedAttempts
    }

    // 踢出玩家
    //fun kickPlayer() {
    //    isKicked = true
    //    lastFailedTime = System.currentTimeMillis()
    //}

    // 检查是否可以解除锁定（冷却时间已过）
    fun checkAndUnlock(): Boolean {
        if (isKicked && !isInCoolDown()) {
            resetFailedAttempts()
            return true
        }
        return false
    }


}