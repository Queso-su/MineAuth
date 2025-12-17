// UsercacheUtil.kt (新建)
package com.quesox.mineauth

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.command.permission.PermissionLevel
import net.minecraft.util.math.MathHelper
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

data class UsercacheEntry(
    val uuid: String,
    val name: String,
    val expiresOn: String
)

object UsercacheUtil {
    private val logger = LoggerFactory.getLogger("mineauth")
    private val gson = Gson()
    private val usercacheFile = File("usercache.json")

    // 获取所有玩家名称（从 usercache.json）
    fun getAllPlayerNamesFromUsercache(): Set<String> {
        return try {
            if (usercacheFile.exists() && usercacheFile.length() > 0) {
                val jsonString = usercacheFile.readText()
                val typeToken = object : TypeToken<List<UsercacheEntry>>() {}.type
                val entries: List<UsercacheEntry> = gson.fromJson(jsonString, typeToken)
                entries.map { it.name }.toSet()
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            logger.error("读取 usercache.json 失败: ${e.message}")
            emptySet()
        }
    }

    // 获取玩家UUID（通过名称）
    fun getUuidByName(playerName: String): UUID? {
        return try {
            if (usercacheFile.exists() && usercacheFile.length() > 0) {
                val jsonString = usercacheFile.readText()
                val typeToken = object : TypeToken<List<UsercacheEntry>>() {}.type
                val entries: List<UsercacheEntry> = gson.fromJson(jsonString, typeToken)

                entries.find { it.name.equals(playerName, ignoreCase = true) }
                    ?.let { UUID.fromString(it.uuid) }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("从 usercache.json 获取UUID失败: ${e.message}")
            null
        }
    }



}