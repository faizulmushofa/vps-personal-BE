package io.github.faizul.storage.folder.dtos;

import io.github.faizul.storage.file.dtos.FileResponse;
import java.util.List;

public record FolderContentResponse(
    List<FolderResponse> folders,
    List<FileResponse> files,
    String permission,
    Boolean allowAnonymous,
    String folderName
) {
    public FolderContentResponse(List<FolderResponse> folders, List<FileResponse> files) {
        this(folders, files, "VIEW", true, null);
    }
    public FolderContentResponse(List<FolderResponse> folders, List<FileResponse> files, String permission, Boolean allowAnonymous) {
        this(folders, files, permission, allowAnonymous, null);
    }
}
