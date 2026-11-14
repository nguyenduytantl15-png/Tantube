package com.tan.tantube;

import android.content.Context;
import android.view.View;
import android.webkit.WebView;

public class BgWebView extends WebView {
    public BgWebView(Context context) {
        super(context);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(View.VISIBLE);
    }
}
