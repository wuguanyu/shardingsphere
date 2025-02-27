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

package org.apache.shardingsphere.data.pipeline.core.check.consistency.algorithm;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.shardingsphere.data.pipeline.api.check.consistency.DataConsistencyCalculateParameter;
import org.apache.shardingsphere.data.pipeline.core.exception.PipelineDataConsistencyCheckFailedException;
import org.apache.shardingsphere.data.pipeline.core.sqlbuilder.PipelineSQLBuilderFactory;
import org.apache.shardingsphere.data.pipeline.spi.sqlbuilder.PipelineSQLBuilder;
import org.apache.shardingsphere.infra.database.type.DatabaseTypeRegistry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.Properties;

/**
 * Data match data consistency calculate algorithm.
 */
@Getter
@Setter
@Slf4j
public final class DataMatchDataConsistencyCalculateAlgorithm extends AbstractStreamingDataConsistencyCalculateAlgorithm {
    
    private static final Collection<String> SUPPORTED_DATABASE_TYPES = DatabaseTypeRegistry.getDatabaseTypeNames();
    
    private static final String CHUNK_SIZE_KEY = "chunk-size";
    
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    
    private int chunkSize;
    
    private Properties props;
    
    @Override
    public void init() {
        chunkSize = getChunkSize();
    }
    
    private int getChunkSize() {
        int result = Integer.parseInt(props.getProperty(CHUNK_SIZE_KEY, DEFAULT_CHUNK_SIZE + ""));
        if (result <= 0) {
            log.warn("Invalid result={}, use default value", result);
            return DEFAULT_CHUNK_SIZE;
        }
        return result;
    }
    
    @Override
    protected Optional<Object> calculateChunk(final DataConsistencyCalculateParameter parameter) {
        String logicTableName = parameter.getLogicTableName();
        PipelineSQLBuilder sqlBuilder = PipelineSQLBuilderFactory.newInstance(parameter.getDatabaseType());
        String uniqueKey = parameter.getUniqueKey();
        CalculatedResult previousCalculatedResult = (CalculatedResult) parameter.getPreviousCalculatedResult();
        Number startUniqueKeyValue = null != previousCalculatedResult ? previousCalculatedResult.getMaxUniqueKeyValue() : -1;
        String sql = sqlBuilder.buildChunkedQuerySQL(logicTableName, uniqueKey, startUniqueKeyValue);
        try {
            return query(parameter.getDataSource(), sql, uniqueKey, startUniqueKeyValue, chunkSize);
        } catch (final SQLException ex) {
            throw new PipelineDataConsistencyCheckFailedException(String.format("table %s data check failed.", logicTableName), ex);
        }
    }
    
    private Optional<Object> query(final DataSource dataSource, final String sql, final String uniqueKey, final Number startUniqueKeyValue, final int chunkSize) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, startUniqueKeyValue);
            preparedStatement.setInt(2, chunkSize);
            Collection<Collection<Object>> records = new ArrayList<>(chunkSize);
            Number maxUniqueKeyValue = null;
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
                    int columnCount = resultSetMetaData.getColumnCount();
                    Collection<Object> record = new ArrayList<>(columnCount);
                    for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                        record.add(resultSet.getObject(columnIndex));
                    }
                    records.add(record);
                    maxUniqueKeyValue = (Number) resultSet.getObject(uniqueKey);
                }
            }
            return records.isEmpty() ? Optional.empty() : Optional.of(new CalculatedResult(maxUniqueKeyValue, records.size(), records));
        }
    }
    
    @Override
    public String getType() {
        return "DATA_MATCH";
    }
    
    @Override
    public Collection<String> getSupportedDatabaseTypes() {
        return SUPPORTED_DATABASE_TYPES;
    }
    
    @Override
    public String getDescription() {
        return "Match raw data of records.";
    }
    
    @RequiredArgsConstructor
    @Getter
    private static final class CalculatedResult {
        
        @NonNull
        private final Number maxUniqueKeyValue;
        
        private final int recordCount;
        
        private final Collection<Collection<Object>> records;
        
        @SneakyThrows
        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CalculatedResult)) {
                return false;
            }
            final CalculatedResult that = (CalculatedResult) o;
            boolean equalsFirst = new EqualsBuilder().append(getRecordCount(), that.getRecordCount()).append(getMaxUniqueKeyValue(), that.getMaxUniqueKeyValue()).isEquals();
            if (!equalsFirst) {
                return false;
            }
            Iterator<Collection<Object>> thisIterator = this.records.iterator();
            Iterator<Collection<Object>> thatIterator = that.records.iterator();
            while (thisIterator.hasNext() && thatIterator.hasNext()) {
                Collection<Object> thisNext = thisIterator.next();
                Collection<Object> thatNext = thatIterator.next();
                if (thisNext.size() != thatNext.size()) {
                    log.info("record column size not match, size1={}, size2={}, record1={}, record2={}", thisNext.size(), thatNext.size(), thisNext, thatNext);
                    return false;
                }
                Iterator<Object> thisNextIterator = thisNext.iterator();
                Iterator<Object> thatNextIterator = thatNext.iterator();
                while (thisNextIterator.hasNext() && thatNextIterator.hasNext()) {
                    Object thisResult = thisNextIterator.next();
                    Object thatResult = thatNextIterator.next();
                    if (thisResult instanceof SQLXML && thatResult instanceof SQLXML) {
                        return ((SQLXML) thisResult).getString().equals(((SQLXML) thatResult).getString());
                    }
                    if (!new EqualsBuilder().append(thisResult, thatResult).isEquals()) {
                        log.info("record column value not match, value1={}, value2={}, record1={}, record2={}", thisResult, thatResult, thisNext, thatNext);
                        return false;
                    }
                }
            }
            return true;
        }
        
        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37).append(getMaxUniqueKeyValue()).append(getRecordCount()).append(getRecords()).toHashCode();
        }
    }
}
