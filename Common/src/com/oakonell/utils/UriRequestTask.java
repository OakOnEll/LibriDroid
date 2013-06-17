package com.oakonell.utils;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.net.Uri;

/**
 * Provides a runnable that uses an HttpClient to asynchronously load a given
 * URI. After the network content is loaded, the task delegates handling of the
 * request to a ResponseHandler specialized to handle the given content.
 */
public class UriRequestTask implements Runnable {
    private HttpUriRequest mRequest;
    private ResponseHandler mHandler;

    // private Context mAppContext;

    private ServiceReadToDBBufferer mSiteProvider;
    private String mRequestTag;

    public UriRequestTask(String requestTag,
            ServiceReadToDBBufferer siteProvider, HttpUriRequest request,
            ResponseHandler handler, Context appContext) {
        mRequestTag = requestTag;
        mSiteProvider = siteProvider;
        mRequest = request;
        mHandler = handler;
        // mAppContext = appContext;
    }

    /**
     * Carries out the request on the complete URI as indicated by the protocol,
     * host, and port contained in the configuration, and the URI supplied to
     * the constructor.
     */
    @Override
    public void run() {
        HttpResponse response;

        try {
            response = execute();
            mHandler.handleResponse(response, getUri());
        } catch (IOException e) {
            mHandler.requestError(e);
            LogHelper.warn("UriRequestTask", "exception processing asynch request", e);
        } finally {
            if (mSiteProvider != null) {
                mSiteProvider.requestComplete(mRequestTag);
            }
        }
    }

    private HttpResponse execute() throws IOException {
        HttpClient client = new DefaultHttpClient();
        return client.execute(mRequest);
    }

    public Uri getUri() {
        return Uri.parse(mRequest.getURI().toString());
    }
}
