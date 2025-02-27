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

package org.apache.shardingsphere.data.pipeline.core.check.consistency.algorithm.fixture;

import org.apache.shardingsphere.data.pipeline.api.ingest.record.Column;
import org.apache.shardingsphere.data.pipeline.api.ingest.record.DataRecord;
import org.apache.shardingsphere.data.pipeline.spi.sqlbuilder.PipelineSQLBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class FixturePipelineSQLBuilder implements PipelineSQLBuilder {
    
    @Override
    public String buildInsertSQL(final DataRecord dataRecord) {
        return "";
    }
    
    @Override
    public String buildUpdateSQL(final DataRecord dataRecord, final Collection<Column> conditionColumns) {
        return "";
    }
    
    @Override
    public List<Column> extractUpdatedColumns(final Collection<Column> columns, final DataRecord record) {
        return Collections.emptyList();
    }
    
    @Override
    public String buildDeleteSQL(final DataRecord dataRecord, final Collection<Column> conditionColumns) {
        return "";
    }
    
    @Override
    public String buildTruncateSQL(final String tableName) {
        return "";
    }
    
    @Override
    public String buildCountSQL(final String tableName) {
        return "";
    }
    
    @Override
    public String buildChunkedQuerySQL(final String tableName, final String uniqueKey, final Number startUniqueValue) {
        return "";
    }
    
    @Override
    public String buildCheckEmptySQL(final String tableName) {
        return null;
    }
    
    @Override
    public String buildSplitByPrimaryKeyRangeSQL(final String tableName, final String primaryKey) {
        return "";
    }
    
    @Override
    public Optional<String> buildCRC32SQL(final String tableName, final String column) {
        return Optional.of(String.format("SELECT CRC32(%s) FROM %s", column, tableName));
    }
    
    @Override
    public String getType() {
        return "FIXTURE";
    }
}
