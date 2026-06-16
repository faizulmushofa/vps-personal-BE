package io.github.faizul.migration.dtos;

import java.util.List;
import java.util.UUID;

public record MigrationRequest(
    List<UUID> fileIds,
    List<String> folderIds,
    String targetProvider,
    Long targetExternalAccountId,
    boolean deleteSource
) {}
