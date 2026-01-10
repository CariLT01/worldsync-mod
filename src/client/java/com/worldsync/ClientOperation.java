package com.worldsync;

public record ClientOperation(
        String path,
        String hash,
        ClientFileOperation operation
) {
}
