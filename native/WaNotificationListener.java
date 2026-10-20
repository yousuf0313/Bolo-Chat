package com.bolochat.app;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Listens ONLY to WhatsApp notifications (com.whatsapp / com.whatsapp.w4b),
 * parses the newest message out of each conversation notification, keeps
 * the notification for its reply action, and broadcasts the parsed message
 * to the app's Capacitor plugin.
 */
public class WaNotificationListener extends NotificationListenerService {

    public static final String ACTION_MESSAGE = "com.bolochat.app.WA_MESSAGE";
    public static final String ACTION_REMOVED = "com.bolochat.app.WA_REMOVED";

    private static final String[] WA_PACKAGES = {"com.whatsapp", "com.whatsapp.w4b"};

    private static boolean isWhatsApp(String pkg) {
        if (pkg == null) return false;
        for (String p : WA_PACKAGES) {
            if (p.equals(pkg)) return true;
        }
        return false;
    }

    private static String charSeq(Bundle b, String key) {
        CharSequence cs = b.getCharSequence(key);
        return cs == null ? null : cs.toString();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !isWhatsApp(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        Bundle extras = n.extras;

        // Skip group-summary / silent housekeeping notifications.
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        String conversation = charSeq(extras, Notification.EXTRA_CONVERSATION_TITLE);
        if (conversation == null) conversation = charSeq(extras, Notification.EXTRA_TITLE);
        if (conversation == null) return;

        String text = null;
        String sender = null;
        try {
            Parcelable[] msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (msgs != null && msgs.length > 0) {
                Bundle last = (Bundle) msgs[msgs.length - 1];
                text = charSeq(last, "text");
                sender = charSeq(last, "sender");
            }
        } catch (Exception ignored) {
        }
        if (text == null) {
            text = charSeq(extras, Notification.EXTRA_BIG_TEXT);
            if (text == null) text = charSeq(extras, Notification.EXTRA_TEXT);
        }
        if (text == null) return;

        boolean isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false);
        if (sender == null || sender.isEmpty()) sender = conversation;
        if (!isGroup) sender = conversation;

        WaReplyStore.ParsedMessage parsed = new WaReplyStore.ParsedMessage();
        parsed.key = sbn.getKey();
        parsed.conversation = conversation;
        parsed.sender = sender;
        parsed.text = text;
        parsed.timestamp = sbn.getPostTime();
        parsed.isGroup = isGroup;
        parsed.canReply = WaReplyStore.findReplyAction(n) != null;

        WaReplyStore.put(sbn.getKey(), n, parsed);

        Intent i = new Intent(ACTION_MESSAGE);
        i.setPackage(getPackageName());
        i.putExtra("key", parsed.key);
        i.putExtra("conversation", parsed.conversation);
        i.putExtra("sender", parsed.sender);
        i.putExtra("text", parsed.text);
        i.putExtra("timestamp", parsed.timestamp);
        i.putExtra("isGroup", parsed.isGroup);
        i.putExtra("canReply", parsed.canReply);
        sendBroadcast(i);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !isWhatsApp(sbn.getPackageName())) return;
        WaReplyStore.remove(sbn.getKey());
        Intent i = new Intent(ACTION_REMOVED);
        i.setPackage(getPackageName());
        i.putExtra("key", sbn.getKey());
        sendBroadcast(i);
    }
}
