package com.worldsync.http;

public interface ProgressListener {
    void onProgress(long bytesTransferred, long totalBytes);
}
