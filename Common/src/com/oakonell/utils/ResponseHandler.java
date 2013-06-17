package com.oakonell.utils;

import java.io.IOException;

import org.apache.http.HttpResponse;

import android.net.Uri;

/**
 * Enables custom handling of HttpResponse and the entities they contain.
 */
public interface ResponseHandler {
    void handleResponse(HttpResponse response, Uri uri)
            throws IOException;

    void requestError(IOException e);
}
