package com.oakonell.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;

import android.content.Context;

public class ServiceReadToDBBufferer {
    private static final Map<String, UriRequestTask> REQUESTS_IN_PROGRESS = new HashMap<String, UriRequestTask>();
    private final Context context;
    private final ResponseHandler handler;

    public ServiceReadToDBBufferer(Context context, ResponseHandler handler) {
        this.context = context;
        this.handler = handler;
    }

    private UriRequestTask getRequestTask(String queryText) {
        return REQUESTS_IN_PROGRESS.get(queryText);
    }

    public void requestComplete(String mQueryText) {
        synchronized (REQUESTS_IN_PROGRESS) {
            REQUESTS_IN_PROGRESS.remove(mQueryText);
        }
    }

    /**
     * Creates a new worker thread to carry out a RESTful network invocation.
     * 
     * @param queryTag
     *            unique tag that identifies this request.
     * 
     * @param queryUri
     *            the complete URI that should be access by this request.
     */
    public void asyncQueryRequest(String queryTag, String queryUri) {
        synchronized (REQUESTS_IN_PROGRESS) {
            UriRequestTask requestTask = getRequestTask(queryTag);
            if (requestTask == null) {
                requestTask = newQueryTask(queryTag, queryUri);
                Thread t = new Thread(requestTask);
                // allows other requests to run in parallel.
                t.start();
            }
        }
    }

    private UriRequestTask newQueryTask(String requestTag, String url) {
        UriRequestTask requestTask;

        final HttpGet get = new HttpGet(url);
        requestTask = new UriRequestTask(requestTag, this, get,
                handler, context);

        REQUESTS_IN_PROGRESS.put(requestTag, requestTask);
        return requestTask;
    }

    public static String encode(String gDataQuery) {
        try {
            return URLEncoder.encode(gDataQuery, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            LogHelper.debug("ServiceReadToDBBufferer", "could not decode UTF-8," +
                    " this should not happen");
        }
        return null;
    }

}
