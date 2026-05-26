package io.github.faizul.File.dtos;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

public record UserStorageResponse(
    long usedBytes,
    long quotaBytes
) {}
