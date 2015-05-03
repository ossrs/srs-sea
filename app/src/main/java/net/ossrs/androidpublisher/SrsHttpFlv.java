package net.ossrs.androidpublisher;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Queue;

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
    private Looper looper;
    private Handler handler;

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

        if (looper != null) {
            looper.quit();
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
        // create the handler.
        Looper.prepare();
        looper = Looper.myLooper();
        handler = new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what != SrsMessageType.FLV) {
                    Log.w(TAG, String.format("worker: drop unkown message, what=%d", msg.what));
                    return;
                }
                SrsFlvFrame frame = (SrsFlvFrame)msg.obj;
                sendFlvTag(bos, frame);
            }
        };
        flv.setHandler(handler);

        Log.i(TAG, String.format("connect to SRS."));
        conn.setDoOutput(true);
        conn.setChunkedStreamingMode(0);
        bos = new BufferedOutputStream(conn.getOutputStream());
        Log.i(TAG, String.format("muxer opened, url=%s", url));

        Looper.loop();
    }

    private void sendFlvTag(BufferedOutputStream bos, SrsFlvFrame frame) {
        if (frame.frame_type == SrsCodecVideoAVCFrame.KeyFrame) {
            Log.i(TAG, String.format("worker: got frame type=%d, dts=%d, size=%dB", frame.type, frame.dts, frame.tag.size));
        } else {
            //Log.i(TAG, String.format("worker: got frame type=%d, dts=%d, size=%dB", frame.type, frame.dts, frame.tag.size));
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

    // AVCPacketType IF CodecID == 7 UI8
    // The following values are defined:
    //     0 = AVC sequence header
    //     1 = AVC NALU
    //     2 = AVC end of sequence (lower level NALU sequence ender is
    //         not required or supported)
    class SrsCodecVideoAVCType
    {
        // set to the max value to reserved, for array map.
        public final static int Reserved                    = 3;

        public final static int SequenceHeader                 = 0;
        public final static int NALU                         = 1;
        public final static int SequenceHeaderEOF             = 2;
    }

    /**
     * E.4.1 FLV Tag, page 75
     */
    class SrsCodecFlvTag
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;

        // 8 = audio
        public final static int Audio = 8;
        // 9 = video
        public final static int Video = 9;
        // 18 = script data
        public final static int Script = 18;
    };

    // E.4.3.1 VIDEODATA
    // CodecID UB [4]
    // Codec Identifier. The following values are defined:
    //     2 = Sorenson H.263
    //     3 = Screen video
    //     4 = On2 VP6
    //     5 = On2 VP6 with alpha channel
    //     6 = Screen video version 2
    //     7 = AVC
    class SrsCodecVideo
    {
        // set to the zero to reserved, for array map.
        public final static int Reserved                = 0;
        public final static int Reserved1                = 1;
        public final static int Reserved2                = 9;

        // for user to disable video, for example, use pure audio hls.
        public final static int Disabled                = 8;

        public final static int SorensonH263             = 2;
        public final static int ScreenVideo             = 3;
        public final static int On2VP6                 = 4;
        public final static int On2VP6WithAlphaChannel = 5;
        public final static int ScreenVideoVersion2     = 6;
        public final static int AVC                     = 7;
    }

    /**
     * the type of message to process.
     */
    class SrsMessageType {
        public final static int FLV = 0x100;
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

        public boolean srs_bytes_equals(byte[] a, byte[]b) {
            if ((a == null || b == null) && (a != null || b != null)) {
                return false;
            }

            if (a.length != b.length) {
                return false;
            }

            for (int i = 0; i < a.length && i < b.length; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }

            return true;
        }

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
            int max = bb.remaining();
            for (i = 0; i < size && i < max; i++) {
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
     * the muxed flv frame.
     */
    class SrsFlvFrame {
        // the tag bytes.
        public SrsAnnexbFrame tag;
        // the frame type, keyframe or not.
        public int frame_type;
        // the tag type, audio, video or data.
        public int type;
        // the dts in ms, tbn is 1000.
        public int dts;
    }

    /**
     * the raw h.264 stream, in annexb.
     */
    class SrsRawH264Stream {
        private SrsUtils utils;
        private final static String TAG = "SrsMuxer";

        public SrsRawH264Stream() {
            utils = new SrsUtils();
        }

        public boolean is_sps(SrsAnnexbFrame frame) {
            if (frame.size < 1) {
                return false;
            }

            // 5bits, 7.3.1 NAL unit syntax,
            // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
            //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
            int nal_unit_type = (int)(frame.frame.get(0) & 0x1f);

            return nal_unit_type == SrsAvcNaluType.SPS;
        }

        public boolean is_pps(SrsAnnexbFrame frame) {
            if (frame.size < 1) {
                return false;
            }

            // 5bits, 7.3.1 NAL unit syntax,
            // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
            //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
            int nal_unit_type = (int)(frame.frame.get(0) & 0x1f);

            return nal_unit_type == SrsAvcNaluType.PPS;
        }

        public SrsAnnexbFrame mux_ibp_frame(SrsAnnexbFrame frame) {
            SrsAnnexbFrame nalu_header = new SrsAnnexbFrame();
            nalu_header.size = 4;
            nalu_header.frame = ByteBuffer.allocate(nalu_header.size);

            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size
            int NAL_unit_length = frame.size;

            // mux the avc NALU in "ISO Base Media File Format"
            // from H.264-AVC-ISO_IEC_14496-15.pdf, page 20
            // NALUnitLength
            nalu_header.frame.putInt(NAL_unit_length);

            // reset the buffer.
            nalu_header.frame.rewind();

            //Log.i(TAG, String.format("mux ibp frame %dB", frame.size));
            //utils.srs_print_bytes(nalu_header.frame, 16);

            return nalu_header;
        }

        public void mux_sequence_header(byte[] sps, byte[] pps, int dts, int pts, ArrayList<SrsAnnexbFrame> frames) {
            // 5bytes sps/pps header:
            //      configurationVersion, AVCProfileIndication, profile_compatibility,
            //      AVCLevelIndication, lengthSizeMinusOne
            // 3bytes size of sps:
            //      numOfSequenceParameterSets, sequenceParameterSetLength(2B)
            // Nbytes of sps.
            //      sequenceParameterSetNALUnit
            // 3bytes size of pps:
            //      numOfPictureParameterSets, pictureParameterSetLength
            // Nbytes of pps:
            //      pictureParameterSetNALUnit

            // decode the SPS:
            // @see: 7.3.2.1.1, H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 62
            if (true) {
                SrsAnnexbFrame hdr = new SrsAnnexbFrame();
                hdr.size = 5;
                hdr.frame = ByteBuffer.allocate(hdr.size);

                // @see: Annex A Profiles and levels, H.264-AVC-ISO_IEC_14496-10.pdf, page 205
                //      Baseline profile profile_idc is 66(0x42).
                //      Main profile profile_idc is 77(0x4d).
                //      Extended profile profile_idc is 88(0x58).
                byte profile_idc = sps[1];
                //u_int8_t constraint_set = frame[2];
                byte level_idc = sps[3];

                // generate the sps/pps header
                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // configurationVersion
                hdr.frame.put((byte)0x01);
                // AVCProfileIndication
                hdr.frame.put(profile_idc);
                // profile_compatibility
                hdr.frame.put((byte)0x00);
                // AVCLevelIndication
                hdr.frame.put(level_idc);
                // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size,
                // so we always set it to 0x03.
                hdr.frame.put((byte)0x03);

                // reset the buffer.
                hdr.frame.rewind();
                frames.add(hdr);
            }

            // sps
            if (true) {
                SrsAnnexbFrame sps_hdr = new SrsAnnexbFrame();
                sps_hdr.size = 3;
                sps_hdr.frame = ByteBuffer.allocate(sps_hdr.size);

                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // numOfSequenceParameterSets, always 1
                sps_hdr.frame.put((byte) 0x01);
                // sequenceParameterSetLength
                sps_hdr.frame.putShort((short)sps.length);

                sps_hdr.frame.rewind();
                frames.add(sps_hdr);

                // sequenceParameterSetNALUnit
                SrsAnnexbFrame sps_bb = new SrsAnnexbFrame();
                sps_bb.size = sps.length;
                sps_bb.frame = ByteBuffer.wrap(sps);
                frames.add(sps_bb);
            }

            // pps
            if (true) {
                SrsAnnexbFrame pps_hdr = new SrsAnnexbFrame();
                pps_hdr.size = 3;
                pps_hdr.frame = ByteBuffer.allocate(pps_hdr.size);

                // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
                // numOfPictureParameterSets, always 1
                pps_hdr.frame.put((byte) 0x01);
                // pictureParameterSetLength
                pps_hdr.frame.putShort((short)pps.length);

                pps_hdr.frame.rewind();
                frames.add(pps_hdr);

                // pictureParameterSetNALUnit
                SrsAnnexbFrame pps_bb = new SrsAnnexbFrame();
                pps_bb.size = pps.length;
                pps_bb.frame = ByteBuffer.wrap(pps);
                frames.add(pps_bb);
            }
        }

        public SrsAnnexbFrame mux_avc2flv(ArrayList<SrsAnnexbFrame> frames, int frame_type, int avc_packet_type, int dts, int pts) {
            SrsAnnexbFrame flv_tag = new SrsAnnexbFrame();

            // for h264 in RTMP video payload, there is 5bytes header:
            //      1bytes, FrameType | CodecID
            //      1bytes, AVCPacketType
            //      3bytes, CompositionTime, the cts.
            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            flv_tag.size = 5;
            for (int i = 0; i < frames.size(); i++) {
                SrsAnnexbFrame frame = frames.get(i);
                flv_tag.size += frame.size;
            }
            flv_tag.frame = ByteBuffer.allocate(flv_tag.size);

            // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
            // Frame Type, Type of video frame.
            // CodecID, Codec Identifier.
            // set the rtmp header
            flv_tag.frame.put((byte)((frame_type << 4) | SrsCodecVideo.AVC));

            // AVCPacketType
            flv_tag.frame.put((byte)avc_packet_type);

            // CompositionTime
            // pts = dts + cts, or
            // cts = pts - dts.
            // where cts is the header in rtmp video packet payload header.
            int cts = pts - dts;
            flv_tag.frame.put((byte)(cts >> 16));
            flv_tag.frame.put((byte)(cts >> 8));
            flv_tag.frame.put((byte)cts);

            // h.264 raw data.
            for (int i = 0; i < frames.size(); i++) {
                SrsAnnexbFrame frame = frames.get(i);
                byte[] frame_bytes = new byte[frame.size];
                frame.frame.get(frame_bytes);
                flv_tag.frame.put(frame_bytes);
            }

            // reset the buffer.
            flv_tag.frame.rewind();

            //Log.i(TAG, String.format("flv tag muxed, %dB", flv_tag.size));
            //utils.srs_print_bytes(flv_tag.frame, 128);

            return flv_tag;
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
        private SrsUtils utils;
        private Handler handler;

        private SrsRawH264Stream avc;
        private byte[] h264_sps;
        private boolean h264_sps_changed;
        private byte[] h264_pps;
        private boolean h264_pps_changed;
        private boolean h264_sps_pps_sent;

        public SrsFlv() {
            utils = new SrsUtils();

            avc = new SrsRawH264Stream();
            h264_sps = new byte[0];
            h264_sps_changed = false;
            h264_pps = new byte[0];
            h264_pps_changed = false;
            h264_sps_pps_sent = false;
        }

        /**
         * set the handler to send message to work thread.
         * @param h the handler to send the message.
         */
        public void setHandler(Handler h) {
            handler = h;
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

            ArrayList<SrsAnnexbFrame> ibps = new ArrayList<SrsAnnexbFrame>();
            int frame_type = SrsCodecVideoAVCFrame.InterFrame;
            //Log.i(TAG, String.format("video %d/%d bytes, offset=%d, position=%d, pts=%d", bb.remaining(), bi.size, bi.offset, bb.position(), pts));

            // send each frame.
            while (bb.position() < bi.size) {
                SrsAnnexbFrame frame = avc.annexb_demux(bb, bi);

                // 5bits, 7.3.1 NAL unit syntax,
                // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
                //  7: SPS, 8: PPS, 5: I Frame, 1: P Frame
                int nal_unit_type = (int)(frame.frame.get(0) & 0x1f);
                if (nal_unit_type == SrsAvcNaluType.SPS || nal_unit_type == SrsAvcNaluType.PPS) {
                    Log.i(TAG, String.format("annexb demux %dB, pts=%d, frame=%dB, nalu=%d", bi.size, pts, frame.size, nal_unit_type));
                }

                // for IDR frame, the frame is keyframe.
                if (nal_unit_type == SrsAvcNaluType.IDR) {
                    frame_type = SrsCodecVideoAVCFrame.KeyFrame;
                }

                // ignore the nalu type aud(9)
                if (nal_unit_type == SrsAvcNaluType.AccessUnitDelimiter) {
                    continue;
                }

                // for sps
                if (avc.is_sps(frame)) {
                    byte[] sps = new byte[frame.size];
                    frame.frame.get(sps);

                    if (utils.srs_bytes_equals(h264_sps, sps)) {
                        continue;
                    }
                    h264_sps_changed = true;
                    h264_sps = sps;
                    continue;
                }

                // for pps
                if (avc.is_pps(frame)) {
                    byte[] pps = new byte[frame.size];
                    frame.frame.get(pps);

                    if (utils.srs_bytes_equals(h264_pps, pps)) {
                        continue;
                    }
                    h264_pps_changed = true;
                    h264_pps = pps;
                    continue;
                }

                // ibp frame.
                SrsAnnexbFrame nalu_header = avc.mux_ibp_frame(frame);
                ibps.add(nalu_header);
                ibps.add(frame);
            }

            write_h264_sps_pps(dts, pts);

            write_h264_ipb_frame(ibps, frame_type, dts, pts);
        }

        private void write_h264_sps_pps(int dts, int pts) {
            // when sps or pps changed, update the sequence header,
            // for the pps maybe not changed while sps changed.
            // so, we must check when each video ts message frame parsed.
            if (h264_sps_pps_sent && !h264_sps_changed && !h264_pps_changed) {
                return;
            }

            // when not got sps/pps, wait.
            if (h264_pps.length <= 0 || h264_sps.length <= 0) {
                return;
            }

            // h264 raw to h264 packet.
            ArrayList<SrsAnnexbFrame> frames = new ArrayList<SrsAnnexbFrame>();
            avc.mux_sequence_header(h264_sps, h264_pps, dts, pts, frames);

            // h264 packet to flv packet.
            int frame_type = SrsCodecVideoAVCFrame.KeyFrame;
            int avc_packet_type = SrsCodecVideoAVCType.SequenceHeader;
            SrsAnnexbFrame flv_tag = avc.mux_avc2flv(frames, frame_type, avc_packet_type, dts, pts);

            // the timestamp in rtmp message header is dts.
            int timestamp = dts;
            rtmp_write_packet(SrsCodecFlvTag.Video, timestamp, frame_type, flv_tag);

            // reset sps and pps.
            h264_sps_changed = false;
            h264_pps_changed = false;
            h264_sps_pps_sent = true;
            Log.i(TAG, String.format("flv: h264 sps/pps sent, sps=%dB, pps=%dB", h264_sps.length, h264_pps.length));
        }

        private void write_h264_ipb_frame(ArrayList<SrsAnnexbFrame> ibps, int frame_type, int dts, int pts) {
            // when sps or pps not sent, ignore the packet.
            // @see https://github.com/simple-rtmp-server/srs/issues/203
            if (!h264_sps_pps_sent) {
                return;
            }

            int avc_packet_type = SrsCodecVideoAVCType.NALU;
            SrsAnnexbFrame flv_tag = avc.mux_avc2flv(ibps, frame_type, avc_packet_type, dts, pts);

            if (frame_type == SrsCodecVideoAVCFrame.KeyFrame) {
                //Log.i(TAG, String.format("flv: keyframe %dB, dts=%d", flv_tag.size, dts));
            }

            // the timestamp in rtmp message header is dts.
            int timestamp = dts;
            rtmp_write_packet(SrsCodecFlvTag.Video, timestamp, frame_type, flv_tag);
        }

        private void rtmp_write_packet(int type, int dts, int frame_type, SrsAnnexbFrame tag) {
            SrsFlvFrame frame = new SrsFlvFrame();
            frame.tag = tag;
            frame.type = type;
            frame.dts = dts;
            frame.frame_type = frame_type;

            // use handler to send the message.
            // TODO: FIXME: we must wait for the handler to ready, for the sps/pps cannot be dropped.
            if (handler == null) {
                Log.w(TAG, "flv: drop frame for handler not ready.");
                return;
            }

            Message msg = Message.obtain();
            msg.what = SrsMessageType.FLV;
            msg.obj = frame;
            handler.sendMessage(msg);
            //Log.i(TAG, String.format("flv: enqueue frame type=%d, dts=%d, size=%dB", frame.type, frame.dts, frame.tag.size));
        }
    }
}
