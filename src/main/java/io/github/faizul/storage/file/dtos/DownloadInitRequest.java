package io.github.faizul.storage.file.dtos;

import io.github.faizul.storage.file.dtos.*;
import io.github.faizul.storage.file.model.*;

import java.util.UUID;

public record DownloadInitRequest(
    UUID fileId
) {}
