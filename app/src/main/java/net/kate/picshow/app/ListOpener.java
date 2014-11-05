package net.kate.picshow.app;

import android.content.Context;
import android.util.Log;
import com.yandex.disk.client.Credentials;
import com.yandex.disk.client.ListItem;
import com.yandex.disk.client.ListParsingHandler;
import com.yandex.disk.client.TransportClient;
import com.yandex.disk.client.exceptions.CancelledPropfindException;
import com.yandex.disk.client.exceptions.WebdavException;

import java.io.IOException;

/**
 * Created by Stille on 30.10.2014.
 */

public class ListOpener implements Runnable, PausedRunnable {
    private Credentials credentials;
    private static final String TAG = "ListOpener";
    private ListManager listManager;
    Context context;
    private boolean flagToPause;
    private Object monitor;

    public ListOpener(ListManager listManager, Context context, Credentials credentials) {
        this.listManager = listManager;
        this.context = context;
        this.credentials = credentials;
        flagToPause = false;
    }
    public void run() {
        Thread.currentThread().setName(ShowFragment.LIST_OPENER);
        int position = 0;
        Log.d(ShowFragment.THREAD_TAG, Thread.currentThread().getName() + " starts");

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

        while (position < listManager.currentSize()) {
            ListItem item;
            try {
                item = listManager.readItem(position++);
            } catch (InterruptedException ex) {
                return;
            }
            if (item.isCollection()) {
                TransportClient client = null;
                try {
                    client = TransportClient.getInstance(context, credentials);
                    client.getList(item.getFullPath(), new ListParsingHandler() {

                        boolean ignoreFirstItem = true;

                        @Override
                        public boolean handleItem(ListItem item) {
                            if (ignoreFirstItem) {
                                ignoreFirstItem = false;
                                return false;
                            } else {
                                if (item.isCollection() || (ListPicLoader.itemIsImage(item))) {
                                    listManager.addItem(item);
                                    Log.d(TAG, "item " + item.getName() + " has been added");
                                    return true;
                                }
                                return false;
                            }
                        }
                    });
                } catch (CancelledPropfindException ex) {
                    Log.d(TAG, ex.toString());
                } catch (WebdavException ex) {
                    Log.d(TAG, ex.toString());
                } catch (IOException ex) {
                    Log.d(TAG, ex.toString());
                } finally {
                    TransportClient.shutdown(client);
                }
            }
        }
        listManager.setOpened();
        Log.d(TAG, "finish list's opening");
        Log.d(ShowFragment.THREAD_TAG, Thread.currentThread().getName() + " has finished");
    }

    @Override
    public void pause(boolean flag, Object monitor) {
        flagToPause = flag;
        this.monitor = monitor;
    }
}