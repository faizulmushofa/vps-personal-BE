package io.github.faizul.storage.file.dtos;

import io.github.faizul.storage.file.dtos.*;
import io.github.faizul.storage.file.model.*;

public record FileChunk(
    String fileId,
    int chunkIndex,
    int totalChunks,
    byte[] data
) {}
