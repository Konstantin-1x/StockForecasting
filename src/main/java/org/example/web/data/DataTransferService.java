package org.example.web.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Service
public class DataTransferService {

    private static final String FORMAT = "stockforecasting-jsonl-zip";
    private static final int VERSION = 2;
    private static final int INSERT_BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DataTransferState transferState;
    private final TransactionTemplate exportTransactionTemplate;
    private final TransactionTemplate importTransactionTemplate;

    public DataTransferService(JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper,
                               DataTransferState transferState,
                               PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transferState = transferState;
        this.exportTransactionTemplate = new TransactionTemplate(transactionManager);
        this.exportTransactionTemplate.setReadOnly(true);
        this.exportTransactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.exportTransactionTemplate.setTimeout(3600);
        this.importTransactionTemplate = new TransactionTemplate(transactionManager);
        this.importTransactionTemplate.setTimeout(3600);
    }

    public String exportFileName() {
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        return "stockforecasting-full-db-" + timestamp + ".zip";
    }

    public void exportZip(OutputStream outputStream) throws IOException {
        try (DataTransferState.TransferLock ignored = transferState.begin("export")) {
            writeExportZip(outputStream);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public DataExportFile exportZipToTempFile() throws IOException {
        String fileName = exportFileName();
        Path tempFile = Files.createTempFile("stockforecasting-full-db-", ".zip");
        boolean completed = false;
        try (DataTransferState.TransferLock ignored = transferState.begin("export")) {
            try (OutputStream output = Files.newOutputStream(tempFile)) {
                writeExportZip(output);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            verifyZipArchive(tempFile);
            completed = true;
            return new DataExportFile(fileName, tempFile, Files.size(tempFile));
        } finally {
            if (!completed) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private void writeExportZip(OutputStream outputStream) {
        exportTransactionTemplate.executeWithoutResult(status -> {
            try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                List<TableSpec> tables = loadCurrentTables();
                writeManifest(zip, tables);
                for (TableSpec table : tables) {
                    writeTable(zip, table);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void verifyZipArchive(Path zipPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            if (zip.getEntry("manifest.json") == null) {
                throw new IOException("Сформированный архив не содержит manifest.json.");
            }

            byte[] buffer = new byte[64 * 1024];
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    while (input.read(buffer) != -1) {
                        // Read the whole entry so ZipFile can validate the stream and CRC.
                    }
                } catch (IOException e) {
                    throw new IOException("Сформированный архив поврежден в файле " + entry.getName()
                            + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new IOException("Не удалось проверить сформированный ZIP-архив: " + e.getMessage(), e);
        }
    }

    public DataImportResult importZip(InputStream inputStream) throws IOException {
        Path tempFile = Files.createTempFile("stockforecasting-import-", ".zip");
        try (DataTransferState.TransferLock ignored = transferState.begin("import")) {
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return importTransactionTemplate.execute(status -> {
                try {
                    return importZipFile(tempFile);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void writeManifest(ZipOutputStream zip, List<TableSpec> tables) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", FORMAT);
        manifest.put("version", VERSION);
        manifest.put("schema", tables.isEmpty() ? currentSchema() : tables.get(0).schemaName());
        manifest.put("exportedAt", Instant.now().toString());

        List<Map<String, Object>> tableInfoList = new ArrayList<>();
        for (TableSpec table : tables) {
            Map<String, Object> tableInfo = new LinkedHashMap<>();
            tableInfo.put("name", table.tableName());
            tableInfo.put("entry", table.entryName());
            tableInfo.put("rows", countRows(table));
            tableInfo.put("primaryKey", table.primaryKeyColumns());

            List<Map<String, Object>> columns = new ArrayList<>();
            for (ColumnSpec column : table.columns()) {
                Map<String, Object> columnInfo = new LinkedHashMap<>();
                columnInfo.put("name", column.name());
                columnInfo.put("jdbcType", column.jdbcType());
                columnInfo.put("typeName", column.typeName());
                columnInfo.put("nullable", column.nullable());
                columnInfo.put("identity", column.autoIncrement());
                columns.add(columnInfo);
            }
            tableInfo.put("columns", columns);
            tableInfoList.add(tableInfo);
        }
        manifest.put("tables", tableInfoList);

        zip.putNextEntry(new ZipEntry("manifest.json"));
        zip.write(objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(manifest)
                .getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void writeTable(ZipOutputStream zip, TableSpec table) throws IOException {
        zip.putNextEntry(new ZipEntry(table.entryName()));
        String sql = "select " + table.columnNamesCsv()
                + " from " + table.qualifiedName()
                + table.orderBySql();

        jdbcTemplate.query(sql, resultSet -> {
            try {
                Map<String, Object> row = new LinkedHashMap<>();
                for (ColumnSpec column : table.columns()) {
                    row.put(column.name(), normalizeValue(resultSet.getObject(column.name())));
                }
                zip.write(objectMapper.writeValueAsBytes(row));
                zip.write('\n');
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        zip.closeEntry();
    }

    private DataImportResult importZipFile(Path zipPath) throws IOException {
        List<TableSpec> currentTables = loadCurrentTables();
        Map<String, TableSpec> currentTableMap = tableMap(currentTables);
        Map<String, Long> importedRows = new LinkedHashMap<>();

        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            JsonNode manifest = readAndValidateManifest(zip);
            Map<String, ArchiveTable> archiveTables = readArchiveTables(manifest);
            validateArchiveMatchesCurrentSchema(currentTableMap, archiveTables);

            truncateTables(currentTables);
            for (TableSpec table : currentTables) {
                ArchiveTable archiveTable = archiveTables.get(table.tableName());
                List<ColumnSpec> importColumns = resolveImportColumns(table, archiveTable);
                long rows = importTable(zip, table, archiveTable, importColumns);
                importedRows.put(table.tableName(), rows);
            }
        }

        resetSequences(currentTables);
        long totalRows = importedRows.values().stream().mapToLong(Long::longValue).sum();
        return new DataImportResult(totalRows, importedRows);
    }

    private JsonNode readAndValidateManifest(ZipFile zip) throws IOException {
        ZipEntry manifestEntry = zip.getEntry("manifest.json");
        if (manifestEntry == null) {
            throw new IOException("Архив не содержит manifest.json.");
        }
        try (InputStream input = zip.getInputStream(manifestEntry)) {
            JsonNode manifest = objectMapper.readTree(input);
            if (!FORMAT.equals(manifest.path("format").asText())) {
                throw new IOException("Неверный формат архива экспорта.");
            }
            if (!manifest.path("tables").isArray()) {
                throw new IOException("Manifest архива не содержит список таблиц.");
            }
            return manifest;
        }
    }

    private Map<String, ArchiveTable> readArchiveTables(JsonNode manifest) throws IOException {
        Map<String, ArchiveTable> tables = new LinkedHashMap<>();
        for (JsonNode tableNode : manifest.path("tables")) {
            String name = tableNode.path("name").asText(null);
            String entry = tableNode.path("entry").asText(null);
            if (name == null || name.isBlank() || entry == null || entry.isBlank()) {
                throw new IOException("Manifest архива содержит таблицу без имени или файла.");
            }

            List<String> columns = new ArrayList<>();
            JsonNode columnNodes = tableNode.path("columns");
            if (columnNodes.isArray()) {
                for (JsonNode columnNode : columnNodes) {
                    if (columnNode.isTextual()) {
                        columns.add(columnNode.asText());
                    } else {
                        columns.add(columnNode.path("name").asText());
                    }
                }
            }
            tables.put(name, new ArchiveTable(name, entry, List.copyOf(columns)));
        }
        return tables;
    }

    private void validateArchiveMatchesCurrentSchema(Map<String, TableSpec> currentTables,
                                                     Map<String, ArchiveTable> archiveTables) throws IOException {
        List<String> missingTables = currentTables.keySet().stream()
                .filter(table -> !archiveTables.containsKey(table))
                .toList();
        if (!missingTables.isEmpty()) {
            throw new IOException("Архив неполный: отсутствуют таблицы " + String.join(", ", missingTables) + ".");
        }

        List<String> unknownTables = archiveTables.keySet().stream()
                .filter(table -> !currentTables.containsKey(table))
                .toList();
        if (!unknownTables.isEmpty()) {
            throw new IOException("Архив создан для другой схемы: неизвестные таблицы "
                    + String.join(", ", unknownTables) + ".");
        }

        for (TableSpec table : currentTables.values()) {
            ArchiveTable archiveTable = archiveTables.get(table.tableName());
            if (archiveTable.columnNames().isEmpty()) {
                continue;
            }

            Set<String> archiveColumns = new LinkedHashSet<>(archiveTable.columnNames());
            List<String> missingColumns = table.columns().stream()
                    .map(ColumnSpec::name)
                    .filter(column -> !archiveColumns.contains(column))
                    .toList();
            if (!missingColumns.isEmpty()) {
                throw new IOException("Архив неполный: в таблице " + table.tableName()
                        + " отсутствуют колонки " + String.join(", ", missingColumns) + ".");
            }

            Set<String> currentColumns = table.columnMap().keySet();
            List<String> unknownColumns = archiveTable.columnNames().stream()
                    .filter(column -> !currentColumns.contains(column))
                    .toList();
            if (!unknownColumns.isEmpty()) {
                throw new IOException("Архив создан для другой схемы: в таблице " + table.tableName()
                        + " есть неизвестные колонки " + String.join(", ", unknownColumns) + ".");
            }
        }
    }

    private List<ColumnSpec> resolveImportColumns(TableSpec table, ArchiveTable archiveTable) {
        if (archiveTable.columnNames().isEmpty()) {
            return table.columns();
        }
        return archiveTable.columnNames().stream()
                .map(table.columnMap()::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private long importTable(ZipFile zip,
                             TableSpec table,
                             ArchiveTable archiveTable,
                             List<ColumnSpec> columns) throws IOException {
        ZipEntry entry = zip.getEntry(archiveTable.entryName());
        if (entry == null) {
            throw new IOException("Архив не содержит таблицу " + archiveTable.entryName() + ".");
        }

        List<ForeignKeySpec> selfReferences = table.selfReferences(columns);
        List<SelfReferenceUpdate> selfReferenceUpdates = new ArrayList<>();
        String sql = insertSql(table, columns);
        ImportCounter counter = new ImportCounter();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    String line;
                    int pendingBatchRows = 0;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }
                        JsonNode row = objectMapper.readTree(line);
                        Map<String, Object> overrides = selfReferenceOverrides(table, row, selfReferences, selfReferenceUpdates);
                        setStatementValues(statement, columns, row, overrides);
                        statement.addBatch();
                        counter.rows++;
                        pendingBatchRows++;
                        if (pendingBatchRows >= INSERT_BATCH_SIZE) {
                            statement.executeBatch();
                            pendingBatchRows = 0;
                        }
                    }
                    if (pendingBatchRows > 0) {
                        statement.executeBatch();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return null;
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        applySelfReferenceUpdates(table, selfReferenceUpdates);
        return counter.rows;
    }

    private Map<String, Object> selfReferenceOverrides(TableSpec table,
                                                       JsonNode row,
                                                       List<ForeignKeySpec> selfReferences,
                                                       List<SelfReferenceUpdate> updates) throws IOException {
        if (selfReferences.isEmpty()) {
            return Map.of();
        }
        if (table.primaryKeyColumns().isEmpty()) {
            throw new IOException("Таблица " + table.tableName()
                    + " содержит самоссылку, но не имеет первичного ключа для восстановления связи.");
        }

        Map<String, Object> overrides = new HashMap<>();
        for (ForeignKeySpec selfReference : selfReferences) {
            ColumnSpec fkColumn = table.columnMap().get(selfReference.columnName());
            JsonNode rawValue = row.get(selfReference.columnName());
            if (fkColumn == null || rawValue == null || rawValue.isNull()) {
                continue;
            }

            Object fkValue = importValue(fkColumn, rawValue);
            List<Object> primaryKeyValues = new ArrayList<>();
            for (String primaryKeyColumnName : table.primaryKeyColumns()) {
                ColumnSpec primaryKeyColumn = table.columnMap().get(primaryKeyColumnName);
                JsonNode primaryKeyRawValue = row.get(primaryKeyColumnName);
                if (primaryKeyColumn == null || primaryKeyRawValue == null || primaryKeyRawValue.isNull()) {
                    throw new IOException("В строке таблицы " + table.tableName()
                            + " отсутствует значение первичного ключа " + primaryKeyColumnName + ".");
                }
                primaryKeyValues.add(importValue(primaryKeyColumn, primaryKeyRawValue));
            }

            overrides.put(selfReference.columnName(), null);
            updates.add(new SelfReferenceUpdate(selfReference.columnName(), fkValue, primaryKeyValues));
        }
        return overrides;
    }

    private void applySelfReferenceUpdates(TableSpec table, List<SelfReferenceUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }

        for (SelfReferenceUpdate update : updates) {
            ColumnSpec foreignKeyColumn = table.columnMap().get(update.columnName());
            List<ColumnSpec> primaryKeyColumns = table.primaryKeyColumns().stream()
                    .map(table.columnMap()::get)
                    .toList();
            String whereClause = primaryKeyColumns.stream()
                    .map(column -> quoteIdentifier(column.name()) + " = ?")
                    .reduce((left, right) -> left + " and " + right)
                    .orElseThrow();
            String sql = "update " + table.qualifiedName()
                    + " set " + quoteIdentifier(update.columnName()) + " = ?"
                    + " where " + whereClause;

            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                setSqlValue(statement, 1, foreignKeyColumn, update.value());
                for (int i = 0; i < primaryKeyColumns.size(); i++) {
                    setSqlValue(statement, i + 2, primaryKeyColumns.get(i), update.primaryKeyValues().get(i));
                }
                return statement;
            });
        }
    }

    private void setStatementValues(PreparedStatement statement,
                                    List<ColumnSpec> columns,
                                    JsonNode row,
                                    Map<String, Object> overrides) throws SQLException {
        for (int index = 0; index < columns.size(); index++) {
            ColumnSpec column = columns.get(index);
            Object value = overrides.containsKey(column.name())
                    ? overrides.get(column.name())
                    : importValue(column, row.get(column.name()));
            setSqlValue(statement, index + 1, column, value);
        }
    }

    private void setSqlValue(PreparedStatement statement, int index, ColumnSpec column, Object value) throws SQLException {
        if (value == null) {
            statement.setNull(index, column.jdbcType());
            return;
        }
        if (value instanceof Timestamp timestamp) {
            statement.setTimestamp(index, timestamp);
        } else if (value instanceof Date date) {
            statement.setDate(index, date);
        } else if (value instanceof Time time) {
            statement.setTime(index, time);
        } else if (value instanceof BigDecimal decimal) {
            statement.setBigDecimal(index, decimal);
        } else if (value instanceof Integer integer) {
            statement.setInt(index, integer);
        } else if (value instanceof Long longValue) {
            statement.setLong(index, longValue);
        } else if (value instanceof Boolean bool) {
            statement.setBoolean(index, bool);
        } else if (value instanceof Double doubleValue) {
            statement.setDouble(index, doubleValue);
        } else if (value instanceof Float floatValue) {
            statement.setFloat(index, floatValue);
        } else if (value instanceof byte[] bytes) {
            statement.setBytes(index, bytes);
        } else {
            statement.setObject(index, value, column.jdbcType());
        }
    }

    private String insertSql(TableSpec table, List<ColumnSpec> columns) {
        String columnNames = String.join(", ", columns.stream().map(column -> quoteIdentifier(column.name())).toList());
        String placeholders = String.join(", ", columns.stream().map(column -> "?").toList());
        return "insert into " + table.qualifiedName()
                + " (" + columnNames + ") values (" + placeholders + ")";
    }

    private void truncateTables(List<TableSpec> tables) {
        if (tables.isEmpty()) {
            return;
        }
        String tableNames = String.join(", ", tables.stream().map(TableSpec::qualifiedName).toList());
        jdbcTemplate.execute("truncate table " + tableNames + " restart identity cascade");
    }

    private void resetSequences(List<TableSpec> tables) {
        for (TableSpec table : tables) {
            for (ColumnSpec column : table.columns()) {
                if (column.sequenceName() == null || column.sequenceName().isBlank()) {
                    continue;
                }
                String maxValueSql = "(select max(" + quoteIdentifier(column.name()) + ")::bigint from "
                        + table.qualifiedName() + ")";
                String sql = """
                        select setval('%s'::regclass,
                            greatest(coalesce(%s, 0), 1),
                            coalesce(%s, 0) > 0)
                        """.formatted(escapeSqlString(column.sequenceName()), maxValueSql, maxValueSql);
                jdbcTemplate.execute(sql);
            }
        }
    }

    private List<TableSpec> loadCurrentTables() {
        String schema = currentSchema();
        List<String> tableNames = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = ?
                  and table_type = 'BASE TABLE'
                order by table_name
                """, String.class, schema);

        Map<String, List<String>> primaryKeys = loadPrimaryKeys(schema);
        Map<String, List<ForeignKeySpec>> foreignKeys = loadForeignKeys(schema);
        Map<String, String> sequenceNames = loadSequenceNames(schema);

        List<TableSpec> tables = new ArrayList<>();
        for (String tableName : tableNames) {
            List<ColumnSpec> columns = loadColumns(schema, tableName, sequenceNames);
            tables.add(new TableSpec(
                    schema,
                    tableName,
                    "tables/" + tableName + ".jsonl",
                    columns,
                    primaryKeys.getOrDefault(tableName, List.of()),
                    foreignKeys.getOrDefault(tableName, List.of())
            ));
        }
        return sortTablesForImport(tables);
    }

    private String currentSchema() {
        String schema = jdbcTemplate.queryForObject("select current_schema()", String.class);
        return schema == null || schema.isBlank() ? "public" : schema;
    }

    private Map<String, List<String>> loadPrimaryKeys(String schema) {
        Map<String, List<String>> primaryKeys = new LinkedHashMap<>();
        jdbcTemplate.query("""
                select kcu.table_name, kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_schema = tc.constraint_schema
                 and kcu.constraint_name = tc.constraint_name
                 and kcu.table_schema = tc.table_schema
                 and kcu.table_name = tc.table_name
                where tc.table_schema = ?
                  and tc.constraint_type = 'PRIMARY KEY'
                order by kcu.table_name, kcu.ordinal_position
                """, (RowCallbackHandler) resultSet -> primaryKeys
                .computeIfAbsent(resultSet.getString("table_name"), ignored -> new ArrayList<>())
                .add(resultSet.getString("column_name")), schema);
        return primaryKeys;
    }

    private Map<String, List<ForeignKeySpec>> loadForeignKeys(String schema) {
        Map<String, List<ForeignKeySpec>> foreignKeys = new LinkedHashMap<>();
        jdbcTemplate.query("""
                select tc.table_name,
                       kcu.column_name,
                       ccu.table_name as referenced_table_name,
                       ccu.column_name as referenced_column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_schema = tc.constraint_schema
                 and kcu.constraint_name = tc.constraint_name
                 and kcu.table_schema = tc.table_schema
                 and kcu.table_name = tc.table_name
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_schema = tc.constraint_schema
                 and ccu.constraint_name = tc.constraint_name
                where tc.table_schema = ?
                  and tc.constraint_type = 'FOREIGN KEY'
                order by tc.table_name, kcu.ordinal_position
                """, (RowCallbackHandler) resultSet -> {
            String tableName = resultSet.getString("table_name");
            foreignKeys.computeIfAbsent(tableName, ignored -> new ArrayList<>())
                    .add(new ForeignKeySpec(
                            tableName,
                            resultSet.getString("column_name"),
                            resultSet.getString("referenced_table_name"),
                            resultSet.getString("referenced_column_name")
                    ));
        }, schema);
        return foreignKeys;
    }

    private Map<String, String> loadSequenceNames(String schema) {
        Map<String, String> sequenceNames = new HashMap<>();
        jdbcTemplate.query("""
                select table_name,
                       column_name,
                       pg_get_serial_sequence(format('%I.%I', table_schema, table_name), column_name) as sequence_name
                from information_schema.columns
                where table_schema = ?
                  and (column_default like 'nextval%' or is_identity = 'YES')
                """, (RowCallbackHandler) resultSet -> {
            String sequenceName = resultSet.getString("sequence_name");
            if (sequenceName != null && !sequenceName.isBlank()) {
                sequenceNames.put(sequenceKey(resultSet.getString("table_name"), resultSet.getString("column_name")), sequenceName);
            }
        }, schema);
        return sequenceNames;
    }

    private List<ColumnSpec> loadColumns(String schema, String tableName, Map<String, String> sequenceNames) {
        return jdbcTemplate.execute((ConnectionCallback<List<ColumnSpec>>) connection -> {
            List<ColumnSpec> columns = new ArrayList<>();
            try (ResultSet resultSet = connection.getMetaData().getColumns(null, schema, tableName, null)) {
                while (resultSet.next()) {
                    String columnName = resultSet.getString("COLUMN_NAME");
                    int nullable = resultSet.getInt("NULLABLE");
                    String autoIncrement = resultSet.getString("IS_AUTOINCREMENT");
                    String generated = resultSet.getString("IS_GENERATEDCOLUMN");
                    columns.add(new ColumnSpec(
                            columnName,
                            resultSet.getInt("DATA_TYPE"),
                            resultSet.getString("TYPE_NAME"),
                            resultSet.getInt("ORDINAL_POSITION"),
                            nullable == java.sql.DatabaseMetaData.columnNullable,
                            "YES".equalsIgnoreCase(autoIncrement),
                            "YES".equalsIgnoreCase(generated),
                            sequenceNames.get(sequenceKey(tableName, columnName))
                    ));
                }
            }
            columns.sort(Comparator.comparingInt(ColumnSpec::ordinalPosition));
            return List.copyOf(columns);
        });
    }

    private List<TableSpec> sortTablesForImport(List<TableSpec> tables) {
        Map<String, TableSpec> byName = tableMap(tables);
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (TableSpec table : tables) {
            Set<String> tableDependencies = new LinkedHashSet<>();
            for (ForeignKeySpec foreignKey : table.foreignKeys()) {
                if (!foreignKey.referencedTableName().equals(table.tableName())
                        && byName.containsKey(foreignKey.referencedTableName())) {
                    tableDependencies.add(foreignKey.referencedTableName());
                }
            }
            dependencies.put(table.tableName(), tableDependencies);
        }

        List<TableSpec> sorted = new ArrayList<>();
        Set<String> imported = new HashSet<>();
        Set<String> remaining = new LinkedHashSet<>(byName.keySet());

        boolean progressed;
        do {
            progressed = false;
            for (String tableName : List.copyOf(remaining)) {
                if (imported.containsAll(dependencies.getOrDefault(tableName, Set.of()))) {
                    sorted.add(byName.get(tableName));
                    imported.add(tableName);
                    remaining.remove(tableName);
                    progressed = true;
                }
            }
        } while (progressed);

        remaining.stream()
                .sorted()
                .map(byName::get)
                .forEach(sorted::add);
        return sorted;
    }

    private Map<String, TableSpec> tableMap(List<TableSpec> tables) {
        Map<String, TableSpec> tableMap = new LinkedHashMap<>();
        for (TableSpec table : tables) {
            tableMap.put(table.tableName(), table);
        }
        return tableMap;
    }

    private long countRows(TableSpec table) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + table.qualifiedName(), Long.class);
        return count == null ? 0 : count;
    }

    private Object normalizeValue(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toString();
        }
        if (value instanceof Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Array array) {
            return array.getArray();
        }
        if (value instanceof Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof LocalTime localTime) {
            return localTime.toString();
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return value;
    }

    private Object importValue(ColumnSpec column, JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }

        return switch (column.jdbcType()) {
            case Types.BIGINT -> value.longValue();
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> value.intValue();
            case Types.NUMERIC, Types.DECIMAL -> decimalValue(value);
            case Types.BOOLEAN, Types.BIT -> value.booleanValue();
            case Types.FLOAT, Types.REAL -> value.floatValue();
            case Types.DOUBLE -> value.doubleValue();
            case Types.DATE -> Date.valueOf(LocalDate.parse(value.asText()));
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> Time.valueOf(LocalTime.parse(value.asText()));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> Timestamp.from(Instant.parse(value.asText()));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> Base64.getDecoder().decode(value.asText());
            default -> value.asText();
        };
    }

    private BigDecimal decimalValue(JsonNode value) {
        if (value.isNumber()) {
            return value.decimalValue();
        }
        return new BigDecimal(value.asText());
    }

    private static String sequenceKey(String tableName, String columnName) {
        return tableName + '\u0000' + columnName;
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String escapeSqlString(String value) {
        return value.replace("'", "''");
    }

    private static final class ImportCounter {
        private long rows;
    }

    private record ArchiveTable(String tableName, String entryName, List<String> columnNames) {
    }

    private record ColumnSpec(
            String name,
            int jdbcType,
            String typeName,
            int ordinalPosition,
            boolean nullable,
            boolean autoIncrement,
            boolean generated,
            String sequenceName
    ) {
    }

    private record ForeignKeySpec(
            String tableName,
            String columnName,
            String referencedTableName,
            String referencedColumnName
    ) {
    }

    private record SelfReferenceUpdate(String columnName, Object value, List<Object> primaryKeyValues) {
    }

    private record TableSpec(
            String schemaName,
            String tableName,
            String entryName,
            List<ColumnSpec> columns,
            List<String> primaryKeyColumns,
            List<ForeignKeySpec> foreignKeys
    ) {
        String qualifiedName() {
            return quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        }

        String columnNamesCsv() {
            return String.join(", ", columns.stream().map(column -> quoteIdentifier(column.name())).toList());
        }

        String orderBySql() {
            if (primaryKeyColumns.isEmpty()) {
                return "";
            }
            String orderBy = String.join(", ", primaryKeyColumns.stream().map(DataTransferService::quoteIdentifier).toList());
            return " order by " + orderBy;
        }

        Map<String, ColumnSpec> columnMap() {
            Map<String, ColumnSpec> columnMap = new LinkedHashMap<>();
            for (ColumnSpec column : columns) {
                columnMap.put(column.name(), column);
            }
            return columnMap;
        }

        List<ForeignKeySpec> selfReferences(List<ColumnSpec> importColumns) {
            Set<String> importedColumnNames = new HashSet<>(importColumns.stream().map(ColumnSpec::name).toList());
            return foreignKeys.stream()
                    .filter(foreignKey -> foreignKey.referencedTableName().equals(tableName))
                    .filter(foreignKey -> importedColumnNames.contains(foreignKey.columnName()))
                    .toList();
        }
    }
}
