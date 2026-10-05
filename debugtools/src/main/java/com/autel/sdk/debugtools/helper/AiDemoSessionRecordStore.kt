package com.autel.sdk.debugtools.helper

import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.enums.DetectTargetEnum
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI recognition demo lightweight per-session event record (in-memory, a simplified Athena recognition history).
 */
data class AiDemoSessionEvent(
    val timeMs: Long,
    val objectId: Int,
    val type: Int,
    val typeName: String,
    val confidence: Float,
)

class AiDemoSessionRecordStore {

    private val events = mutableListOf<AiDemoSessionEvent>()
    private val seenObjectIds = mutableSetOf<Int>()
    private var sessionStartMs: Long? = null

    fun startSession() {
        clear()
        sessionStartMs = System.currentTimeMillis()
    }

    fun clear() {
        events.clear()
        seenObjectIds.clear()
        sessionStartMs = null
    }

    fun recordIfNew(objectId: Int, type: Int, confidence: Float) {
        if (objectId <= 0 || !seenObjectIds.add(objectId)) {
            return
        }
        val typeName = DetectTargetEnum.findEnum(type).let { enum ->
            DetectTargetEnum.getEnglishName(enum).ifEmpty { enum.info }
        }
        events.add(
            AiDemoSessionEvent(
                timeMs = System.currentTimeMillis(),
                objectId = objectId,
                type = type,
                typeName = typeName.ifEmpty { "Type($type)" },
                confidence = confidence,
            )
        )
    }

    fun formatForDisplay(): String {
        if (events.isEmpty()) {
            return ""
        }
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return events.joinToString(separator = "\n") { event ->
            val time = formatter.format(Date(event.timeMs))
            "$time  id=${event.objectId}  ${event.typeName}  conf=${"%.2f".format(Locale.US, event.confidence)}"
        }
    }

    fun eventCount(): Int = events.size

    fun sessionStartTimeMs(): Long? = sessionStartMs
}
