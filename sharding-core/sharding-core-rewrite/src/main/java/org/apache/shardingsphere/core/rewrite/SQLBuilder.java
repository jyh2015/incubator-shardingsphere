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

package org.apache.shardingsphere.core.rewrite;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import org.apache.shardingsphere.core.metadata.datasource.ShardingDataSourceMetaData;
import org.apache.shardingsphere.core.optimize.result.insert.InsertOptimizeResultUnit;
import org.apache.shardingsphere.core.rewrite.placeholder.IndexPlaceholder;
import org.apache.shardingsphere.core.rewrite.placeholder.InsertSetPlaceholder;
import org.apache.shardingsphere.core.rewrite.placeholder.InsertValuesPlaceholder;
import org.apache.shardingsphere.core.rewrite.placeholder.SchemaPlaceholder;
import org.apache.shardingsphere.core.rewrite.placeholder.ShardingPlaceholder;
import org.apache.shardingsphere.core.rewrite.placeholder.TablePlaceholder;
import org.apache.shardingsphere.core.route.SQLUnit;
import org.apache.shardingsphere.core.route.type.TableUnit;
import org.apache.shardingsphere.core.rule.DataNode;
import org.apache.shardingsphere.core.rule.MasterSlaveRule;
import org.apache.shardingsphere.core.rule.ShardingRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * SQL builder.
 *
 * @author gaohongtao
 * @author zhangliang
 * @author maxiaoguang
 * @author panjuan
 */
public final class SQLBuilder {
    
    private final List<Object> segments;
    
    private final List<Object> parameters;
    
    private StringBuilder currentSegment;
    
    public SQLBuilder() {
        this(Collections.emptyList());
    }
    
    public SQLBuilder(final List<Object> parameters) {
        segments = new LinkedList<>();
        this.parameters = parameters;
        currentSegment = new StringBuilder();
        segments.add(currentSegment);
    }
    
    /**
     * Append literals.
     *
     * @param literals literals for SQL
     */
    public void appendLiterals(final String literals) {
        currentSegment.append(literals);
    }
    
    /**
     * Append sharding placeholder.
     *
     * @param shardingPlaceholder sharding placeholder
     */
    public void appendPlaceholder(final ShardingPlaceholder shardingPlaceholder) {
        segments.add(shardingPlaceholder);
        currentSegment = new StringBuilder();
        segments.add(currentSegment);
    }
    
    /**
     * Convert to SQL unit.
     *
     * @param tableUnit table unit
     * @param logicAndActualTableMap logic and actual map
     * @param shardingRule sharding rule
     * @param shardingDataSourceMetaData sharding data source meta data
     * @return SQL unit
     */
    public SQLUnit toSQL(final TableUnit tableUnit, final Map<String, String> logicAndActualTableMap, final ShardingRule shardingRule, final ShardingDataSourceMetaData shardingDataSourceMetaData) {
        StringBuilder result = new StringBuilder();
        List<Object> insertParameters = new LinkedList<>();
        for (Object each : segments) {
            if (!(each instanceof ShardingPlaceholder)) {
                result.append(each);
                continue;
            }
            String logicTableName = ((ShardingPlaceholder) each).getLogicTableName();
            String actualTableName = logicAndActualTableMap.get(logicTableName);
            if (each instanceof TablePlaceholder) {
                appendTablePlaceholder((TablePlaceholder) each, actualTableName, result);
            } else if (each instanceof SchemaPlaceholder) {
                appendSchemaPlaceholder(shardingRule, shardingDataSourceMetaData, actualTableName, result);
            } else if (each instanceof IndexPlaceholder) {
                appendIndexPlaceholder((IndexPlaceholder) each, actualTableName, result);
            } else if (each instanceof InsertValuesPlaceholder) {
                appendInsertValuesPlaceholder(tableUnit, (InsertValuesPlaceholder) each, insertParameters, result);
            } else if (each instanceof InsertSetPlaceholder) {
                appendInsertSetPlaceholder(tableUnit, (InsertSetPlaceholder) each, insertParameters, result);
            } else {
                result.append(each);
            }
        }
        return insertParameters.isEmpty() ? new SQLUnit(result.toString(), new ArrayList<>(parameters)) : new SQLUnit(result.toString(), insertParameters);
    }
    
