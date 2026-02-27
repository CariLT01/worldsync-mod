package com.worldsync.types;

public record CompressionResult(boolean isCompressed, byte[] processedData) {
}
