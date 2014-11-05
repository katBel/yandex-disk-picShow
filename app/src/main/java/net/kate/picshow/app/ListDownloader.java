package net.kate.picshow.app;

import android.content.Context;
import android.util.Log;
import com.yandex.disk.client.Credentials;
import com.yandex.disk.client.ListItem;

/**
 * Created by Stille on 30.10.2014.
 */
public class ListDownloader implements Runnable, PausedRunnable {
    private static final String TAG = "ListDownloader";

    private CacheManager cacheManager;
    private ListManager listManager;

    private boolean flagToPause;
    private Object monitor;

    public ListDownloader(CacheManager cacheManager, ListManager listManager) {
        super();
        this.cacheManager = cacheManager;
        this.listManager = listManager;
        flagToPause = false;
    }


    public void run() {
        Thread.currentThread().setName(ShowFragment.LIST_DOWNLOADER);
        Log.d(ShowFragment.THREAD_TAG, Thread.currentThread().getName() + " starts");
        int currentPosition = 0;
        while (!listManager.listIsOpened() || (currentPosition < listManager.currentSize())) {
            while (flagToPause) {
                synchronized (monitor) {
                    try {
                        ShowFragment.ThreadInfo(Thread.currentThread().getName(), ShowFragment.MONITOR_NAME,
                                ShowFragment.WAIT_OP, "Pause");
                        monitor.wait();
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
            }
            try {
                ListItem item = listManager.readItem(currentPosition++);
                if (item == null) {
                    if (!listManager.listIsOpened()) {
                        Log.w(TAG, "null item");
                    }
                    return;
                }
                if (!item.isCollection()) {
                    cacheManager.putFile(item);
                }
            } catch (InterruptedException ex) {
                return;
            }
        }
        Log.d(TAG, "download is completed -> setAllLoad");
        cacheManager.setAllLoad();
        Log.d(ShowFragment.THREAD_TAG, Thread.currentThread().getName() + " has finished");
    }

    @Override
    public void pause(boolean flag, Object monitor) {
        flagToPause = flag;
        this.monitor = monitor;
    }
}