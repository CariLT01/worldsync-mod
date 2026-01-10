package com.worldsync;

public record Operation(
        String path,
        String hash,
        FileOperation operation
) {
}
