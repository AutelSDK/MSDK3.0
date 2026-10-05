package com.autel.sdk.debugtools.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.autel.drone.sdk.SDKConstants
import com.autel.player.player.AutelPlayerManager
import com.autel.player.player.autelplayer.AutelPlayerImpl
import com.autel.player.player.autelplayer.AutelPlayerView
import com.autel.sdk.debugtools.R
import com.autel.sdk.debugtools.databinding.FragMuiltStreamBinding
import com.autel.sourcer.WebrtcSourcer

/**
 * multiple type of codec for video streaming
 * @author Autel Robotics on 2022/12/17.
 */
class WHEPPlayerFragment : AutelFragment() {

    var right_view: LinearLayout? = null
    private var mAutelPlayer: AutelPlayerImpl? = null
    var codecView: AutelPlayerView? = null

    var left_view: LinearLayout? = null
    private var mAutelPlayer2: AutelPlayerImpl? = null
    var codecView2: AutelPlayerView? = null

    private lateinit var uiBinding: FragMuiltStreamBinding

    var mSourcer:WebrtcSourcer ? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        uiBinding = FragMuiltStreamBinding.inflate(inflater, container, false)

        AutelPlayerManager.getInstance().init(activity, true, true);

        return uiBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        left_view = uiBinding.root.findViewById(R.id.layout_left_view)
        codecView = createAutelCodecView()
        with(left_view) { this?.addView(codecView) }

        //mAutelPlayer = AutelPlayer(SDKConstants.STREAM_CHANNEL_16110)
        //mAutelPlayer!!.addVideoView(codecView)

        AutelPlayerManager.getInstance().getAutelPlayer(SDKConstants.STREAM_CHANNEL_16110).addVideoView(codecView);

        right_view = uiBinding.root.findViewById(R.id.layout_right_view)
        codecView2 = createAutelCodecView2()
        with(right_view) { this?.addView(codecView2) }


        AutelPlayerManager.getInstance().getAutelPlayer(SDKConstants.STREAM_CHANNEL_10000).addVideoView(codecView2);

        mSourcer =  WebrtcSourcer(context, true, SDKConstants.STREAM_CHANNEL_10000);
        mSourcer?.prepare();

        mSourcer?.start("http://YOUR_WHEP_SERVER/index/api/whep?app=live&stream=stream1&type=play");

        mSourcer =  WebrtcSourcer(context, true, SDKConstants.STREAM_CHANNEL_16110);
        mSourcer?.prepare();
        mSourcer?.start("http://YOUR_WHEP_SERVER/index/api/whep?app=live&stream=stream2&type=play");

    }

    /**
     * create code view for media player 1
     */
    private fun createAutelCodecView(): AutelPlayerView? {
        val codecView = AutelPlayerView(activity)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        codecView.layoutParams = params
        return codecView
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

    override fun onDestroy() {
        super.onDestroy()

        if (mAutelPlayer != null) {
            mAutelPlayer!!.stopPlayer()
            mAutelPlayer!!.removeVideoView()
            mAutelPlayer!!.releasePlayer()
        }

        if (mAutelPlayer2 != null) {
            mAutelPlayer2!!.stopPlayer()
            mAutelPlayer2!!.removeVideoView()
            mAutelPlayer2!!.releasePlayer()
        }

    }
}
