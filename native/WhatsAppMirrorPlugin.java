package com.bolochat.app;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.List;

/**
 * Capacitor bridge for the WhatsApp notification mirror.
 * Contract (web side): isAccessGranted, openAccessSettings,
 * getPendingMessages, sendReply; events whatsAppMessage / whatsAppRemoved.
 */
@CapacitorPlugin(name = "WhatsAppMirror")
public class WhatsAppMirrorPlugin extends Plugin {

    private BroadcastReceiver receiver;

    private static JSObject toJson(WaReplyStore.ParsedMessage m) {
        JSObject o = new JSObject();
        o.put("key", m.key);
        o.put("conversation", m.conversation);
        o.put("sender", m.sender);
        o.put("text", m.text);
        o.put("timestamp", m.timestamp);
        o.put("isGroup", m.isGroup);
        o.put("canReply", m.canReply);
        return o;
    }

    @Override
    public void load() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (WaNotificationListener.ACTION_MESSAGE.equals(intent.getAction())) {
                    JSObject o = new JSObject();
                    o.put("key", intent.getStringExtra("key"));
                    o.put("conversation", intent.getStringExtra("conversation"));
                    o.put("sender", intent.getStringExtra("sender"));
                    o.put("text", intent.getStringExtra("text"));
                    o.put("timestamp", intent.getLongExtra("timestamp", System.currentTimeMillis()));
                    o.put("isGroup", intent.getBooleanExtra("isGroup", false));
                    o.put("canReply", intent.getBooleanExtra("canReply", false));
                    notifyListeners("whatsAppMessage", o);
                } else if (WaNotificationListener.ACTION_REMOVED.equals(intent.getAction())) {
                    JSObject o = new JSObject();
                    o.put("key", intent.getStringExtra("key"));
                    notifyListeners("whatsAppRemoved", o);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(WaNotificationListener.ACTION_MESSAGE);
        filter.addAction(WaNotificationListener.ACTION_REMOVED);
        if (Build.VERSION.SDK_INT >= 33) {
            getContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(receiver, filter);
        }
    }

    @PluginMethod
    public void isAccessGranted(PluginCall call) {
        boolean granted = false;
        try {
            String flat = Settings.Secure.getString(
                    getContext().getContentResolver(), "enabled_notification_listeners");
            if (flat != null) {
                for (String name : flat.split(":")) {
                    ComponentName cn = ComponentName.unflattenFromString(name);
                    if (cn != null
                            && cn.getPackageName().equals(getContext().getPackageName())
                            && cn.getClassName().equals(WaNotificationListener.class.getName())) {
                        granted = true;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        JSObject o = new JSObject();
        o.put("granted", granted);
        call.resolve(o);
    }

    @PluginMethod
    public void openAccessSettings(PluginCall call) {
        Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void getPendingMessages(PluginCall call) {
        List<WaReplyStore.ParsedMessage> msgs = WaReplyStore.drainMessages();
        JSArray arr = new JSArray();
        for (WaReplyStore.ParsedMessage m : msgs) {
            arr.put(toJson(m));
        }
        JSObject o = new JSObject();
        o.put("messages", arr);
        call.resolve(o);
    }

    @PluginMethod
    public void sendReply(PluginCall call) {
        String key = call.getString("key");
        String text = call.getString("text");
        if (key == null || text == null || text.trim().isEmpty()) {
            call.reject("Invalid reply", "INVALID");
            return;
        }
        boolean ok = WaReplyStore.sendReply(getContext(), key, text);
        if (!ok) {
            call.reject("Notification is no longer available for reply", "NOT_REPLYABLE");
            return;
        }
        JSObject o = new JSObject();
        o.put("sent", true);
        call.resolve(o);
    }

    @Override
    protected void handleOnDestroy() {
        if (receiver != null) {
            try {
                getContext().unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            receiver = null;
        }
    }
}
