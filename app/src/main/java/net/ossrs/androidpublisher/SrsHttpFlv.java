package net.ossrs.androidpublisher;

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
 */
public class SrsHttpFlv {
    private String url;
    private HttpURLConnection conn;
    private BufferedOutputStream bos;
    private Thread worker;
    private ByteBuffer queue;
    private long nb_queue;
    private static final String TAG = "SrsMuxer";

    /**
     * constructor.
     * @param u the http flv url to post to.
     */
    public SrsHttpFlv(String u) {
        url = u;
    }

    /**
     * connect to the remote SRS for remux.
     */
    public void connect() throws IOException {
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

    private void cycle() throws Exception {
        Log.i(TAG, String.format("muxer opened, url=%s", url));
        conn.setDoOutput(true);
        conn.setChunkedStreamingMode(0);
        bos = new BufferedOutputStream(conn.getOutputStream());

        while (!Thread.interrupted()) {
            Thread.sleep(1000, 0);
            Log.i(TAG, String.format("worker thread pump message, queue is %dB", nb_queue));
        }
    }

    /**
     * close the muxer, disconnect HTTP connection from SRS.
     */
    public void close() {
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
     * @param annexb the annexb bytes buffer.
     * @param size the size of annexb.
     * @param offset the start offset of annexb.
     * @param pts the pts, timestamp in ms, for flv tbn is 1000.
     */
    public void sendAnnexbFrame(ByteBuffer annexb, int size, int offset, long pts) {
        //Log.i(TAG, String.format("encoded frame %dB, offset=%d pts=%dms", size, offset, pts));
        nb_queue += size;
    }
}
