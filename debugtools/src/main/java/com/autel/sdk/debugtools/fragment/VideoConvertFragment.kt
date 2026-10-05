package com.autel.sdk.debugtools.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.autel.drone.sdk.SDKConstants
import com.autel.drone.sdk.vmodelx.manager.VideoConvertManager
import com.autel.ffmpeg.VideoConvert

import com.autel.player.player.AutelPlayerManager
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.player.player.autelplayer.AutelPlayerView
import com.autel.sdk.debugtools.helper.PlayerWeightHelper.applySplitScreenWeight
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragmentVideoConvertBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class VideoConvertFragment : AutelFragment()  {
    var contentView: View? = null
    lateinit var edit_url: TextView
    lateinit var btn_start: Button

    var right_view: LinearLayout? = null
    private var mAutelPlayer: AutelPlayer? = null
    var codecView: AutelPlayerView? = null

    var left_view: LinearLayout? = null
    private var mAutelPlayer2: AutelPlayer? = null
    var codecView2: AutelPlayerView? = null

    var rtmpPort:Int = SDKConstants.STREAM_CHANNEL_16110;

    private lateinit var uiBinding: FragmentVideoConvertBinding

    private var mPublishFlag: Boolean = false

    private var iCurrentPort:Int = SDKConstants.STREAM_CHANNEL_16110 // wide-angle

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var mVideoConvert:VideoConvert?= null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        uiBinding = FragmentVideoConvertBinding.inflate(inflater, container, false)

        return uiBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        edit_url = uiBinding.root.findViewById(R.id.edit_url)
        btn_start = uiBinding.root.findViewById(R.id.btn_start)

        left_view = uiBinding.root.findViewById(R.id.layout_left_view)
        codecView = createAutelCodecView()
        with(left_view) { this?.addView(codecView) }

        mAutelPlayer = AutelPlayer(SDKConstants.STREAM_CHANNEL_16110)
        mAutelPlayer!!.addVideoView(codecView)

        AutelPlayerManager.getInstance().addAutelPlayer(mAutelPlayer);


        right_view = uiBinding.root.findViewById(R.id.layout_right_view)
        codecView2 = createAutelCodecView2()
        with(right_view) { this?.addView(codecView2) }


        mAutelPlayer2 = AutelPlayer(SDKConstants.STREAM_CHANNEL_16115)
        mAutelPlayer2!!.addVideoView(codecView2)

        AutelPlayerManager.getInstance().addAutelPlayer(mAutelPlayer2);

        mAutelPlayer!!.startPlayer()
        mAutelPlayer.applySplitScreenWeight()
        mAutelPlayer2!!.startPlayer()
        mAutelPlayer2.applySplitScreenWeight()

        initView();

    }

    /**
     * create code view for media player 2
     */
    private fun createAutelCodecView2(): AutelPlayerView? {
        val codecView = AutelPlayerView(activity)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        codecView.layoutParams = params
        return codecView
    }

    private fun createAutelCodecView(): AutelPlayerView? {
        val codecView = AutelPlayerView(activity)
        val params = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        codecView.layoutParams = params
        return codecView
    }

    private fun initView() {

        btn_start.isEnabled = true

        btn_start.setOnClickListener {

            if (mPublishFlag) {
                mPublishFlag = false;
                VideoConvertManager.getInstance().stopPublishStream()
            } else {
                mPublishFlag = true
                VideoConvertManager.getInstance().startVideoConvert(iCurrentPort)
            }
        }
    }

    private fun stopPublish() {

        btn_start.text = getString(R.string.debug_start)
    }

    private fun startPublish() {

        btn_start.text = getString(R.string.debug_stop)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (mAutelPlayer != null) {
            mAutelPlayer?.stopPlayer()
            mAutelPlayer?.removeVideoView()
            mAutelPlayer?.releasePlayer()
        }

        if (mAutelPlayer2 != null) {
            mAutelPlayer2?.stopPlayer()
            mAutelPlayer2?.removeVideoView()
            mAutelPlayer2?.releasePlayer()
        }
    }
}