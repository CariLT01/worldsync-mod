package com.worldsync.types;

public record Operation(
        String path,
        String hash,
        FileOperation operation
) {
}
