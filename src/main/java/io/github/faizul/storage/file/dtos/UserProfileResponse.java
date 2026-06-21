package io.github.faizul.storage.file.dtos;

import io.github.faizul.storage.file.dtos.*;
import io.github.faizul.storage.file.model.*;

public record UserProfileResponse(
    Long id,
    String username,
    String email
) {}
