package net.kate.picshow.app;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.widget.ImageView;
import android.widget.Toast;
import com.yandex.disk.client.Credentials;
import com.yandex.disk.client.ListItem;

import java.util.ArrayList;

/**
 * Created by Stille on 27.10.2014.
 */
public class ShowFragment extends Fragment {
    public final static int WAIT_OP = 0;
    public final static int NOTIFY_OP = 1;
    public final static String THREAD_TAG = "THREAD";
    public final static String LIST_OPENER = "LIST_OPENER";
    public final static String LIST_DOWNLOADER = "LIST_DOWNLOADER";
    public final static String TRANSFER = "TRANSFER";
    public final static String MONITOR_NAME = "MONITOR";

    private final static String TAG = "ShowFragment";
    private final static int MEMORY_PART = 8;
    private final static int MAX_CACHE_SIZE = 1024 * 1024 * 15;

    private final static int HANDLER_PAUSE = -1;
    private final static int IMAGE_IS_READY = 0;
    private final static int NEED_IMAGE = 1;
    //private final static int NEED_DELAY = 2;

    private ArrayList<ListItem> listToShow;
    private int interval;
    private Credentials credentials;
    private CacheManager cacheManager;
    private ImageView picView;

    private PausedThread listOpener, listDownloader, transfer;
    private Handler handler;
    private final Object monitor = new Object();

    private boolean paused;

    public static void ThreadInfo(String threadName, String monitorName, int operation, String comment) {
        switch (operation) {
            case WAIT_OP:
                Log.d(THREAD_TAG, threadName + " waits on " + monitorName + " /" + comment);
                break;
            case NOTIFY_OP:
                Log.d(THREAD_TAG, threadName + " notifies all, waiting on " + monitorName + " /" + comment);
                break;
        }
    }

    public ShowFragment(){
        super();
    }

    public void setListForShow(ArrayList<ListItem> listToAdd) {
        listToShow = listToAdd;
    }

    public void setInterval(int interval) {
        this.interval = interval * 1000;
    }

