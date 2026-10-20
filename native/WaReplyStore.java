package com.bolochat.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the live WhatsApp notifications (for their reply actions) and the
 * most recently parsed messages, so the plugin can answer even when a
 * message arrived while the WebView UI was not listening.
 */
public class WaReplyStore {

    public static class ParsedMessage {
        public String key;
        public String conversation;
        public String sender;
        public String text;
        public long timestamp;
        public boolean isGroup;
        public boolean canReply;
    }

    private static final int MAX_NOTIFICATIONS = 60;
    private static final int MAX_MESSAGES = 200;

    private static final Map<String, Notification> NOTIFICATIONS =
            new LinkedHashMap<String, Notification>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Notification> eldest) {
                    return size() > MAX_NOTIFICATIONS;
                }
            };

    private static final List<ParsedMessage> MESSAGES = new ArrayList<>();

    public static synchronized void put(String key, Notification notification, ParsedMessage parsed) {
        NOTIFICATIONS.put(key, notification);
        // Replace any earlier parse of this same notification (WhatsApp
        // updates a conversation's notification in place, same key).
        for (int i = MESSAGES.size() - 1; i >= 0; i--) {
            if (MESSAGES.get(i).key.equals(key)) MESSAGES.remove(i);
        }
        MESSAGES.add(parsed);
        while (MESSAGES.size() > MAX_MESSAGES) MESSAGES.remove(0);
    }

    public static synchronized void remove(String key) {
        NOTIFICATIONS.remove(key);
    }

    public static synchronized List<ParsedMessage> drainMessages() {
        List<ParsedMessage> out = new ArrayList<>(MESSAGES);
        MESSAGES.clear();
        return out;
    }

    public static Notification.Action findReplyAction(Notification notification) {
        if (notification == null || notification.actions == null) return null;
        for (Notification.Action action : notification.actions) {
            if (action != null && action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                return action;
            }
        }
        return null;
    }

    public static boolean sendReply(Context context, String key, String text) {
        Notification notification;
        synchronized (WaReplyStore.class) {
            notification = NOTIFICATIONS.get(key);
        }
        if (notification == null) return false;
        Notification.Action action = findReplyAction(notification);
        if (action == null || action.actionIntent == null) return false;
        RemoteInput[] inputs = action.getRemoteInputs();
        if (inputs == null || inputs.length == 0) return false;
        Bundle bundle = new Bundle();
        bundle.putCharSequence(inputs[0].getResultKey(), text);
        Intent intent = new Intent();
        RemoteInput.addResultsToIntent(inputs, intent, bundle);
        try {
            action.actionIntent.send(context, 0, intent);
            return true;
        } catch (PendingIntent.CanceledException e) {
            return false;
        }
    }
}
