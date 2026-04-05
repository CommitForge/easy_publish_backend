package com.easypublish.erp.service;

import com.easypublish.erp.entities.ErpIntegration;
import com.easypublish.erp.entities.ErpRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ErpCliService {

    private static final Pattern TX_DIGEST_PATTERN = Pattern.compile("0x[a-fA-F0-9]{64}");
    private static final Pattern ABSOLUTE_WINDOWS_PATH_PATTERN = Pattern.compile("^[a-zA-Z]:\\\\.*");
    private static final String REDACTED = "[REDACTED]";
    private static final String REDACTED_PRIVATE_KEY = "[REDACTED_PRIVATE_KEY]";
    private static final Set<String> SENSITIVE_VALUE_FLAGS = new HashSet<>(Set.of(
            "--private-key",
            "--content",
            "--name",
            "--description",
            "--external-id",
            "--recipients",
            "--references",
            "--container-id",
            "--data-type-id"
    ));

    @Value("${app.erp.cli.binary:node}")
    private String defaultCliBinary;

    @Value("${app.erp.cli.script:}")
    private String defaultCliScript;

    @Value("${app.erp.cli.working-directory:.}")
    private String defaultCliWorkingDirectory;

    @Value("${app.erp.cli.default-network:mainnet}")
    private String defaultCliNetwork;

    @Value("${app.erp.cli.private-key-env-var:IOTA_PRIVATE_KEY}")
    private String defaultCliPrivateKeyEnvVar;

    @Value("${app.erp.cli.timeout-ms:180000}")
    private long cliTimeoutMs;

    public record CliExecutionResult(
            String command,
            int exitCode,
            String stdout,
            String stderr,
            String txDigest,
            Instant finishedAt
    ) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public CliExecutionResult runPublishDataItem(
            ErpIntegration integration,
            ErpRecord record,
            String containerId,
            String dataTypeId,
            String content,
            boolean dryRun
    ) {
        String binary = choose(integration.getCliBinary(), defaultCliBinary);
        String script = choose(integration.getCliScript(), defaultCliScript);
        if (script == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CLI script path is missing. Set integration.cliScript or app.erp.cli.script"
            );
        }

        String network = choose(integration.getCliNetwork(), defaultCliNetwork);
        String privateKeyEnv = choose(integration.getCliPrivateKeyEnvVar(), defaultCliPrivateKeyEnvVar);
        String privateKey = privateKeyEnv == null ? null : System.getenv(privateKeyEnv);
        String normalizedPrivateKey = ErpSecurityService.normalize(privateKey);
        String workingDir = choose(integration.getCliWorkingDirectory(), defaultCliWorkingDirectory);

        List<String> command = new ArrayList<>();
        command.add(binary);
        command.add(script);
        command.add("publish-data-item");
        command.add("--container-id");
        command.add(containerId);
        command.add("--data-type-id");
        command.add(dataTypeId);
        command.add("--name");
        command.add(choose(record.getRecordName(), "ERP Record " + record.getId()));
        command.add("--description");
        command.add(choose(record.getRecordDescription(), "ERP synced record"));
        command.add("--content");
        command.add(content);

        String externalId = ErpSecurityService.normalize(record.getExternalRecordId());
        if (externalId != null) {
            command.add("--external-id");
            command.add(externalId);
        }

        String recipients = ErpSecurityService.normalize(record.getRecipientsCsv());
        if (recipients != null) {
            command.add("--recipients");
            command.add(recipients);
        }

        String references = ErpSecurityService.normalize(record.getReferencesCsv());
        if (references != null) {
            command.add("--references");
            command.add(references);
        }

        if (network != null) {
            command.add("--network");
            command.add(network);
        }

        command.add("--compact");

        if (!dryRun) {
            if (normalizedPrivateKey != null) {
                command.add("--private-key");
                command.add(normalizedPrivateKey);
            }
        } else {
            command.clear();
            command.add(binary);
            command.add(script);
            command.add("publish-data-item");
            command.add("--help");
        }

        return runCommand(
                command,
                workingDir,
                Map.of(),
                sanitizeCommandForAudit(command, normalizedPrivateKey),
                normalizedPrivateKey
        );
    }

    public CliExecutionResult runDiagnostics(ErpIntegration integration) {
        String binary = choose(integration.getCliBinary(), defaultCliBinary);
        String script = choose(integration.getCliScript(), defaultCliScript);
        if (script == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CLI script path is missing. Set integration.cliScript or app.erp.cli.script"
            );
        }
        String workingDir = choose(integration.getCliWorkingDirectory(), defaultCliWorkingDirectory);
        List<String> command = List.of(binary, script, "--help");
        return runCommand(command, workingDir, Map.of(), sanitizeCommandForAudit(command, null), null);
    }

    private CliExecutionResult runCommand(
            List<String> command,
            String workingDirectory,
            Map<String, String> envOverrides,
            String commandForAudit,
            String sensitiveToken
    ) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (workingDirectory != null) {
                builder.directory(new File(workingDirectory));
            }
            if (envOverrides != null && !envOverrides.isEmpty()) {
                builder.environment().putAll(envOverrides);
            }

            Process process = builder.start();
            CompletableFuture<String> stdoutFuture = readStreamAsync(process.getInputStream());
            CompletableFuture<String> stderrFuture = readStreamAsync(process.getErrorStream());
            boolean finished = process.waitFor(cliTimeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "CLI command timed out");
            }

            int exitCode = process.exitValue();
            String stdout = redactSensitiveText(stdoutFuture.join(), sensitiveToken);
            String stderr = redactSensitiveText(stderrFuture.join(), sensitiveToken);
            String txDigest = extractTxDigest(stdout, stderr);

            return new CliExecutionResult(
                    commandForAudit,
                    exitCode,
                    stdout,
                    stderr,
                    txDigest,
                    Instant.now()
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute ERP CLI", ex);
        }
    }

    private String extractTxDigest(String stdout, String stderr) {
        String combined = (stdout == null ? "" : stdout) + "\n" + (stderr == null ? "" : stderr);
        Matcher matcher = TX_DIGEST_PATTERN.matcher(combined);
        return matcher.find() ? matcher.group() : null;
    }

    private String sanitizeCommandForAudit(List<String> command, String sensitiveToken) {
        List<String> sanitized = new ArrayList<>();
        for (int i = 0; i < command.size(); i++) {
            String token = command.get(i);
            if (SENSITIVE_VALUE_FLAGS.contains(token) && i + 1 < command.size()) {
                sanitized.add(token);
                sanitized.add("--private-key".equals(token) ? REDACTED_PRIVATE_KEY : REDACTED);
                i++;
                continue;
            }

            String normalized = ErpSecurityService.normalize(token);
            if (sensitiveToken != null && sensitiveToken.equals(normalized)) {
                sanitized.add(REDACTED_PRIVATE_KEY);
                continue;
            }

            sanitized.add(sanitizePathToken(token));
        }
        return String.join(" ", sanitized);
    }

    private String sanitizePathToken(String token) {
        if (token == null) {
            return "";
        }
        if (token.startsWith("/") || ABSOLUTE_WINDOWS_PATH_PATTERN.matcher(token).matches()) {
            return "<abs-path>/" + new File(token).getName();
        }
        return token;
    }

    private String redactSensitiveText(String value, String sensitiveToken) {
        if (value == null) {
            return null;
        }
        String redacted = value;
        if (sensitiveToken != null && !sensitiveToken.isBlank()) {
            redacted = redacted.replace(sensitiveToken, REDACTED_PRIVATE_KEY);
        }
        String homeDir = System.getProperty("user.home");
        if (homeDir != null && !homeDir.isBlank()) {
            redacted = redacted.replace(homeDir, "$HOME");
        }
        return redacted;
    }

    private CompletableFuture<String> readStreamAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream input = stream) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return "";
            }
        });
    }

    private String choose(String primary, String fallback) {
        String p = ErpSecurityService.normalize(primary);
        if (p != null) {
            return p;
        }
        return ErpSecurityService.normalize(fallback);
    }
}
