package com.autel.sdk.debugtools.fragment

import com.autel.drone.sdk.SDKConfig
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.activity.FragmentPageInfo
import com.autel.sdk.debugtools.activity.FragmentPageInfoItem

/**
 * page info creating with over ride method and items list
 * Copyright: Autel Robotics
 * @author Autel Robotics on 2022/12/17.
 */
class ExternalFragmentPageInfoFactory : IFragmentPageInfoFactory {

    override fun createPageInfo(): FragmentPageInfo {

        // A/V capabilities
        val audioVideoCenter = FragmentPageInfoItem(
            -1,
            R.string.debug_item_audio_video_center_title,
            R.string.debug_item_audio_video_center_title_description
        )

        val onDemandStreamPage = audioVideoCenter.InnerFragmentPageInfoItem(
            R.id.ondemand_stream_page,
            R.string.debug_item_ondemand_stream_title,
            R.string.debug_item_ondemand_stream_description
        )

        val multiDecoder = audioVideoCenter.InnerFragmentPageInfoItem(
            R.id.multi_video_decoding_page,
            R.string.debug_item_multi_video_decoding_title,
            R.string.debug_item_multi_video_decoding_description
        )

        val surfacePage = audioVideoCenter.InnerFragmentPageInfoItem(
            R.id.video_decoding_surface_page,
            R.string.debug_item_multi_video_surface_title,
            R.string.debug_item_multi_video_surface_description
        )

        val rtmpPage = audioVideoCenter.InnerFragmentPageInfoItem(
            R.id.livestream_page,
            R.string.debug_item_livestreaming_title,
            R.string.debug_item_livestreaming_title_description
        )
        val rtspPage = audioVideoCenter.InnerFragmentPageInfoItem(
            R.id.rtspclient_page,
            R.string.debug_item_rtspclient_title,
            R.string.debug_item_rtspclient_title_description
        )
        val gb28181Page = audioVideoCenter.InnerFragmentPageInfoItem(
            R.id.gb28181_page,
            R.string.debug_item_gb28181_title,
            R.string.debug_item_gb28181_title_description
        )
        val avCenter = ArrayList<FragmentPageInfoItem.InnerFragmentPageInfoItem>()
        avCenter.add(onDemandStreamPage)
        avCenter.add(multiDecoder)
        avCenter.add(surfacePage)
        avCenter.add(rtmpPage)
        avCenter.add(rtspPage)
        avCenter.add(gb28181Page)
        audioVideoCenter.fragmentInfoList = avCenter

        return FragmentPageInfo(R.navigation.nav_external).apply {
            items.add(
                FragmentPageInfoItem(
                    R.id.key_value_page,
                    R.string.debug_item_key_value_title,
                    R.string.debug_item_key_value_description
                )
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.preview_info_page,
                    R.string.debug_preview_info_page,
                    R.string.debug_preview_info_description
                )
            )

            if(!SDKConfig.isSingle()){
                items.add(
                    FragmentPageInfoItem(
                        R.id.net_mesh_demo_page,
                        R.string.debug_item_net_mesh_demo_title,
                        R.string.debug_item_net_mesh_demo_description
                    )
                )

                items.add(
                    FragmentPageInfoItem(
                        R.id.im_demo_page,
                        R.string.debug_item_im_demo_title,
                        R.string.debug_item_im_demo_description
                    )
                )
            }

            items.add(
                FragmentPageInfoItem(
                    R.id.virtual_stick_page,
                    R.string.debug_item_virtual_stick_title,
                    R.string.debug_item__virtual_stick_description
                )
            )
            items.add(
                FragmentPageInfoItem(
                    R.id.media_page,
                    R.string.debug_item_media_file_title,
                    R.string.debug_item_media_file_description
                )
            )

            // Add the A/V capabilities item
            items.add(audioVideoCenter)

            items.add(
                FragmentPageInfoItem(
                    R.id.recognize_page,
                    R.string.debug_recognition_page,
                    R.string.debug_recognition_page_description
                )
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.scenerio_testing_page,
                    R.string.debug_scenario_testing_title,
                    R.string.debug_scenario_testing_description
                )
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.test_key_page,
                    R.string.debug_test_key,
                    R.string.debug_test_key_description
                )
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.rtk_page,
                    R.string.debug_rtk_title,
                    R.string.debug_rtk_title_description
                )
            )


            items.add(
                FragmentPageInfoItem(
                    R.id.device_log,
                    R.string.debug_device_log,
                    R.string.debug_device_log_description
                )
            )



            items.add(
                FragmentPageInfoItem(
                    R.id.ota_page,
                    R.string.debug_device_ota,
                    R.string.debug_device_ota_description
                )
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.mission_demo_page,
                    R.string.mission_demo,
                    R.string.mission_demo_description
                )
            )


            items.add(
                FragmentPageInfoItem(
                    R.id.perception_page,
                    R.string.debug_perception_page,
                    R.string.debug_perception_page_description
                )
            )
            items.add(
                FragmentPageInfoItem(
                    R.id.lte_page,
                    R.string.debug_lte_page,
                    R.string.debug_lte_page_description
                )
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.alarm_page,
                    R.string.debug_alarm_page,
                    R.string.debug_alarm_page_description
                )
            )
            items.add(
                FragmentPageInfoItem(
                    R.id.lidar_rang_page,
                    R.string.debug_lidar_ranging_page,
                    R.string.debug_lidar_ranging_description
                )
            )

            items.add(
                FragmentPageInfoItem(R.id.surround_task_page,
                    R.string.debug_surround_task_page,
                    R.string.debug_surround_task_page_description)
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.security_customization_test_page,
                    R.string.security_customization_test_page,
                    R.string.security_customization_test_description
                )
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.fly_zone_page,
                    R.string.debug_item_fly_zone_title,
                    R.string.debug_item_fly_zone_description
                )
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.wired_relay_test_page,
                    R.string.debug_wired_relay_page,
                    R.string.debug_wired_relay_page_description
                )
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.esim_page,
                    R.string.debug_esim_page,
                    R.string.debug_esim_page_description
                )
            )

            items.add(
                FragmentPageInfoItem(
                    R.id.radar_det_test_page,
                    R.string.debug_radar_det_test_page,
                    R.string.debug_radar_det_test_description
                )
            )

//            items.add(
//                FragmentPageInfoItem(
//                    R.id.psdk_page,
//                    R.string.debug_psdk_usage_page,
//                    R.string.debug_psdk_usage
//                )
//            )
        }
    }
}
