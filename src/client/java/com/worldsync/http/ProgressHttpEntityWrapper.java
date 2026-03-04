package com.worldsync.http;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;

import java.io.IOException;
import java.io.OutputStream;

public class ProgressHttpEntityWrapper extends HttpEntityWrapper {
    private final ProgressListener listener;

    public ProgressHttpEntityWrapper(HttpEntity entity, ProgressListener listener) {
        super(entity);
        this.listener = listener;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        super.writeTo(new CountingOutputStream(outstream, listener, getContentLength()));
    }
}
