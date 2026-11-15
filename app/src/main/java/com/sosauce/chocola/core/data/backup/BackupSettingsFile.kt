package com.sosauce.chocola.core.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val SETTINGS_BACKUP_VERSION = 1
const val SETTINGS_BACKUP_APP_NAME = "ByMusic"
const val SETTINGS_BACKUP_APP_NAME_LEGACY = "Chocola"

fun isSupportedSettingsBackupApp(app: String): Boolean {
    return app == SETTINGS_BACKUP_APP_NAME || app == SETTINGS_BACKUP_APP_NAME_LEGACY
}

@Serializable
data class BackupSettingsFile(
    val version: Int = SETTINGS_BACKUP_VERSION,
    val app: String = SETTINGS_BACKUP_APP_NAME,
    val createdAtMillis: Long,
    val settings: Map<String, SettingsWithType>
)

@Serializable
data class SettingsWithType(
    val type: SettingType,
    val value: JsonElement
)


enum class SettingType {
    STRING,
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    SET_STRING
}
