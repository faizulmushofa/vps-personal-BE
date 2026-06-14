package io.github.faizul.migration.dtos;

import java.util.List;
import java.util.UUID;

public record MigrationRequest(
    List<UUID> fileIds,
    String targetProvider,
    Long targetExternalAccountId,
    boolean deleteSource
) {}
