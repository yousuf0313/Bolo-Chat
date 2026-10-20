package com.bolochat.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(WhatsAppMirrorPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
