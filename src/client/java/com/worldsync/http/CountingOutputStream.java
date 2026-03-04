package com.worldsync.http;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CountingOutputStream extends FilterOutputStream {
    private final ProgressListener listener;
    private final long totalBytes;
    private long bytesWritten = 0;

    public CountingOutputStream(OutputStream out, ProgressListener listener, long totalBytes) {
        super(out);
        this.listener = listener;
        this.totalBytes = totalBytes;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        bytesWritten += len;
        notifyListener();
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        bytesWritten++;
        notifyListener();
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onProgress(bytesWritten, totalBytes);
        }
    }
}
