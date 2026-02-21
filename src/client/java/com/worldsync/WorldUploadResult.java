package com.worldsync;

import java.util.List;

public record WorldUploadResult(
        int gameId,
        List<String> errors
) {
}
