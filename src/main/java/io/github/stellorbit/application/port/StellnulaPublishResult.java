package io.github.stellorbit.application.port;

public record StellnulaPublishResult(
    boolean success,
    String releaseNo,
    Long version,
    Long revision,
    String releaseStatus,
    String checksum,
    String errorMessage) {}
