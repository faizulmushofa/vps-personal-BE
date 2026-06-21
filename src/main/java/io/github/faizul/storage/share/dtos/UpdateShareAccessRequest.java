package io.github.faizul.storage.share.dtos;

public record UpdateShareAccessRequest(
    String permission, // "VIEW" or "EDIT"
    Boolean allowAnonymous
) {}
