package com.oakonell.utils.query;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import android.content.Context;
import android.net.Uri;

import com.oakonell.utils.LogHelper;
import com.oakonell.utils.R;
import com.oakonell.utils.ResponseHandler;
import com.oakonell.utils.ServiceReadToDBBufferer;
import com.oakonell.utils.query.Communications.CommunicationEntry;

public abstract class AbstractAsyncQueryHelper implements ResponseHandler {
    private ServiceReadToDBBufferer reader;
    private String communicationId;
    private long startQueryTime;
    private String parseString;
    private String readString;
    private Context context;

    public AbstractAsyncQueryHelper(Context context, String communicationId, String parseString, String readString) {
        reader = new ServiceReadToDBBufferer(context, this);
        this.context = context;
        this.communicationId = communicationId;
        this.parseString = parseString;
        this.readString = readString;

    }

    public final void asyncQueryRequest(String queryText) {
        updateStartQueryProgress();
        startQueryTime = System.currentTimeMillis();
        reader.asyncQueryRequest(queryText, getQueryUri(queryText));
    }

    protected abstract String getQueryUri(String queryText);

    protected abstract int parseResponseEntity(HttpEntity entity, Uri uri) throws IOException;

    private void updateStartQueryProgress() {
        if (communicationId == null) {
            return;
        }
        CommunicationEntry entry = Communications.get(communicationId);
        entry.updateMessage(readString);
    }

    private void updateParsingResponseProgress() {
        if (communicationId == null) {
            return;
        }

        CommunicationEntry entry = Communications.get(communicationId);
        entry.updateMessage(parseString);
    }

    private void deleteProgress() {
        if (communicationId == null) {
            return;
        }
        Communications.delete(communicationId);
    }

    @Override
    public void requestError(IOException e) {
        if (communicationId != null) {
            CommunicationEntry entry = Communications.get(communicationId);
            entry.placeInError(context.getString(R.string.connectionProblem));
        }
        deleteProgress();
    }

    /**
     * Handles the response from the librivox server, which is in the form of an
     * xml doc.
     */
    @Override
    public final void handleResponse(HttpResponse response, Uri uri) {
        try {
            LogHelper.info("Libridroid.AsyncQuery",
                    "Read reasponse for " + uri.toString() + " in "
                            + (System.currentTimeMillis() - startQueryTime)
                            + " ms");
            int statusCode = response.getStatusLine().getStatusCode();
            if (HttpStatus.SC_OK != statusCode) {
                if (communicationId != null) {
                    CommunicationEntry entry = Communications.get(communicationId);
                    entry.placeInError(context.getString(R.string.httpError, statusCode));
                }
                deleteProgress();
            }
            updateParsingResponseProgress();
            long start = System.currentTimeMillis();
            int newCount = parseResponseEntity(response.getEntity(), uri);

            LogHelper.info("Libridroid.AsyncQuery",
                    "Parsed search for " + uri.toString() + " in "
                            + (System.currentTimeMillis() - start) + " ms, finding " + newCount + " new records");
            deleteProgress();
        } catch (IOException e) {
            requestError(e);
        }
    }
}
