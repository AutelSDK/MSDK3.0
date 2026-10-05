package com.autel.sdk.debugtools.helper

import android.content.Context

/**
 * AI recognition demo local preferences: target type multi-select and alert sound toggle.
 */
object AiDemoTargetTypePrefs {

    private const val SP_NAME = "ai_demo_recognize_prefs"
    private const val KEY_TARGET_TYPES = "selected_target_types"
    private const val KEY_SOUND_ENABLED = "sound_alert_enabled"

    fun getSelectedTypeIds(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_TARGET_TYPES, null)
        if (saved.isNullOrBlank()) {
            return AiDemoTargetTypeCatalog.defaultSelectedIds
        }
        return saved.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { id -> AiDemoTargetTypeCatalog.options.any { it.typeId == id } }
            .toSet()
            .ifEmpty { AiDemoTargetTypeCatalog.defaultSelectedIds }
    }

    fun saveSelectedTypeIds(context: Context, ids: Set<Int>) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TARGET_TYPES, ids.joinToString(","))
            .apply()
    }

    fun isSoundAlertEnabled(context: Context): Boolean {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundAlertEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOUND_ENABLED, enabled)
            .apply()
    }
}
