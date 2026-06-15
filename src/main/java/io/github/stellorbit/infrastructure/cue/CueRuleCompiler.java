package io.github.stellorbit.infrastructure.cue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class CueRuleCompiler {

  private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
      new TypeReference<>() {};

  private final CueCompilerProperties properties;
  private final CueSchemaRegistry cueSchemaRegistry;
  private final ObjectMapper objectMapper;

  public CueRuleCompiler(
      CueCompilerProperties properties,
      CueSchemaRegistry cueSchemaRegistry,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.cueSchemaRegistry = cueSchemaRegistry;
    this.objectMapper = objectMapper;
  }

  /** 通过CUE CLI完成Schema校验、默认值合并和JSON导出。 */
  public CueCompilationResult compile(
      GovernanceRuleEntity rule, Map<String, Object> baseRuleModel) {
    CueSchemaDefinition schema = cueSchemaRegistry.currentSchema(rule.getRuleType());
    List<String> warnings = new ArrayList<>();
    List<String> explain = new ArrayList<>();
    explain.add("CUE schema version: " + schema.schemaVersion());
    explain.add("CUE schema source: " + schema.schemaName());
    explain.add("Merge order: schema.cue + base-rule.json + user-rule.cue");
    validateCueSourceShape(rule);

    Path workDir = null;
    try {
      workDir = Files.createTempDirectory("stellorbit-cue-");
      Files.writeString(workDir.resolve("schema.cue"), schema.cueSchema(), StandardCharsets.UTF_8);
      Files.writeString(
          workDir.resolve("user-rule.cue"), rule.getCueSource(), StandardCharsets.UTF_8);
      Files.writeString(
          workDir.resolve("base-rule.json"),
          objectMapper.writeValueAsString(Map.of("rule", baseRuleModel)),
          StandardCharsets.UTF_8);
      String exportedJson = executeCueExport(workDir);
      Map<String, Object> normalized = objectMapper.readValue(exportedJson, MAP_TYPE);
      explain.add("CUE export completed and default values materialized");
      return new CueCompilationResult(schema.schemaVersion(), normalized, warnings, explain);
    } catch (JsonProcessingException exception) {
      throw new InvalidRuleRequestException("CUE编译结果JSON解析失败: " + exception.getMessage());
    } catch (IOException exception) {
      throw new InvalidRuleRequestException("CUE编译执行失败: " + exception.getMessage());
    } finally {
      deleteQuietly(workDir);
    }
  }

  private void validateCueSourceShape(GovernanceRuleEntity rule) {
    if (rule.getCueSource() == null || rule.getCueSource().isBlank()) {
      throw new InvalidRuleRequestException("CUE规则原文不能为空");
    }
    if (!rule.getCueSource().contains("rule:")) {
      throw new InvalidRuleRequestException("CUE规则必须定义顶层 rule 字段");
    }
  }

  private String executeCueExport(Path workDir) throws IOException {
    ProcessBuilder processBuilder =
        new ProcessBuilder(
            properties.getBinary(), "export", workDir.toString(), "-e", "rule", "--out", "json");
    Process process;
    try {
      process = processBuilder.start();
    } catch (IOException exception) {
      throw new InvalidRuleRequestException(
          "CUE CLI不可用，请安装cue并配置stellorbit.cue.binary: " + exception.getMessage());
    }
    boolean finished;
    try {
      finished =
          process.waitFor(
              Duration.ofMillis(properties.getTimeoutMillis()).toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new InvalidRuleRequestException("CUE编译被中断");
    }
    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    if (!finished) {
      process.destroyForcibly();
      throw new InvalidRuleRequestException("CUE编译超时");
    }
    if (process.exitValue() != 0) {
      throw new InvalidRuleRequestException("CUE Schema校验失败: " + stderr.trim());
    }
    if (stdout.isBlank()) {
      throw new InvalidRuleRequestException("CUE编译未产生JSON输出");
    }
    return stdout;
  }

  private void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try (var stream = Files.walk(path)) {
      stream.sorted((left, right) -> right.compareTo(left)).forEach(this::deletePathQuietly);
    } catch (IOException ignored) {
      // Best-effort cleanup.
    }
  }

  private void deletePathQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best-effort cleanup.
    }
  }
}
