package io.github.faizul.File.dtos;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

public record UserStorageSummary(
    Long userId,
    String username,
    String email,
    long usedBytes,
    long quotaBytes
) {}
