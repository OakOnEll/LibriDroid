package com.oakonell.libridroid.impl;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentResolver;
import android.os.Environment;

import com.oakonell.utils.LogHelper;

public final class FileHelper {
    private static final Pattern INVALID_CHAR_REPLACE_PATTERN = Pattern.compile("["
            + Pattern.quote("*[]{}:?^|\"&%:;'<>=+!\t`/") + "]");

    private FileHelper() {
        // prevent instantiation
    }

    public static File getFile(ContentResolver resolver, long bookId, long sectionNumber,
            boolean forWrite, String title, String librivoxId) {
        BookSection section = Book.readSection(resolver, Long.toString(bookId), sectionNumber);
        String url = section.getUrl();
        String fileNameUrl = url.substring(url.lastIndexOf('/') + 1);

        return getFile(librivoxId, title, fileNameUrl, sectionNumber, forWrite);
    }

    protected static File getFile(String librivoxId, String title, String fileNameUrl, long sectionNumber,
            boolean forWrite) {
        File dir = getBookDirectory(librivoxId, title);
        if (forWrite && !dir.exists() && !dir.mkdirs()) {
            LogHelper.error("DownloadService",
                    "Unable to create directories for " + dir.getAbsolutePath());
        }
        String fileSafeSectionName = escapeToSafeFilename(fileNameUrl);
        File file = new File(dir, sectionNumber + "_" + fileSafeSectionName);
        return file;
    }

    public static File getRootLibridroidDirectory() {
        // boolean mExternalStorageAvailable = false;
        boolean mExternalStorageWriteable = false;

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // We can read and write the media
            // mExternalStorageAvailable =
            mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // We can only read the media
            // mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            // Something else is wrong. It may be one of many other states, but
            // all we need
            // to know is we can neither read nor write
            // mExternalStorageAvailable = mExternalStorageWriteable = false;
        }
        if (!mExternalStorageWriteable) {
            throw new RuntimeException("External storage is not writable");
        }

        // set the path where we want to save the file
        // in this case, going to save it on the root directory of the
        // sd card.
        File sdCardRoot = Environment.getExternalStorageDirectory();
        // create a new file, specifying the path, and the filename
        // which we want to save the file as.
        String[] dirs = new String[] {
                "data", "libridroid"
        };
        File dir = sdCardRoot;
        for (String dirName : dirs) {
            dir = new File(dir, dirName);
        }
        return dir;
    }

    public static File getBookDirectory(Book book) {
        return getBookDirectory(book.getLibrivoxId(), book.getTitle());
    }

    @Deprecated
    public static File getBookDirectory(String librivoxId, String title) {
        File dir = getRootLibridroidDirectory();

        String filesafeTitleName = escapeToSafeFilename(title);
        return new File(dir, librivoxId + "_" + filesafeTitleName);
    }

    protected static String escapeToSafeFilename(String title) {
        if (title == null) {
            return "";
        }
        // http://mindprod.com/jgloss/filenames.html
        Matcher matcher = INVALID_CHAR_REPLACE_PATTERN.matcher(title);
        String result = matcher.replaceAll("_");
        return result;
    }

    public static long getDiskUsage(File directory) {
        File[] filelist = directory.listFiles();
        if (filelist == null) {
            return 0;
        }

        long foldersize = 0;
        for (File each : filelist) {
            if (each.isDirectory()) {
                foldersize += getDiskUsage(each);
            } else {
                foldersize += each.length();
            }
        }
        return foldersize;
    }

    public static void deleteFiles(File directory) {
        File[] filelist = directory.listFiles();
        if (filelist != null) {
            for (File each : filelist) {
                if (each.isDirectory()) {
                    deleteFiles(each);
                } else {
                    if (!each.delete()) {
                        LogHelper.error("FileHelper", "Unable to delete file " + each);
                    }

                }
            }
        }
        if (!directory.delete()) {
            LogHelper.error("FileHelper", "Unable to delete directory " + directory);
        }
    }
}
