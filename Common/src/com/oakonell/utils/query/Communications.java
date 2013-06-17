package com.oakonell.utils.query;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.concurrent.GuardedBy;

import com.oakonell.utils.query.BackgroundQueryHelper.CommunicationObserver;

/**
 * A Utility class for handling messages between threads.
 */
public final class Communications {
    private static final Communications INSTANCE = new Communications();

    private int nextId = 1;
    private Map<String, CommunicationEntry> communications = new HashMap<String, CommunicationEntry>();

    private Communications() {
        // prevent instantiation
    }

    public static boolean delete(String communicationId) {
        CommunicationEntry entry = get(communicationId);
        if (entry == null) {
            return false;
        }
        entry.setState(State.COMPLETE);
        INSTANCE.communications.remove(communicationId);
        return true;
    }

    public static CommunicationEntry get(String communicationId) {
        return INSTANCE.communications.get(communicationId);
    }

    public static CommunicationEntry create() {
        CommunicationEntry entry = new CommunicationEntry(INSTANCE.nextId + "");
        INSTANCE.nextId++;
        INSTANCE.communications.put(entry.getId(), entry);
        return entry;
    }

    public static final class CommunicationEntry {
        private final String id;
        private final Collection<CommunicationObserver> observers = new ConcurrentLinkedQueue<CommunicationObserver>();

        @GuardedBy("this")
        private String message;
        @GuardedBy("this")
        private State state = State.WORKING;

        private CommunicationEntry(String id) {
            this.id = id;
        }

        public synchronized void setState(State complete) {
            state = complete;
            notifyObservers();
        }

        public synchronized void placeInError(String message) {
            state = State.ERROR;
            this.message = message;
            notifyObservers();
        }

        public synchronized void updateMessage(String string) {
            message = string;
            notifyObservers();
        }

        private void notifyObservers() {
            for (CommunicationObserver each : observers) {
                each.onChange();
            }
        }

        public synchronized State getStatus() {
            return state;
        }

        public synchronized String getMessage() {
            return message;
        }

        public String getId() {
            return id;
        }

        public void unregisterChangeListener(CommunicationObserver communicationObserver) {
            observers.remove(communicationObserver);
        }

        public void registerChangeListener(CommunicationObserver communicationObserver) {
            observers.add(communicationObserver);
        }
    }

    public static enum State {
        ERROR, COMPLETE, WORKING;
    }

}
