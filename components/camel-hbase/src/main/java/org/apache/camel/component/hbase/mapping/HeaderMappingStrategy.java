/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.component.hbase.mapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.hbase.HbaseAttribute;
import org.apache.camel.component.hbase.model.HBaseCell;
import org.apache.camel.component.hbase.model.HBaseData;
import org.apache.camel.component.hbase.model.HBaseRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A default {@link CellMappingStrategy} implementation.
 * It distinguishes between multiple cell, by reading headers with index suffix.
 * <p/>
 * In case of multiple headers:
 * <p>First header is expected to have no suffix</p>.
 * <p>Suffixes start from number 2</p>.
 * <p>Suffixes need to be sequential</p>.
 */
public class HeaderMappingStrategy implements CellMappingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(HeaderMappingStrategy.class);

    /**
     * Resolves the cell that the {@link Exchange} refers to.
     *
     * @param message
     * @param index
     * @return
     */
    private HBaseRow resolveRow(Message message, int index) {
        HBaseRow hRow = new HBaseRow();
        HBaseCell hCell = new HBaseCell();

        if (message != null) {
            Object id =  message.getHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(index));
            String rowClassName = message.getHeader(HbaseAttribute.HBASE_ROW_TYPE.asHeader(index), String.class);
            Class rowClass = rowClassName == null || rowClassName.isEmpty() ? String.class : message.getExchange().getContext().getClassResolver().resolveClass(rowClassName);
            String columnFamily = (String) message.getHeader(HbaseAttribute.HBASE_FAMILY.asHeader(index));
            String columnName = (String) message.getHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(index));
            Object value =  message.getHeader(HbaseAttribute.HBASE_VALUE.asHeader(index));

            String valueClassName = message.getHeader(HbaseAttribute.HBASE_VALUE_TYPE.asHeader(index), String.class);
            Class valueClass = valueClassName == null || valueClassName.isEmpty() ? String.class : message.getExchange().getContext().getClassResolver().resolveClass(valueClassName);

            //Id can be accepted as null when using get, scan etc.
            if (id == null && columnFamily == null && columnName == null) {
                return null;
            }

            hRow.setId(id);
            hRow.setRowType(rowClass);
            hCell.setQualifier(columnName);
            hCell.setFamily(columnFamily);
            hCell.setValue(value);
            hCell.setValueType(valueClass);
            hRow.getCells().add(hCell);
        }
        return hRow;
    }

    /**
     * Resolves the cells that the {@link org.apache.camel.Exchange} refers to.
     *
     * @param message
     * @return
     */
    @Override
    public HBaseData resolveModel(Message message) {
        int index = 1;
        HBaseData data = new HBaseData();
        //We use a LinkedHashMap to preserve the order.
        Map<Object, HBaseRow> rows = new LinkedHashMap<Object, HBaseRow>();
        HBaseRow hRow = new HBaseRow();
        while (hRow != null) {
            hRow = resolveRow(message, index++);
            if (hRow != null) {
                if (rows.containsKey(hRow.getId())) {
                    rows.get(hRow.getId()).getCells().addAll(hRow.getCells());
                } else {
                    rows.put(hRow.getId(), hRow);
                }
            }
        }
        for (Map.Entry<Object, HBaseRow> rowEntry : rows.entrySet()) {
            data.getRows().add(rowEntry.getValue());
        }
        return data;
    }


    /**
     * Applies the cells to the {@link org.apache.camel.Exchange}.
     *
     * @param message
     * @param hRows
     */
    public void applyGetResults(Message message, HBaseData data) {
        int index = 1;
        if (data == null || data.getRows() == null) {
            return;
        }

        for (HBaseRow hRow : data.getRows()) {
            if (hRow.getId() != null) {
                Set<HBaseCell> cells = hRow.getCells();
                for (HBaseCell cell : cells) {
                    message.setHeader(HbaseAttribute.HBASE_VALUE.asHeader(index++), getValueForColumn(cells, cell.getFamily(), cell.getQualifier()));
                }
            }
        }
    }


    /**
     * Applies the cells to the {@link org.apache.camel.Exchange}.
     *
     * @param message
     */
    public void applyScanResults(Message message, HBaseData data) {
        int index = 1;
        if (data == null || data.getRows() == null) {
            return;
        }

        for (HBaseRow hRow : data.getRows()) {
            Set<HBaseCell> cells = hRow.getCells();
            for (HBaseCell cell : cells) {
                message.setHeader(HbaseAttribute.HBASE_ROW_ID.asHeader(index), hRow.getId());
                message.setHeader(HbaseAttribute.HBASE_FAMILY.asHeader(index), cell.getFamily());
                message.setHeader(HbaseAttribute.HBASE_QUALIFIER.asHeader(index), cell.getQualifier());
                message.setHeader(HbaseAttribute.HBASE_VALUE.asHeader(index), cell.getValue());

            }
            index++;
        }
    }

    /**
     * Searches a list of cells and returns the value, if family/column matches with the specified.
     *
     * @param family
     * @param qualifier
     * @param cells
     * @return
     */
    private Object getValueForColumn(Set<HBaseCell> cells, String family, String qualifier) {
        if (cells != null) {
            for (HBaseCell cell : cells) {
                if (cell.getQualifier().equals(qualifier) && cell.getFamily().equals(family)) {
                    return cell.getValue();
                }
            }
        }
        return null;
    }
}
