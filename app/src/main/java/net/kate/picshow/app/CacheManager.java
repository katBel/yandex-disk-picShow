package net.kate.picshow.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import com.yandex.disk.client.Credentials;
import com.yandex.disk.client.DownloadListener;
import com.yandex.disk.client.ListItem;
import com.yandex.disk.client.TransportClient;
import com.yandex.disk.client.exceptions.WebdavException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;

/**
 * Created by Stille on 02.11.2014.
 */
public class CacheManager {
    private final Disk disk;
    private final Memory memory;
//    private TransferTask transferTask;

    public CacheManager(Context context, Credentials credentials, long maxDiskSize, long maxMemorySize,
                        int imageViewHeight, int imageViewWidth) {
        disk = new Disk(context, credentials,  maxDiskSize);
        memory = new Memory(maxMemorySize, imageViewHeight, imageViewWidth);
    }

    /*public void transferTaskExecute() {
        if (transferTask == null) {
            transferTask = new TransferTask();
            transferTask.execute();
        }
    }*/

    public boolean hasFinished() {
        return (disk.workIsFinished() && memory.workIsFinished());
    }

    public void setAllLoad() {
        disk.setAllLoad();
    }

    public void changeContext(Context newContext) {
        disk.changeContext(newContext);
    }

    public void putFile(ListItem item) throws InterruptedException{
        disk.putFile(item);
    }

    public Bitmap getImage() throws InterruptedException{
        return memory.getImage();
    }

    public void stopWork() {
/*        if (transferTask != null) {
            transferTask.cancel(false);*/
            disk.clean();
    }

/*    private class TransferTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "Transfer is going in background");
            int count = 0;
            while (!disk.workIsFinished()) {
                Log.d(TAG, "start transfer " + count);
                memory.putImage();
                disk.deleteFile();
                if (isCancelled()) {
                    return null;
                }
                count++;
            }
            return null;
        }
    }*/
    public void putImageToMemory() throws InterruptedException {
        memory.putImage();
    }

    public void deleteFileFromDisk() {
        disk.deleteFile();
    }

    public boolean diskFinished() {
        return disk.workIsFinished();
    }

    private class Disk {
        private static final String TAG = "DISK";

        private final long maxSize;
        private final File location;

        private final Credentials credentials;
        private Context context;

        private LinkedList<File> files;
        private long currentSize;
        private boolean allLoad, finished;


        public Disk(Context context, Credentials credentials, long maxDiskSize) {
            maxSize = maxDiskSize;
            this.context = context;
            this.credentials = credentials;
            files = new LinkedList<File>();
            finished = false;
            allLoad = false;
            location = new File(context.getCacheDir() + File.separator + "picShowCache");
            location.mkdir();
            location.setReadable(true, false);
            location.setWritable(true, false);
            currentSize = 0;
            Log.d(TAG, "File for cache: " + context.getCacheDir() + File.separator + "picShowCache");
        }

        public void clean() {
            location.delete();
        }

        public void changeContext(Context newContext) {
            context = newContext;
        }

        public void setAllLoad() {
            allLoad = true;
            if (files.isEmpty()) {
                finished = true;
                Log.d(TAG, "disk has finished its work /setAllLoad");
                synchronized (this) {
                    notifyAll();
                }
                ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG,
                        ShowFragment.NOTIFY_OP, "setAllLoad");
            }
        }

        public boolean workIsFinished() {
            return finished;
        }

        private boolean enoughSpace (long size) {
            return currentSize + size <= maxSize;
        }

        private void increaseCurrentSize(long size) {
            currentSize += size;
        }

        private void decreaseCurrentSize(long size) {
            currentSize -= size;
        }

