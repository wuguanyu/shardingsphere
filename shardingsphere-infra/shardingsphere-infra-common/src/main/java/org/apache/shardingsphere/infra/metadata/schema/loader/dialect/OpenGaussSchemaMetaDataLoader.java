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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Schema meta data loader for openGauss.
 */
public final class OpenGaussSchemaMetaDataLoader implements DialectSchemaMetaDataLoader {
    
    private static final String BASIC_TABLE_META_DATA_SQL_NO_ORDER = "SELECT table_name, column_name, ordinal_position, data_type, udt_name, column_default FROM information_schema.columns "
            + "WHERE table_schema = ?";
    
    private static final String ORDER_BY_ORDINAL_POSITION = " ORDER BY ordinal_position";
    
    private static final String BASIC_TABLE_META_DATA_SQL = BASIC_TABLE_META_DATA_SQL_NO_ORDER + ORDER_BY_ORDINAL_POSITION;
    
    private static final String TABLE_META_DATA_SQL_WITH_EXISTED_TABLES = BASIC_TABLE_META_DATA_SQL_NO_ORDER + " AND table_name IN (%s)" + ORDER_BY_ORDINAL_POSITION;
    
    private static final String PRIMARY_KEY_META_DATA_SQL = "SELECT tc.table_name, kc.column_name FROM information_schema.table_constraints tc"
            + " JOIN information_schema.key_column_usage kc"
            + " ON kc.table_schema = tc.table_schema AND kc.table_name = tc.table_name AND kc.constraint_name = tc.constraint_name"
            + " WHERE tc.constraint_type = 'PRIMARY KEY' AND kc.ordinal_position IS NOT NULL AND kc.table_schema = ?";
    
    private static final String BASIC_INDEX_META_DATA_SQL = "SELECT tablename, indexname FROM pg_indexes WHERE schemaname = ?";
    
    @Override
    public Collection<SchemaMetaData> load(final DataSource dataSource, final Collection<String> existedTables, final String defaultSchemaName) throws SQLException {
        Map<String, TableMetaData> tableMetaDataMap = new LinkedHashMap<>();
        Map<String, Collection<IndexMetaData>> indexMetaDataMap = loadIndexMetaDataMap(dataSource);
        for (Entry<String, Collection<ColumnMetaData>> entry : loadColumnMetaDataMap(dataSource, existedTables).entrySet()) {
            Collection<IndexMetaData> indexMetaDataList = indexMetaDataMap.get(entry.getKey());
            if (null == indexMetaDataList) {
                indexMetaDataList = Collections.emptyList();
            }
            tableMetaDataMap.put(entry.getKey(), new TableMetaData(entry.getKey(), entry.getValue(), indexMetaDataList, Collections.emptyList()));
        }
        return Collections.singletonList(new SchemaMetaData(defaultSchemaName, tableMetaDataMap));
    }
    
    private Map<String, Collection<ColumnMetaData>> loadColumnMetaDataMap(final DataSource dataSource, final Collection<String> existedTables) throws SQLException {
        Map<String, Collection<ColumnMetaData>> result = new HashMap<>();
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(getTableMetaDataSQL(existedTables))) {
            Map<String, Integer> dataTypes = DataTypeLoader.load(connection.getMetaData());
            Set<String> primaryKeys = loadPrimaryKeys(connection);
            preparedStatement.setString(1, connection.getSchema());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("table_name");
                    Collection<ColumnMetaData> columns = result.computeIfAbsent(tableName, key -> new LinkedList<>());
                    ColumnMetaData columnMetaData = loadColumnMetaData(dataTypes, primaryKeys, resultSet);
                    columns.add(columnMetaData);
                }
            }
        }
        return result;
    }
    
    private Set<String> loadPrimaryKeys(final Connection connection) throws SQLException {
        Set<String> result = new HashSet<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(PRIMARY_KEY_META_DATA_SQL)) {
            preparedStatement.setString(1, connection.getSchema());
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
    
    private String getTableMetaDataSQL(final Collection<String> existedTables) {
        return existedTables.isEmpty() ? BASIC_TABLE_META_DATA_SQL
                : String.format(TABLE_META_DATA_SQL_WITH_EXISTED_TABLES, existedTables.stream().map(each -> String.format("'%s'", each)).collect(Collectors.joining(",")));
    }
    
    private Map<String, Collection<IndexMetaData>> loadIndexMetaDataMap(final DataSource dataSource) throws SQLException {
        Map<String, Collection<IndexMetaData>> result = new HashMap<>();
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(BASIC_INDEX_META_DATA_SQL)) {
            preparedStatement.setString(1, connection.getSchema());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("tablename");
                    Collection<IndexMetaData> indexes = result.computeIfAbsent(tableName, k -> new LinkedList<>());
                    String indexName = resultSet.getString("indexname");
                    indexes.add(new IndexMetaData(indexName));
                }
            }
        }
        return result;
    }
    
    @Override
    public String getType() {
        return "openGauss";
    }
}
