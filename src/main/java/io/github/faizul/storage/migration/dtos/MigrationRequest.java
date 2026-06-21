package io.github.faizul.storage.migration.dtos;

import java.util.List;

public record MigrationRequest(
    List<String> fileIds,
    List<String> folderIds,
    String targetProvider,
    Long targetExternalAccountId,
    Long sourceExternalAccountId,
    Boolean deleteSource
) {
    public Boolean deleteSource() {
        return deleteSource != null && deleteSource;
    }
}
