package io.github.faizul.File.Dtos;

import java.util.UUID;

public record InitResponse(
         UUID id,
         String originalFileName
) {

}
