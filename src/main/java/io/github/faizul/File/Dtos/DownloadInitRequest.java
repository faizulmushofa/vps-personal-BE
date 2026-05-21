package io.github.faizul.File.Dtos;

import java.util.UUID;

public record DownloadInitRequest(
    UUID fileId
) {}