    private CacheManager createCacheManager() {
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        int height = metrics.heightPixels;
        int width = metrics.widthPixels;
        final long maxMemory = Runtime.getRuntime().maxMemory() / MEMORY_PART;

        return new CacheManager(getActivity(), credentials, MAX_CACHE_SIZE, maxMemory, height, width);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.show_frag_action_bar, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                stopWork();
                getFragmentManager().popBackStack();
                break;
            case R.id.show_pause:
                if (paused) {
                    item.setTitle("Pause");
                    Toast.makeText(getActivity(), "Resume show", Toast.LENGTH_SHORT).show();
                    resumeWork();
                } else {
                    item.setTitle("Resume");
                    Toast.makeText(getActivity(), "Pause show", Toast.LENGTH_SHORT).show();
                    pauseWork();
                }
                paused = !paused;
                break;
            case R.id.show_stop:
                stopWork();
                Toast.makeText(getActivity(), "Stop show", Toast.LENGTH_SHORT).show();
                getFragmentManager().popBackStack();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        paused = false;

        Log.d(THREAD_TAG, "UI-thread " + Thread.currentThread().getName());

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String username = preferences.getString(PicShowActivity.USERNAME, null);
        String token = preferences.getString(PicShowActivity.TOKEN, null);
        credentials = new Credentials(username, token);

        ListManager listManager = new ListManager(listToShow);
        cacheManager = createCacheManager();
        Log.d(TAG, "start threads of opening and downloading");
        listOpener = new PausedThread(new ListOpener(listManager, getActivity(), credentials));
        listOpener.setPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        listOpener.start();

        listDownloader = new PausedThread(new ListDownloader(cacheManager, listManager));
        listDownloader.setPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        listDownloader.start();

        transfer = new PausedThread(new Transfer(cacheManager));
        transfer.setPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        transfer.start();

        handler = new SetImageHandler();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "ON_ACTIVITY_CREATED!!!");
        setHasOptionsMenu(true);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.show_fragment, null);
        picView = (ImageView) view.findViewById(R.id.image_show);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
        picView.setImageBitmap(bitmap);
        picView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!paused) {
            resumeWork();
        }
    }

    private class SetImageHandler extends Handler{
        private Bitmap image;
        private boolean paused;

        public SetImageHandler() {
            image = null;
            paused = false;
        }

        public void handleMessage(Message msg) {
            Log.d(THREAD_TAG, "handleMessage in thread " + Thread.currentThread().getName());
            switch (msg.what) {
                case HANDLER_PAUSE:
                    Log.d(TAG, "handleMessage HANDLER_PAUSE");
                    break;
                case IMAGE_IS_READY:
                    Log.d(TAG, "handleMessage IMAGE_IS_READY");
                    break;
               /* case NEED_DELAY:
                    Log.d(TAG, "handleMessage NEED_DELAY");
                    break;*/
                case NEED_IMAGE:
                    Log.d(TAG, "handleMessage NEED_IMAGE");
                    break;
                default:
                    Log.d(TAG, "handleMessage ?");
            }

            switch (msg.what) {
                case HANDLER_PAUSE:
                    paused = true;
                    break;
                case IMAGE_IS_READY:
                    Log.d(TAG, "setting image to layout");
                    if (image != null) {
                        if (!paused) {
                            setImageToLayout(image);
                            image = null;
                        }
                    } else {
                        if (msg.obj != null) {
                            if (!paused) {
                                setImageToLayout((Bitmap) msg.obj);
                            } else {
                                image = (Bitmap) msg.obj;
                            }
                        } else {
                            Toast.makeText(getActivity(), "Slide show has finished", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    Log.d(TAG, "message: IMAGE_IS_READY -> DELAY -> NEED_IMAGE");
                    Message msg1 = handler.obtainMessage(NEED_IMAGE);
                    sendMessageDelayed(msg1, interval);
                    break;
                case NEED_IMAGE:
                    paused = false;
                    if (image != null) {
                        sendEmptyMessage(IMAGE_IS_READY);
                        return;
                    }
                    post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "try to getImage()");
                            ImageGetter imageGetter = new ImageGetter();
                            imageGetter.setPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                            imageGetter.start();
                        }
                    });
                    break;
                /*case NEED_DELAY:
                    post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                TimeUnit.MILLISECONDS.sleep(interval);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            Log.d(TAG, "message: NEED_DELAY -> NEED_IMAGE");
                            sendEmptyMessage(NEED_IMAGE);
                        }
                    });
                    break;*/
                default:
                    super.handleMessage(msg);
            }
        }
    }

    public void setImageToLayout(Bitmap picture) {
        picView.setImageBitmap(picture);
    }

    private void stopWork() {
        synchronized (monitor) {
            monitor.notifyAll();
        }
        ShowFragment.ThreadInfo(Thread.currentThread().getName(), MONITOR_NAME,
                ShowFragment.NOTIFY_OP, "onDestroy");
        handler.removeMessages(IMAGE_IS_READY);
        handler.removeMessages(NEED_IMAGE);
        listOpener.interrupt();
        listDownloader.interrupt();
        transfer.interrupt();
        cacheManager.stopWork();
    }

    private void pauseWork() {
        listDownloader.pause(true, monitor);
        listOpener.pause(true, monitor);
        transfer.pause(true, monitor);
        Log.d(TAG, "message: pauseWork -> HANDLER_PAUSE");
        handler.removeMessages(NEED_IMAGE);
        handler.sendEmptyMessage(HANDLER_PAUSE);
    }

    private void resumeWork() {
        listDownloader.pause(false, null);
        listOpener.pause(false, null);
        transfer.pause(false, null);
        synchronized (monitor) {
            monitor.notifyAll();
        }
        ShowFragment.ThreadInfo(Thread.currentThread().getName(), MONITOR_NAME,
                ShowFragment.NOTIFY_OP, "resumeWork");
        Log.d(TAG, "message: resumeWork -> NEED_IMAGE");
        handler.sendEmptyMessage(NEED_IMAGE);
    }

    public void onPause() {
        Log.d(TAG, "ShowFragment.onPause()");
        pauseWork();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "ShowFragment.onDestroy()");
        stopWork();
        super.onDestroy();
    }

    private class PausedThread extends Thread{
        private PausedRunnable runner;

        public PausedThread(PausedRunnable runner) {
            super((Runnable) runner);
            this.runner = runner;
        }

        public void pause(boolean flag, Object monitor){
            runner.pause(flag, monitor);
        }
    }

    private class ImageGetter extends Thread {
        @Override
        public void run() {
            Log.d(THREAD_TAG, "ImageGetter thread - " + Thread.currentThread().getName());
            Bitmap image;
            try {
                image = cacheManager.getImage();
            } catch (InterruptedException ex) {
                return;
            }
            Message msg1 = handler.obtainMessage(IMAGE_IS_READY, image);
            Log.d(TAG, "Image has been got");
            Log.d(TAG, "message: imageGetter -> IMAGE_IS_READY");
            handler.sendMessage(msg1);
        }
    }
}
