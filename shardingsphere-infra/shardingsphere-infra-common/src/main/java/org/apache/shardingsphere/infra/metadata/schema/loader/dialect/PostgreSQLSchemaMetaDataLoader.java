/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.metadata.schema.loader.dialect;

import org.apache.shardingsphere.infra.database.type.DatabaseTypeRegistry;
import org.apache.shardingsphere.infra.metadata.schema.loader.common.DataTypeLoader;
import org.apache.shardingsphere.infra.metadata.schema.loader.spi.DialectSchemaMetaDataLoader;
import org.apache.shardingsphere.infra.metadata.schema.model.ColumnMetaData;
import org.apache.shardingsphere.infra.metadata.schema.model.IndexMetaData;
import org.apache.shardingsphere.infra.metadata.schema.model.SchemaMetaData;
import org.apache.shardingsphere.infra.metadata.schema.model.TableMetaData;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Schema meta data loader for PostgreSQL.
 */
public final class PostgreSQLSchemaMetaDataLoader implements DialectSchemaMetaDataLoader {
    
    private static final String BASIC_TABLE_META_DATA_SQL = "SELECT table_name, column_name, ordinal_position, data_type, udt_name, column_default, table_schema"
            + " FROM information_schema.columns WHERE table_schema IN (%s)";
    
    private static final String TABLE_META_DATA_SQL_WITHOUT_TABLES = BASIC_TABLE_META_DATA_SQL + " ORDER BY ordinal_position";
    
    private static final String TABLE_META_DATA_SQL_WITH_TABLES = BASIC_TABLE_META_DATA_SQL + " AND table_name IN (%s) ORDER BY ordinal_position";
    
    private static final String PRIMARY_KEY_META_DATA_SQL = "SELECT tc.table_name, kc.column_name, kc.table_schema FROM information_schema.table_constraints tc"
            + " JOIN information_schema.key_column_usage kc ON kc.table_schema = tc.table_schema AND kc.table_name = tc.table_name AND kc.constraint_name = tc.constraint_name"
            + " WHERE tc.constraint_type = 'PRIMARY KEY' AND kc.ordinal_position IS NOT NULL AND kc.table_schema IN (%s)";
    
    private static final String BASIC_INDEX_META_DATA_SQL = "SELECT tablename, indexname, schemaname FROM pg_indexes WHERE schemaname IN (%s)";
    
    private static final String LOAD_ALL_ROLE_TABLE_GRANTS_SQL = "SELECT table_name FROM information_schema.role_table_grants";
    
    private static final String LOAD_FILTED_ROLE_TABLE_GRANTS_SQL = LOAD_ALL_ROLE_TABLE_GRANTS_SQL + " WHERE table_name IN (%s)";
    
    @Override
    public Collection<SchemaMetaData> load(final DataSource dataSource, final Collection<String> tables, final String defaultSchemaName) throws SQLException {
        Collection<SchemaMetaData> result = new LinkedList<>();
        Collection<String> schemaNames = loadSchemaNames(dataSource, DatabaseTypeRegistry.getActualDatabaseType(getType()));
        Map<String, Map<String, Collection<IndexMetaData>>> indexMetaDataMap = loadIndexMetaDataMap(dataSource, schemaNames);
        for (Entry<String, Map<String, Collection<ColumnMetaData>>> entry : loadColumnMetaDataMap(dataSource, tables, schemaNames).entrySet()) {
            String schemaName = entry.getKey();
            Collection<TableMetaData> tablesMeta = new LinkedList<>();
            Map<String, Collection<IndexMetaData>> tableIndexMetaDataMap = indexMetaDataMap.getOrDefault(schemaName, Collections.emptyMap());
            for (Entry<String, Collection<ColumnMetaData>> tableEntry : entry.getValue().entrySet()) {
                String tableName = tableEntry.getKey();
                Collection<IndexMetaData> indexMetaDataList = tableIndexMetaDataMap.getOrDefault(tableName, Collections.emptyList());
                tablesMeta.add(new TableMetaData(tableName, tableEntry.getValue(), indexMetaDataList, Collections.emptyList()));
            }
            Map<String, TableMetaData> tableMetaDataMap = tablesMeta.stream().collect(Collectors.toMap(TableMetaData::getName, Function.identity(), (oldValue, newValue) -> newValue));
            result.add(new SchemaMetaData(entry.getKey(), tableMetaDataMap));
        }
        return result;
    }
    
