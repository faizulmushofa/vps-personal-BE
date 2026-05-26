package io.github.faizul.File.dtos;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

import java.util.UUID;

public record DownloadInitRequest(
    UUID fileId
) {}
