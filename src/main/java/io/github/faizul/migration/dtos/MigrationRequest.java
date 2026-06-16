package io.github.faizul.migration.dtos;

import java.util.List;

public record MigrationRequest(
    List<String> fileIds,
    List<String> folderIds,
    String targetProvider,
    Long targetExternalAccountId,
    Long sourceExternalAccountId,
    boolean deleteSource
) {}