    private Map<String, Map<String, Collection<ColumnMetaData>>> loadColumnMetaDataMap(final DataSource dataSource, final Collection<String> tables,
                                                                                       final Collection<String> schemaNames) throws SQLException {
        Map<String, Map<String, Collection<ColumnMetaData>>> result = new LinkedHashMap<>();
        Collection<String> roleTableGrants = loadRoleTableGrants(dataSource, tables);
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(getColumnMetaDataSQL(schemaNames, tables))) {
            Map<String, Integer> dataTypes = DataTypeLoader.load(connection.getMetaData());
            Set<String> primaryKeys = loadPrimaryKeys(connection, schemaNames);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("table_name");
                    if (!roleTableGrants.contains(tableName)) {
                        continue;
                    }
                    String schemaName = resultSet.getString("table_schema");
                    Map<String, Collection<ColumnMetaData>> columnMetaDataMap = result.computeIfAbsent(schemaName, key -> new LinkedHashMap<>());
                    Collection<ColumnMetaData> columns = columnMetaDataMap.computeIfAbsent(tableName, key -> new LinkedList<>());
                    columns.add(loadColumnMetaData(dataTypes, primaryKeys, resultSet));
                }
            }
        }
        return result;
    }
    
    private Set<String> loadPrimaryKeys(final Connection connection, final Collection<String> schemaNames) throws SQLException {
        Set<String> result = new HashSet<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(getPrimaryKeyMetaDataSQL(schemaNames))) {
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("table_name");
                    String columnName = resultSet.getString("column_name");
                    result.add(tableName + "," + columnName);
                }
            }
        }
        return result;
    }
    
    private String getPrimaryKeyMetaDataSQL(final Collection<String> schemaNames) {
        return String.format(PRIMARY_KEY_META_DATA_SQL, schemaNames.stream().map(each -> String.format("'%s'", each)).collect(Collectors.joining(",")));
    }
    
    private ColumnMetaData loadColumnMetaData(final Map<String, Integer> dataTypeMap, final Set<String> primaryKeys, final ResultSet resultSet) throws SQLException {
        String tableName = resultSet.getString("table_name");
        String columnName = resultSet.getString("column_name");
        String dataType = resultSet.getString("udt_name");
        boolean isPrimaryKey = primaryKeys.contains(tableName + "," + columnName);
        String columnDefault = resultSet.getString("column_default");
        boolean generated = null != columnDefault && columnDefault.startsWith("nextval(");
        // TODO user defined collation which deterministic is false
        boolean caseSensitive = true;
        return new ColumnMetaData(columnName, dataTypeMap.get(dataType), isPrimaryKey, generated, caseSensitive);
    }
    
    private String getColumnMetaDataSQL(final Collection<String> schemaNames, final Collection<String> tables) {
        String schemaNameParam = schemaNames.stream().map(each -> String.format("'%s'", each)).collect(Collectors.joining(","));
        return tables.isEmpty() ? String.format(TABLE_META_DATA_SQL_WITHOUT_TABLES, schemaNameParam)
                : String.format(TABLE_META_DATA_SQL_WITH_TABLES, schemaNameParam, tables.stream().map(each -> String.format("'%s'", each)).collect(Collectors.joining(",")));
    }
    
    private Map<String, Map<String, Collection<IndexMetaData>>> loadIndexMetaDataMap(final DataSource dataSource, final Collection<String> schemaNames) throws SQLException {
        Map<String, Map<String, Collection<IndexMetaData>>> result = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(getIndexMetaDataSQL(schemaNames))) {
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String schemaName = resultSet.getString("schemaname");
                    String tableName = resultSet.getString("tablename");
                    String indexName = resultSet.getString("indexname");
                    Map<String, Collection<IndexMetaData>> indexMetaDataMap = result.computeIfAbsent(schemaName, key -> new LinkedHashMap<>());
                    Collection<IndexMetaData> indexes = indexMetaDataMap.computeIfAbsent(tableName, key -> new LinkedList<>());
                    indexes.add(new IndexMetaData(indexName));
                }
            }
        }
        return result;
    }
    
    private String getIndexMetaDataSQL(final Collection<String> schemaNames) {
        return String.format(BASIC_INDEX_META_DATA_SQL, schemaNames.stream().map(each -> String.format("'%s'", each)).collect(Collectors.joining(",")));
    }
    
    private Collection<String> loadRoleTableGrants(final DataSource dataSource, final Collection<String> tables) throws SQLException {
        Collection<String> result = new HashSet<>(tables.size(), 1);
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(getLoadRoleTableGrantsSQL(tables))) {
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getString("table_name"));
                }
            }
        }
        return result;
    }
    
    private String getLoadRoleTableGrantsSQL(final Collection<String> tables) {
        return tables.isEmpty() ? LOAD_ALL_ROLE_TABLE_GRANTS_SQL
                : String.format(LOAD_FILTED_ROLE_TABLE_GRANTS_SQL,
                        tables.stream().map(each -> String.format("'%s'", each)).collect(Collectors.joining(",")));
    }
    
    @Override
    public String getType() {
        return "PostgreSQL";
    }
}
