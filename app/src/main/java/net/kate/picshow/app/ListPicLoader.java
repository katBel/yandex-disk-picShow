package net.kate.picshow.app;

import android.content.Context;
import android.os.Handler;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;
import com.yandex.disk.client.Credentials;
import com.yandex.disk.client.ListItem;
import com.yandex.disk.client.ListParsingHandler;
import com.yandex.disk.client.TransportClient;
import com.yandex.disk.client.exceptions.CancelledPropfindException;
import com.yandex.disk.client.exceptions.WebdavException;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Stille on 26.10.2014.
 */
public class ListPicLoader extends AsyncTaskLoader<List<ListItem>> {
    private static String TAG = "Loader";

    private Credentials credentials;
    private String dir;
    private Handler handler;

    private List<ListItem> fileItemList;
    private Exception exception;
    private boolean hasCancelled;

    private static final int ITEMS_PER_REQUEST = 20;

    public static boolean itemIsImage(ListItem item) {
        return item.getContentType().startsWith("image");
    }

    private static Collator collator = Collator.getInstance();
    static {
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
    }
    private final Comparator<ListItem> FILE_ITEM_COMPARATOR = new Comparator<ListItem>() {
        @Override
        public int compare(ListItem f1, ListItem f2) {
            if (f1.isCollection() && !f2.isCollection()) {
                return -1;
            } else if (f2.isCollection() && !f1.isCollection()) {
                return 1;
            } else {
                return collator.compare(f1.getDisplayName(), f2.getDisplayName());
            }
        }
    };


    public ListPicLoader(Context context, Credentials credentials, String dir) {
        super(context);
        Log.d(TAG, "Loader is being created");
        handler = new Handler();
        this.credentials = credentials;
        this.dir = dir;
    }

    @Override
    protected void onStartLoading() {
        forceLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();
        hasCancelled = true;
    }

    @Override
    public List<ListItem> loadInBackground() {
        Log.d(TAG, "Loader is starting loading");
        fileItemList = new ArrayList<ListItem>();
        hasCancelled = false;
        TransportClient client = null;
        try {
            client = TransportClient.getInstance(getContext(), credentials);
            client.getList(dir, ITEMS_PER_REQUEST, new ListParsingHandler() {

                // First item in PROPFIND is the current collection name
                boolean ignoreFirstItem = true;

                @Override
                public boolean hasCancelled() {
                    return hasCancelled;
                }

                @Override
                public void onPageFinished(int itemsOnPage) {
                    ignoreFirstItem = true;
                    handler.post(new Runnable() {
                        @Override
                        public void run () {
                            Collections.sort(fileItemList, FILE_ITEM_COMPARATOR);
                            deliverResult(new ArrayList<ListItem>(fileItemList));
                        }
                    });
                }

                @Override
                public boolean handleItem(ListItem item) {
                    if (ignoreFirstItem) {
                        ignoreFirstItem = false;
                        return false;
                    } else {
                        if (item.isCollection() || (itemIsImage(item))) {
                            fileItemList.add(item);
                            return true;
                        }
                        return false;
                    }
                }
            });
            Collections.sort(fileItemList, FILE_ITEM_COMPARATOR);
            exception = null;
        } catch (CancelledPropfindException ex) {
            return fileItemList;
        } catch (WebdavException ex) {
            Log.d(TAG, "loadInBackground", ex);
            exception = ex;
        } catch (IOException ex) {
            Log.d(TAG, "loadInBackground", ex);
            exception = ex;
        } finally {
            TransportClient.shutdown(client);
        }
        return fileItemList;
    }

    public Exception getException() {
        return exception;
    }
}
