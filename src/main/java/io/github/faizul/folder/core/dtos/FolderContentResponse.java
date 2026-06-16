package io.github.faizul.folder.core.dtos;

import io.github.faizul.File.dtos.FileResponse;
import java.util.List;

public record FolderContentResponse(
    List<FolderResponse> folders,
    List<FileResponse> files,
    String permission,
    Boolean allowAnonymous
) {
    public FolderContentResponse(List<FolderResponse> folders, List<FileResponse> files) {
        this(folders, files, "VIEW", true);
    }
}
