package com.oakonell.utils.query;

import java.util.Map;

/**
 * An interface to allow unit tests to plug in a different data retrieval
 * mechanism
 * 
 */
public interface BufferedAsyncQueryHelper {

    void asyncQueryRequest(String queryText, Map<String, String> extraInputs);

}
