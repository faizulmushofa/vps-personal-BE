package io.github.faizul.File.dtos;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

public record FileChunk(
    String fileId,
    int chunkIndex,
    int totalChunks,
    byte[] data
) {}