    /**
     * Convert to SQL unit.
     * 
     * @param masterSlaveRule master slave rule
     * @param shardingDataSourceMetaData sharding data source meta data
     * @return SQL
     */
    public String toSQL(final MasterSlaveRule masterSlaveRule, final ShardingDataSourceMetaData shardingDataSourceMetaData) {
        StringBuilder result = new StringBuilder();
        for (Object each : segments) {
            if (each instanceof SchemaPlaceholder) {
                result.append(shardingDataSourceMetaData.getActualDataSourceMetaData(masterSlaveRule.getMasterDataSourceName()).getSchemaName());
            } else {
                result.append(each);
            }
        }
        return result.toString();
    }
    
    /**
     * Convert to SQL unit.
     *
     * @return SQL unit
     */
    public SQLUnit toSQL() {
        StringBuilder result = new StringBuilder();
        List<Object> insertParameters = new LinkedList<>();
        for (Object each : segments) {
            if (!(each instanceof ShardingPlaceholder)) {
                result.append(each);
                continue;
            }
            if (each instanceof InsertValuesPlaceholder) {
                appendInsertValuesPlaceholder(null, (InsertValuesPlaceholder) each, insertParameters, result);
            } else if (each instanceof InsertSetPlaceholder) {
                appendInsertSetPlaceholder(null, (InsertSetPlaceholder) each, insertParameters, result);
            } else {
                result.append(each);
            }
        }
        return insertParameters.isEmpty() ? new SQLUnit(result.toString(), parameters) : new SQLUnit(result.toString(), insertParameters);
    }
    
    private void appendTablePlaceholder(final TablePlaceholder tablePlaceholder, final String actualTableName, final StringBuilder stringBuilder) {
        stringBuilder.append(null == actualTableName ? tablePlaceholder : new TablePlaceholder(actualTableName, tablePlaceholder.getQuoteCharacter()));
    }
    
    private void appendSchemaPlaceholder(final ShardingRule shardingRule, 
                                         final ShardingDataSourceMetaData shardingDataSourceMetaData, final String actualTableName, final StringBuilder stringBuilder) {
        stringBuilder.append(shardingDataSourceMetaData.getActualDataSourceMetaData(shardingRule.getActualDataSourceName(actualTableName)).getSchemaName());
    }
    
    private void appendIndexPlaceholder(final IndexPlaceholder placeholder, final String actualTableName, final StringBuilder stringBuilder) {
        if (Strings.isNullOrEmpty(actualTableName)) {
            stringBuilder.append(placeholder.getQuoteCharacter().getStartDelimiter()).append(placeholder.getLogicIndexName()).append(placeholder.getQuoteCharacter().getEndDelimiter());
        } else {
            stringBuilder.append(placeholder.getQuoteCharacter().getStartDelimiter())
                    .append(placeholder.getLogicIndexName()).append("_").append(actualTableName).append(placeholder.getQuoteCharacter().getEndDelimiter());
        }
    }
    
    private void appendInsertValuesPlaceholder(final TableUnit tableUnit, final InsertValuesPlaceholder placeholder, final List<Object> insertParameters, final StringBuilder stringBuilder) {
        stringBuilder.append(" (").append(Joiner.on(", ").join(placeholder.getColumnNames())).append(") VALUES ");
        for (InsertOptimizeResultUnit each : placeholder.getUnits()) {
            if (isToAppendInsertOptimizeResult(tableUnit, each)) {
                appendInsertOptimizeResult(each, insertParameters, stringBuilder);
            }
        }
        stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
    }
    
    private void appendInsertSetPlaceholder(final TableUnit tableUnit, final InsertSetPlaceholder placeholder, final List<Object> insertParameters, final StringBuilder stringBuilder) {
        stringBuilder.append("SET ");
        for (InsertOptimizeResultUnit each : placeholder.getUnits()) {
            if (isToAppendInsertOptimizeResult(tableUnit, each)) {
                appendInsertOptimizeResult(each, insertParameters, stringBuilder);
            }
        }
        stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
    }
    
    private void appendInsertOptimizeResult(final InsertOptimizeResultUnit unit, final List<Object> insertParameters, final StringBuilder stringBuilder) {
        stringBuilder.append(unit).append(", ");
        insertParameters.addAll(Arrays.asList(unit.getParameters()));
    }
    
    private boolean isToAppendInsertOptimizeResult(final TableUnit tableUnit, final InsertOptimizeResultUnit unit) {
        if (unit.getDataNodes().isEmpty() || null == tableUnit) {
            return true;
        }
        for (DataNode each : unit.getDataNodes()) {
            if (tableUnit.getRoutingTable(each.getDataSourceName(), each.getTableName()).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
