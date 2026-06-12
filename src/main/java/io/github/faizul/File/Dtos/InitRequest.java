package io.github.faizul.File.dtos;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

public record InitRequest(
        String fileName,
        Long totalSize,
        String provider,
        Long externalAccountId
) {
}
