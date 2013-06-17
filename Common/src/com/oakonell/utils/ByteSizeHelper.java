package com.oakonell.utils;

import java.util.Formatter;

public final class ByteSizeHelper {
    private static final int BYTES_SCALING_FACTOR = 1024;

    private ByteSizeHelper() {
        // prevent instantiation
    }

    public static String getDisplayable(long numBytes) {
        if (numBytes < BYTES_SCALING_FACTOR) {
            return numBytes + " bytes";
        }
        String label;
        double size = ((double) numBytes) / BYTES_SCALING_FACTOR;
        if (size < BYTES_SCALING_FACTOR) {
            label = "Kb";
        } else {
            size = size / BYTES_SCALING_FACTOR;
            if (size < BYTES_SCALING_FACTOR) {
                label = "Mb";
            } else {
                size = size / BYTES_SCALING_FACTOR;
                label = "Gb";
            }
        }

        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb);

        formatter.format("%.2f %s", size, label);
        return sb.toString();
    }
}
