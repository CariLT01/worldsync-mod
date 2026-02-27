package com.worldsync.types;

public record ClientOperation(
        String path,
        String hash,
        ClientFileOperation operation
) {
}
