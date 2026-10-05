package com.autel.sdk.debugtools.helper

import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.enums.DetectTargetEnum
import com.autel.player.player.ai.ResourceManager

/**
 * AI recognition demo selectable target type catalog, aligned with common Athena classes.
 */
data class AiDemoTargetTypeOption(
    val typeId: Int,
    val label: String,
)

object AiDemoTargetTypeCatalog {

    val options: List<AiDemoTargetTypeOption> = listOf(
        AiDemoTargetTypeOption(DetectTargetEnum.PERSON.value, "Person"),
        AiDemoTargetTypeOption(DetectTargetEnum.CAR.value, "Car"),
        AiDemoTargetTypeOption(DetectTargetEnum.BOAT.value, "Boat"),
        AiDemoTargetTypeOption(DetectTargetEnum.VEHICLE.value, "Vehicle"),
    )

    val defaultSelectedIds: Set<Int> = setOf(
        ResourceManager.TARGET_TYPE.TARGET_TYPE_PERSON.value,
        ResourceManager.TARGET_TYPE.TARGET_TYPE_CAR.value,
        ResourceManager.TARGET_TYPE.TARGET_TYPE_BOAT.value,
        ResourceManager.TARGET_TYPE.TARGET_TYPE_VEHICLE.value,
        ResourceManager.TARGET_TYPE.TARGET_TYPE_CIVILIAN.value,
    )
}
