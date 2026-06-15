package io.github.stellorbit.api.dto;

public record RecoverRuleReleaseRequest(
    String operator, String recoveryNote, Boolean markFailedRecordsAsPublished) {}
