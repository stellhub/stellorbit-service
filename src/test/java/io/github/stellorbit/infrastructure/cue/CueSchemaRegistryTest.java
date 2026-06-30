package io.github.stellorbit.infrastructure.cue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.stellorbit.infrastructure.persistence.repository.CueSchemaVersionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CueSchemaRegistryTest {

  @Mock private CueSchemaVersionRepository cueSchemaVersionRepository;

  @Test
  void shouldUseTypedRateLimitConfigBlocksInBuiltinSchema() {
    when(cueSchemaVersionRepository.findFirstByRuleTypeAndStatusOrderByCreatedAtDesc(
            "RATE_LIMIT", "ACTIVE"))
        .thenReturn(Optional.empty());

    CueSchemaDefinition schemaDefinition =
        new CueSchemaRegistry(cueSchemaVersionRepository).currentSchema("RATE_LIMIT");

    assertThat(schemaDefinition.cueSchema())
        .contains(
            "keyExtractor: #KeyExtractor",
            "quotaConfig: #QuotaConfig",
            "windowConfig: #WindowConfig",
            "concurrencyConfig: #ConcurrencyConfig",
            "hotspotConfig: #HotspotConfig",
            "customPolicy: #CustomPolicy",
            "modelLimitConfig: #ModelLimitConfig")
        .doesNotContain(
            "quotaConfig: #Object | #QuotaConfig",
            "windowConfig: #Object | #WindowConfig",
            "concurrencyConfig: *{} | #ConcurrencyConfig",
            "hotspotConfig: *{} | #HotspotConfig",
            "customPolicy: *{} | #CustomPolicy",
            "modelLimitConfig: *{} | #ModelLimitConfig");
  }
}
