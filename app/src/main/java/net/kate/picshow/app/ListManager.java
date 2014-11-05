package net.kate.picshow.app;

import android.util.Log;
import com.yandex.disk.client.ListItem;

import java.util.ArrayList;

/**
 * Created by Stille on 30.10.2014.
 */
public class ListManager {
    private static final String TAG = "ListManager";

    private ArrayList<ListItem> itemList;
    private boolean isOpened;

    public ListManager(ArrayList<ListItem> itemList) {
        this.itemList = itemList;
        isOpened = false;
    }

    public ListItem readItem(int position) throws InterruptedException{
        synchronized (this) {
            while ((position >= itemList.size()) && !isOpened) {
                ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG,
                        ShowFragment.WAIT_OP, "readItem");
                this.wait();
            }
        }
        if (isOpened && (position >= itemList.size())) {
            return null;
        }
        Log.d(TAG, "item " + itemList.get(position).getName() + " is going to be downloaded");
        return itemList.get(position);
    }

    public synchronized void addItem(ListItem item) {
        itemList.add(item);
        notifyAll();
        ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG,
                ShowFragment.NOTIFY_OP, "addItem");
    }

    public boolean listIsOpened() {
        return isOpened;
    }

    public synchronized void setOpened() {
        isOpened = true;
        notifyAll();
        ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG,
                ShowFragment.NOTIFY_OP, "setOpened");
    }

    public int currentSize() {
        return itemList.size();
    }
}
