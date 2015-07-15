package it.jaschke.alexandria;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.client.android.camera.CameraConfigurationUtils;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.EAN13Reader;

import java.util.concurrent.CountDownLatch;

import it.jaschke.alexandria.camera.CameraPreview;


public class ScanBook extends ActionBarActivity implements Camera.AutoFocusCallback{

    private static final String TAG = ScanBook.class.getSimpleName();

    private Camera mCamera;
    private CameraPreview mPreview;
    private LooperThread mThread;
    private Point screenResolution;
    private Point cameraResolution;
    private PreviewHandler previewHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_book);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Create an instance of Camera
        mCamera = getCameraInstance();
        if (mCamera != null) {

            // Create our Preview view and set it as the content of our activity.
            mPreview = new CameraPreview(this, mCamera);
            initFromCameraParameters(mCamera);

            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(mPreview);

            mThread = new LooperThread(this, cameraResolution);
            mThread.start();

            mPreview.requestPreviewFrame(new PreviewCallback(mThread.getHandler()));
            previewHandler = new PreviewHandler(mThread.getHandler());
        }
    }

    @Override
    protected void onPause() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }

        super.onPause();
    }

    protected PreviewHandler getPreviewHandler() {
        return previewHandler;
    }

    /**
     * Reads, one time, values from the camera that are needed by the app.
     */
    void initFromCameraParameters(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        Point theScreenResolution = new Point();
        display.getSize(theScreenResolution);
        screenResolution = theScreenResolution;
        Log.i(TAG, "Screen resolution: " + screenResolution);
        cameraResolution = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, screenResolution);
        Log.i(TAG, "Camera resolution: " + cameraResolution);
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        Log.d(TAG, "onAutoFocus()");
    }

    class PreviewHandler extends Handler {
        private Handler mDecodeHandler;

        PreviewHandler(Handler decodeHandler) {
            mDecodeHandler = decodeHandler;
        }

        public void handleMessage(Message msg) {
            mPreview.requestPreviewFrame(new PreviewCallback(mDecodeHandler));
        }
    }

    class PreviewCallback implements Camera.PreviewCallback {
        private Handler mHandler;

        public PreviewCallback(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Log.d(TAG, "onPreviewFrame()");
            Message message = mHandler.obtainMessage(1, data);
            message.sendToTarget();
        }
    }

    class LooperThread extends Thread {
        private Handler mHandler;
        private ScanBook activity;
        private final EAN13Reader ean13Reader;
        private final CountDownLatch handlerInitLatch;

        private Point resolution;

        public LooperThread(ScanBook a, Point resolution/*, PreviewHandler previewHandler*/) {
//            super();

            handlerInitLatch = new CountDownLatch(1);
            this.resolution = resolution;
            ean13Reader = new EAN13Reader();
            activity = a;
//            mPreviewHandler = previewHandler;
        }

        public void run() {
            Looper.prepare();

            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    long start = System.currentTimeMillis();
                    Result rawResult = null;

                    // process incoming messages here
                    byte[] data = (byte[]) msg.obj;

                    PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, resolution.x, resolution.y, 0, 0,
                            resolution.x, resolution.y, false);
                    if (source != null) {
                        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                        try {
                            rawResult = ean13Reader.decode(bitmap);
                        } catch (ReaderException re) {
                            // continue
                        } finally {
                            ean13Reader.reset();
                        }
                    }

                    if (rawResult != null) {
                        long end = System.currentTimeMillis();
                        Log.d(TAG, "Found barcode in " + (end - start) + " ms");
                    } else {
                        Log.d(TAG, "No barcode found. Need to re check");
                        PreviewHandler h = activity.getPreviewHandler();
                        Message req = h.obtainMessage();
                        req.sendToTarget();
                    }
                }
            };

            handlerInitLatch.countDown();
            Looper.loop();
        }

        Handler getHandler() {
            try {
                handlerInitLatch.await();
            } catch (InterruptedException ie) {
                // continue?
            }
            return mHandler;
        }
    }
}
