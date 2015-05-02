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
    private static final int VIDEO_TRACK = 100;
    private static final int AUDIO_TRACK = 101;
    private static final String TAG = "SrsMuxer";

    /**
     * constructor.
     * @param path the http flv url to post to.
     * @param format the mux format, @see SrsHttpFlv.OutputFormat
     */
    public SrsHttpFlv(String path, int format) {
        url = path;
        flv = new SrsFlv();
    }

    /**
     * Adds a track with the specified format.
     * @param format The media format for the track.
     * @return The track index for this newly added track.
     */
    public int addTrack(MediaFormat format) {
        if (format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_AVC) {
            flv.setVideoTrack(format);
            return VIDEO_TRACK;
        }

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
    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) throws Exception {
        //Log.i(TAG, String.format("dumps the es stream %dB, pts=%d", bufferInfo.size, bufferInfo.presentationTimeUs / 1000));
        //SrsHttpFlv.SrsUtils utils = new SrsHttpFlv.SrsUtils();
        //utils.srs_print_bytes(byteBuf, bufferInfo.size);

        if (bufferInfo.offset > 0) {
            Log.w(TAG, String.format("encoded frame %dB, offset=%d pts=%dms",
                    bufferInfo.size, bufferInfo.offset, bufferInfo.presentationTimeUs / 1000
            ));
        }

        if (VIDEO_TRACK == trackIndex) {
            flv.writeVideoSample(byteBuf, bufferInfo);
        }

        // TODO: FIMXE: support audio.
    }

    private void cycle() throws Exception {
        Log.i(TAG, String.format("muxer opened, url=%s", url));
        conn.setDoOutput(true);
        conn.setChunkedStreamingMode(0);
        bos = new BufferedOutputStream(conn.getOutputStream());

        while (!Thread.interrupted()) {
            // TODO: FIXME: implements it.
            Thread.sleep(1000, 0);
            //Log.i(TAG, String.format("worker thread pump message"));
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
     * Table 7-1 – NAL unit type codes, syntax element categories, and NAL unit type classes
     * H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 83.
     */
    class SrsAvcNaluType
    {
        // Unspecified
        public final static int Reserved = 0;

        // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int NonIDR = 1;
        // Coded slice data partition A slice_data_partition_a_layer_rbsp( )
        public final static int DataPartitionA = 2;
        // Coded slice data partition B slice_data_partition_b_layer_rbsp( )
        public final static int DataPartitionB = 3;
        // Coded slice data partition C slice_data_partition_c_layer_rbsp( )
        public final static int DataPartitionC = 4;
        // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int IDR = 5;
        // Supplemental enhancement information (SEI) sei_rbsp( )
        public final static int SEI = 6;
        // Sequence parameter set seq_parameter_set_rbsp( )
        public final static int SPS = 7;
        // Picture parameter set pic_parameter_set_rbsp( )
        public final static int PPS = 8;
        // Access unit delimiter access_unit_delimiter_rbsp( )
        public final static int AccessUnitDelimiter = 9;
        // End of sequence end_of_seq_rbsp( )
        public final static int EOSequence = 10;
        // End of stream end_of_stream_rbsp( )
        public final static int EOStream = 11;
        // Filler data filler_data_rbsp( )
        public final static int FilterData = 12;
        // Sequence parameter set extension seq_parameter_set_extension_rbsp( )
        public final static int SPSExt = 13;
        // Prefix NAL unit prefix_nal_unit_rbsp( )
        public final static int PrefixNALU = 14;
        // Subset sequence parameter set subset_seq_parameter_set_rbsp( )
        public final static int SubsetSPS = 15;
        // Coded slice of an auxiliary coded picture without partitioning slice_layer_without_partitioning_rbsp( )
        public final static int LayerWithoutPartition = 19;
        // Coded slice extension slice_layer_extension_rbsp( )
        public final static int CodedSliceExt = 20;
    }

    /**
     * utils functions from srs.
     */
    public class SrsUtils {
        private final static String TAG = "SrsMuxer";

        public SrsAnnexbSearch srs_avc_startswith_annexb(ByteBuffer bb, MediaCodec.BufferInfo bi) {
            SrsAnnexbSearch as = new SrsAnnexbSearch();
            as.match = false;

            int pos = bb.position();
            while (pos < bi.size - 3) {
                // not match.
                if (bb.get(pos) != 0x00 || bb.get(pos + 1) != 0x00) {
                    break;
                }

                // match N[00] 00 00 01, where N>=0
                if (bb.get(pos + 2) == 0x01) {
                    as.match = true;
                    as.nb_start_code = pos + 3 - bb.position();
                    break;
                }

                pos++;
            }

            return as;
        }

        /**
         * print the size of bytes in bb
         * @param bb the bytes to print.
         * @param size the total size of bytes to print.
         */
        public void srs_print_bytes(ByteBuffer bb, int size) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            int bytes_in_line = 16;
            for (i = 0; i < size; i++) {
                sb.append(String.format("0x%s ", Integer.toHexString(bb.get(i) & 0xFF)));
                if (((i + 1) % bytes_in_line) == 0) {
                    Log.i(TAG, String.format("%03d-%03d: %s", i / bytes_in_line * bytes_in_line, i, sb.toString()));
                    sb = new StringBuilder();
                }
            }
            if (sb.length() > 0) {
                Log.i(TAG, String.format("%03d-%03d: %s", size / bytes_in_line * bytes_in_line, i - 1, sb.toString()));
            }
        }
    }

    /**
     * the search result for annexb.
     */
    class SrsAnnexbSearch {
        public int nb_start_code = 0;
        public boolean match = false;
    }

    /**
     * the demuxed annexb frame.
     */
    class SrsAnnexbFrame {
        public ByteBuffer frame;
        public int size;
    }

    /**
     * the raw h.264 stream, in annexb.
     */
    class SrsRawH264Stream {
        private SrsUtils utils;

        public SrsRawH264Stream() {
            utils = new SrsUtils();
        }

        public SrsAnnexbFrame annexb_demux(ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {
            SrsAnnexbFrame tbb = new SrsAnnexbFrame();

            while (bb.position() < bi.size) {
                // each frame must prefixed by annexb format.
                // about annexb, @see H.264-AVC-ISO_IEC_14496-10.pdf, page 211.
                SrsAnnexbSearch tbbsc = utils.srs_avc_startswith_annexb(bb, bi);
                if (!tbbsc.match || tbbsc.nb_start_code < 3) {
                    throw new Exception(String.format("annexb not match for %dB, pos=%d", bi.size, bb.position()));
                }

                // the start codes.
                ByteBuffer tbbs = bb.slice();
                for (int i = 0; i < tbbsc.nb_start_code; i++) {
                    bb.get();
                }

                // find out the frame size.
                tbb.frame = bb.slice();
                int pos = bb.position();
                while (bb.position() < bi.size) {
                    SrsAnnexbSearch bsc = utils.srs_avc_startswith_annexb(bb, bi);
                    if (bsc.match) {
                        break;
                    }
                    bb.get();
                }

                tbb.size = bb.position() - pos;
                if (bb.position() < bi.size) {
                    Log.i(TAG, String.format("annexb multiple match ok, pts=%d", bi.presentationTimeUs / 1000));
                    utils.srs_print_bytes(tbbs, 16);
                    utils.srs_print_bytes(bb.slice(), 16);
                }
                //Log.i(TAG, String.format("annexb match %d bytes", tbb.size));
                break;
            }

            return tbb;
        }
    }

    /**
     * remux the annexb to flv tags.
     */
    class SrsFlv {
        private MediaFormat videoTrack;
        private MediaFormat audioTrack;

        private SrsRawH264Stream avc;

        public SrsFlv() {
            avc = new SrsRawH264Stream();
        }

        public void setVideoTrack(MediaFormat format) {
            videoTrack = format;
        }

        public void setAudioTrack(MediaFormat format) {
            audioTrack = format;
        }

        public void writeVideoSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {
            int pts = (int)(bi.presentationTimeUs / 1000);
            int dts = (int)pts;

            byte[] ibps = null;
            int frame_type = SrsCodecVideoAVCFrame.InterFrame;
            //Log.i(TAG, String.format("video %d/%d bytes, offset=%d, position=%d, pts=%d", bb.remaining(), bi.size, bi.offset, bb.position(), pts));

            // send each frame.
            while (bb.position() < bi.size) {
                SrsAnnexbFrame frame = avc.annexb_demux(bb, bi);

                // 5bits, 7.3.1 NAL unit syntax,
                // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
                //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
                int nal_unit_type = (int)(frame.frame.get(0) & 0x1f);
                Log.i(TAG, String.format("annexb demux %dB, pts=%d, frame=%dB, nalu=%d", bi.size, pts, frame.size, nal_unit_type));

/*
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
*/
            }
        }
    }
}
