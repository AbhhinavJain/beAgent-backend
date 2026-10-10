package com.gate.pyq.controller;

import com.gate.pyq.repository.QuestionRepository;
import com.gate.pyq.repository.UserAttemptRepository;
import com.gate.pyq.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemStatusController {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final QuestionRepository questionRepository;
    private final UserAttemptRepository userAttemptRepository;
    private final UserRepository userRepository;

    @Value("${app.backup.file-path:../backups/questions_emergency_backup.json}")
    private String backupFilePath;

    @GetMapping("/db-status")
    public ResponseEntity<Map<String, Object>> getDatabaseStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String url = meta.getURL();
            String product = meta.getDatabaseProductName();
            boolean isSupabasePostgres = url != null && url.startsWith("jdbc:postgresql:");

            status.put("connected", true);
            status.put("databaseProduct", product);
            status.put("connectionMode", isSupabasePostgres ? "SUPABASE_POSTGRESQL" : "LOCAL_PERSISTENT_DISK_DB");
            status.put("jdbcUrl", url);
            status.put("totalQuestionsInDb", questionRepository.count());
            status.put("totalAttemptsInDb", userAttemptRepository.count());
            status.put("totalUsersInDb", userRepository.count());

            File backupFile = new File(backupFilePath);
            status.put("localEmergencyBackupExists", backupFile.exists());
            status.put("localEmergencyBackupPath", backupFile.getCanonicalPath());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            status.put("connected", false);
            status.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(status);
        }
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> executeReadOnlyQuery(@RequestBody Map<String, String> payload) {
        String rawSql = payload != null ? payload.getOrDefault("sql", "").trim() : "";
        Map<String, Object> result = new LinkedHashMap<>();

        if (rawSql.isEmpty()) {
            result.put("error", "SQL query cannot be empty.");
            return ResponseEntity.badRequest().body(result);
        }

        String normalized = rawSql.toUpperCase(Locale.ROOT);
        if (!(normalized.startsWith("SELECT") || normalized.startsWith("SHOW") || normalized.startsWith("EXPLAIN"))) {
            result.put("error", "Only read-only SELECT / SHOW queries are allowed in the query explorer.");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(rawSql);
            List<String> columns = rows.isEmpty()
                    ? List.of()
                    : new ArrayList<>(rows.get(0).keySet());
            result.put("sql", rawSql);
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("rowCount", rows.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
}
