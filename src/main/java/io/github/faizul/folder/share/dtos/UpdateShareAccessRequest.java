package io.github.faizul.folder.share.dtos;

public record UpdateShareAccessRequest(
    String permission, // "VIEW" or "EDIT"
    Boolean allowAnonymous
) {}