        public void putFile(ListItem item) throws InterruptedException{
            Log.d(TAG, "Going to download file " + item.getFullPath());
            synchronized (this) {
                while (!enoughSpace(item.getContentLength())) {
                    try {
                        ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG, ShowFragment.WAIT_OP, "putFile");
                        this.wait();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                        throw ex;
                    }
                }
            }
//            increaseCurrentSize(item.getContentLength());
            Log.d(TAG, "start to download file of length = " + item.getContentLength() / 1024
                    + " currentCacheSize = " + currentSize / 1024 + " maxCacheSize = " + maxSize / 1024);
            final File file = new File(location, new File(item.getFullPath()).getName());
            file.setReadable(true, false);
            file.setWritable(true, false);
            TransportClient client = null;
            try {
                client = TransportClient.getInstance(context, credentials);
                client.download(item.getFullPath(),  new DownloadListener() {
                    @Override
                    public OutputStream getOutputStream(boolean append) throws IOException {
                        return new FileOutputStream(file);
                    }
                });
            } catch (IOException ex) {
                Log.d(TAG, "loadFile", ex);
            } catch (WebdavException ex) {
                Log.d(TAG, "loadFile", ex);
            } finally {
                if (client != null) {
                    client.shutdown();
                }
            }
            Log.d(TAG, "finish to download file -> notifyAll");
            files.add(file);
            increaseCurrentSize(file.getTotalSpace());
            synchronized (this) {
                notifyAll();
            }
            ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG,
                    ShowFragment.NOTIFY_OP, "putFile");
        }

        public String getFile() throws InterruptedException{
            Log.d(TAG, "Going to get file");
            synchronized (this) {
                while (files.isEmpty() && !workIsFinished()) {
                    try {
                        ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG, ShowFragment.WAIT_OP, "getFile");
                        this.wait();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                        throw ex;
                    }
                }
            }
            if (workIsFinished()) {
                return null;
            }
            Log.d(TAG, "File has been got");
            return files.getFirst().getAbsolutePath();
        }

        public void deleteFile() {
            File file = files.pollFirst();
            if (file != null) {
                decreaseCurrentSize(file.getTotalSpace());
                file.delete();
                Log.d(TAG, "file has been deleted -> notifyAll. CurrentCacheSize = " + currentSize);
                synchronized (this) {
                    notifyAll();
                }
                ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG,
                        ShowFragment.NOTIFY_OP, "deleteFile");
            }
            if (files.isEmpty() && allLoad) {
                finished = true;
                Log.d(TAG, "disk has finished its work /delete");
            }
        }
    }

    private class Memory {
        private final static String TAG = "MEMORY";

        private final long maxSize;
        private final int reqWidth;
        private final int reqHeight;
        private long currentSize;
        private LinkedList<Bitmap> images;
        private boolean finished;

        public Memory(long maxMemorySize, int imageViewHeight, int imageViewWidth) {
            maxSize = maxMemorySize;
            reqHeight = imageViewHeight;
            reqWidth = imageViewWidth;
            images = new LinkedList<Bitmap>();
            currentSize = 0;
            finished = false;
        }

        public boolean workIsFinished() {
            return finished;
        }

        private boolean enoughMemory(long bitmapSize) {
            return currentSize + bitmapSize <= maxSize;
        }

        private void increaseCurrentSize(long size) {
            currentSize += size;
        }

        private void decreaseCurrentSize(long size) {
            currentSize -= size;
        }

        private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
            // Raw height and width of image
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;
                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while ((halfHeight / inSampleSize) > reqHeight
                        || (halfWidth / inSampleSize) > reqWidth) {
                    inSampleSize *= 2;
                }
            }

            return inSampleSize;
        }

        public void putImage() throws InterruptedException{
            String filePath = disk.getFile();
            if (filePath == null) {
                return;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            //? options.inDensity
            long bitmapSize = 4 * options.outHeight * options.outWidth / (options.inSampleSize * options.inSampleSize);
            Log.d(TAG, "Going to put image " + filePath + " of size = " + bitmapSize
                    + ". CurrentMemorySize = " + currentSize + " maxMemorySize = " + maxSize);
            synchronized (this) {
                while (!enoughMemory(bitmapSize)) {
                    try {
                        ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG, ShowFragment.WAIT_OP, "putImage");
                        this.wait();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                        throw ex;
                    }
                }
            }
            images.add(BitmapFactory.decodeFile(filePath, options));
            increaseCurrentSize(images.getLast().getByteCount());
            Log.d(TAG, "Image has been put. CurrentMemorySize = " + currentSize + " -> notifyAll");
            synchronized (this) {
                notifyAll();
            }
            ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG,
                    ShowFragment.NOTIFY_OP, "putImage");
        }

        public Bitmap getImage() throws InterruptedException{
            Log.d(TAG, "Going to get image from memory. CurrentMemorySize = " + currentSize);
            synchronized (this) {
                while (images.isEmpty() && !disk.workIsFinished()) {
                    try {
                        ShowFragment.ThreadInfo(Thread.currentThread().getName(), TAG, ShowFragment.WAIT_OP, "getImage");
                        this.wait();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                        throw ex;
                    }
                }
            }
            Bitmap image = images.pollFirst();
            if (image != null) {
                decreaseCurrentSize(image.getByteCount());
            }
            Log.d(TAG, "Image has been polled. CurrentMemorySize = " + currentSize);
            if (disk.workIsFinished() && images.isEmpty()) {
                finished = true;
            }
            return image;
        }
    }
}
