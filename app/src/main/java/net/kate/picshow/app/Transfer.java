package net.kate.picshow.app;

import android.util.Log;

/**
 * Created by Stille on 03.11.2014.
 */
public class Transfer implements Runnable, PausedRunnable {
    private final static String TAG = "Transfer";
    private final CacheManager cacheManager;
    private boolean flagToPause;
    private Object monitor;

    public Transfer(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        flagToPause = false;
    }

    @Override
    public void run() {
        Thread.currentThread().setName(ShowFragment.TRANSFER);
        Log.d(ShowFragment.THREAD_TAG, Thread.currentThread().getName() + " starts");
        int count = 0;
        while (!cacheManager.diskFinished()) {
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
            Log.d(TAG, "start transfer " + count);
            try {
                cacheManager.putImageToMemory();
            } catch (InterruptedException ex) {
                return;
            }
            cacheManager.deleteFileFromDisk();
            count++;
        }

        Log.d(ShowFragment.THREAD_TAG, Thread.currentThread().getName() + " has finished");
    }

    @Override
    public void pause(boolean flag, Object monitor) {
        flagToPause = flag;
        this.monitor = monitor;
    }
}