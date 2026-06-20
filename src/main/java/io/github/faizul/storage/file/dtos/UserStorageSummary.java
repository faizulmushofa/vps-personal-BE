package io.github.faizul.storage.file.dtos;

import io.github.faizul.storage.file.dtos.*;
import io.github.faizul.storage.file.model.*;

public record UserStorageSummary(
    Long userId,
    String username,
    String email,
    long usedBytes,
    long quotaBytes
) {}
