package io.github.faizul.folder.core.dtos;

import java.util.List;

public record GoogleDriveFolderContentResponse(
    List<GoogleDriveItemResponse> items
) {}
