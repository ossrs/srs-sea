package net.ossrs.androidpublisher;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

/**
 * Created by winlin on 5/2/15.
 * to POST the h.264/avc annexb frame to SRS over HTTP FLV.
 * @remark we must start a worker thread to send data to server.
 * @see android.media.MediaMuxer https://developer.android.com/reference/android/media/MediaMuxer.html
 */
public class SrsHttpFlv {
    private String url;
    private HttpURLConnection conn;
    private BufferedOutputStream bos;
    private Thread worker;
    private SrsFlv flv;
    private static final String TAG = "SrsMuxer";

    /**
     * constructor.
     * @param path the http flv url to post to.
     * @param format the mux format, @see SrsHttpFlv.OutputFormat
     */
    public SrsHttpFlv(String path, int format) {
        url = path;
    }

    /**
     * Adds a track with the specified format.
     * @param format The media format for the track.
     * @return The track index for this newly added track.
     */
    public int addTrack(MediaFormat format) {
        // TODO: FIXME: support multiple tracks.
        return 0;
    }

    /**
     * start to the remote SRS for remux.
     */
    public void start() throws IOException {
        URL u = new URL(url);
        conn = (HttpURLConnection)u.openConnection();
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    cycle();
                } catch (InterruptedException ie) {
                } catch (Exception e) {
                    Log.i(TAG, "thread exception.");
                    e.printStackTrace();
                }
            }
        });
        worker.start();
    }

    /**
     * Make sure you call this when you're done to free up any resources
     * instead of relying on the garbage collector to do this for you at
     * some point in the future.
     */
    public void release() {
        stop();
    }

    /**
     * stop the muxer, disconnect HTTP connection from SRS.
     */
    public void stop() {
        if (worker == null && conn == null) {
            return;
        }
        
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                Log.i(TAG, "join thread failed.");
                e.printStackTrace();
                worker.stop();
            }
            worker = null;
        }
        if (conn != null) {
            conn.disconnect();
            conn = null;
        }
        Log.i(TAG, String.format("muxer closed, url=%s", url));
    }

    /**
     * send the annexb frame to SRS over HTTP FLV.
     * @param trackIndex The track index for this sample.
     * @param byteBuf The encoded sample.
     * @param bufferInfo The buffer information related to this sample.
     */
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
        if (bufferInfo.offset > 0) {
            Log.w(TAG, String.format("encoded frame %dB, offset=%d pts=%dms",
                bufferInfo.size, bufferInfo.offset, bufferInfo.presentationTimeUs / 1000
            ));
        }

        int pts = (int)bufferInfo.presentationTimeUs;
        int dts = (int)pts;

        byte[] ibps = null;
        int frame_type = SrsCodecVideoAVCFrame.InterFrame;
/*
        // send each frame.
        while (annexb.position() < bi.size) {
            char* frame = NULL;
            int frame_size = 0;
            if ((ret = avc->annexb_demux(avs, &frame, &frame_size)) != ERROR_SUCCESS) {
                return ret;
            }

            // 5bits, 7.3.1 NAL unit syntax,
            // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
            //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
            SrsAvcNaluType nal_unit_type = (SrsAvcNaluType)(frame[0] & 0x1f);

            // for IDR frame, the frame is keyframe.
            if (nal_unit_type == SrsAvcNaluTypeIDR) {
                frame_type = SrsCodecVideoAVCFrameKeyFrame;
            }

            // ignore the nalu type aud(9)
            if (nal_unit_type == SrsAvcNaluTypeAccessUnitDelimiter) {
                continue;
            }

            // for sps
            if (avc->is_sps(frame, frame_size)) {
                std::string sps;
                if ((ret = avc->sps_demux(frame, frame_size, sps)) != ERROR_SUCCESS) {
                    return ret;
                }

                if (h264_sps == sps) {
                    continue;
                }
                h264_sps_changed = true;
                h264_sps = sps;
                continue;
            }

            // for pps
            if (avc->is_pps(frame, frame_size)) {
                std::string pps;
                if ((ret = avc->pps_demux(frame, frame_size, pps)) != ERROR_SUCCESS) {
                    return ret;
                }

                if (h264_pps == pps) {
                    continue;
                }
                h264_pps_changed = true;
                h264_pps = pps;
                continue;
            }

            // ibp frame.
            std::string ibp;
            if ((ret = avc->mux_ipb_frame(frame, frame_size, ibp)) != ERROR_SUCCESS) {
                return ret;
            }
            ibps.append(ibp);
        }

        if ((ret = write_h264_sps_pps(dts, pts)) != ERROR_SUCCESS) {
            return ret;
        }

        if ((ret = write_h264_ipb_frame(ibps, frame_type, dts, pts)) != ERROR_SUCCESS) {
            // drop the ts message.
            if (ret == ERROR_H264_DROP_BEFORE_SPS_PPS) {
                return ERROR_SUCCESS;
            }
            return ret;
        }*/
    }

    private void cycle() throws Exception {
        Log.i(TAG, String.format("muxer opened, url=%s", url));
        conn.setDoOutput(true);
        conn.setChunkedStreamingMode(0);
        bos = new BufferedOutputStream(conn.getOutputStream());

        while (!Thread.interrupted()) {
            Thread.sleep(1000, 0);
            Log.i(TAG, String.format("worker thread pump message"));
        }
    }

    /**
     * the supported output format for muxer.
     */
    class OutputFormat {
        public final static int MUXER_OUTPUT_HTTP_FLV = 0;
    }

    // E.4.3.1 VIDEODATA
    // Frame Type UB [4]
    // Type of video frame. The following values are defined:
    //     1 = key frame (for AVC, a seekable frame)
    //     2 = inter frame (for AVC, a non-seekable frame)
    //     3 = disposable inter frame (H.263 only)
    //     4 = generated key frame (reserved for server use only)
    //     5 = video info/command frame
    class SrsCodecVideoAVCFrame
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;
        public final static int Reserved1 = 6;

        public final static int KeyFrame                     = 1;
        public final static int InterFrame                 = 2;
        public final static int DisposableInterFrame         = 3;
        public final static int GeneratedKeyFrame            = 4;
        public final static int VideoInfoFrame                = 5;
    }

    /**
     * remux the annexb to flv tags.
     */
    class SrsFlv {
    }
}
