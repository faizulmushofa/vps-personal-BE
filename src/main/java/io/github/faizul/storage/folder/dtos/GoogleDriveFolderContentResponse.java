package io.github.faizul.storage.folder.dtos;

import java.util.List;

public record GoogleDriveFolderContentResponse(
    List<GoogleDriveItemResponse> items
) {}
