package io.github.stellorbit.interfaces.http.dto;

public record RecoverRuleReleaseRequest(
    String operator, String recoveryNote, Boolean markFailedRecordsAsPublished) {}
