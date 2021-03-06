/*
 * Copyright (c) 2008-2016, GigaSpaces Technologies, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 *
 */
package com.j_spaces.jdbc.query;

import com.gigaspaces.internal.client.QueryResultTypeInternal;
import com.gigaspaces.internal.client.spaceproxy.ISpaceProxy;
import com.gigaspaces.internal.metadata.ITypeDesc;
import com.gigaspaces.internal.transport.IEntryPacket;
import com.j_spaces.jdbc.AbstractDMLQuery;
import com.j_spaces.jdbc.Stack;
import com.j_spaces.jdbc.builder.QueryTemplatePacket;
import com.j_spaces.jdbc.executor.EntriesCursor;
import com.j_spaces.jdbc.executor.ScanCursor;
import com.j_spaces.jdbc.parser.ColumnNode;
import com.j_spaces.jdbc.parser.ExpNode;

import net.jini.core.transaction.Transaction;


/**
 * @author anna
 * @since 7.0
 */
@com.gigaspaces.api.InternalApi
public class QueryTableData {

    private String _tableName;
    private String _tableAlias;

    // the sequential index of the table
    // in the "from" clause
    private int _tableIndex;

    // the space type descriptor for this table/class
    private ITypeDesc _typeDesc;

    private ExpNode _joinCondition;
    private ExpNode _tableCondition;

    private QueryTableData _joinTable;

    //use thread local because this object is cached and used by several threads
    private ThreadLocal<EntriesCursor> _entriesCursor = new ThreadLocal<EntriesCursor>();

    private boolean _isJoined;
    private boolean _hasAsterixSelectColumns;

    public boolean hasAsterixSelectColumns() {
        return _hasAsterixSelectColumns;
    }

    public void setAsterixSelectColumns(boolean hasAsterixSelectColumns) {
        this._hasAsterixSelectColumns = hasAsterixSelectColumns;
    }

    public String getTableName() {
        return _tableName;
    }

    public void setTableName(String tableName) {
        _tableName = tableName;
    }

    public String getTableAlias() {
        return _tableAlias;
    }

    public void setTableAlias(String tableAlias) {
        _tableAlias = tableAlias;
    }

    public int getTableIndex() {
        return _tableIndex;
    }

    public void setTableIndex(int tableIndex) {
        _tableIndex = tableIndex;
    }

    public ITypeDesc getTypeDesc() {
        return _typeDesc;
    }

    public void setTypeDesc(ITypeDesc typeDesc) {
        _typeDesc = typeDesc;
    }

    public ExpNode getJoinCondition() {
        return _joinCondition;
    }

    public void setJoinCondition(ExpNode joinIndex) {
        _joinCondition = joinIndex;


    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("  QueryTableData\n    [\n        getTableAlias()=");
        builder.append(getTableAlias());

        builder.append(", \n        getTableName()=");
        builder.append(getTableName());

        builder.append(", \n        getTableCondition()=");
        builder.append(getTableCondition());
        builder.append(", \n        getJoinTable()=");

        if (getJoinTable() != null)
            builder.append(getJoinTable().getTableName());
        builder.append(", \n        getJoinExpression()=");
        builder.append(getJoinCondition());
        builder.append(", \n        getTableIndex()=");
        builder.append(getTableIndex());
        builder.append("\n    ]");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((_tableAlias == null) ? 0 : _tableAlias.hashCode());
        result = prime * result + _tableIndex;
        result = prime * result
                + ((_tableName == null) ? 0 : _tableName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        QueryTableData other = (QueryTableData) obj;
        if (_tableAlias == null) {
            if (other._tableAlias != null)
                return false;
        } else if (!_tableAlias.equals(other._tableAlias))
            return false;
        if (_tableIndex != other._tableIndex)
            return false;
        if (_tableName == null) {
            if (other._tableName != null)
                return false;
        } else if (!_tableName.equals(other._tableName))
            return false;
        return true;
    }

    /**
     * @return IEntryPacket
     */
    public IEntryPacket getCurrentEntry() {
        return getEntriesCursor().getCurrentEntry();
    }


    /**
     * @return true _joinTable doesn't finished , false if finished
     */
    public boolean next() {
        if (_joinTable == null)
            return getEntriesCursor().next();

        while (hasNext()) {
            if (_joinTable.next())
                return true;
            //if joined table can't advance any further - increment this table cursor
            // and reset the joined
            if (getEntriesCursor().next()) {
                _joinTable.reset();
            } else {
                return false;
            }

        }
        return false;
    }

    private boolean hasNext() {
        if (getEntriesCursor().isBeforeFirst()) {
            return getEntriesCursor().next();
        }
        return true;

    }

    public void reset() {

        getEntriesCursor().reset();

        if (_joinTable != null)
            _joinTable.reset();
    }


    public QueryTableData getJoinTable() {
        return _joinTable;
    }

    public void setJoinTable(QueryTableData joinTable) {
        _joinTable = joinTable;
    }

    public EntriesCursor getEntriesCursor() {
        return _entriesCursor.get();
    }

    public void setEntriesCursor(EntriesCursor entriesCursor) {
        _entriesCursor.set(entriesCursor);
    }

    public ExpNode getTableCondition() {
        return _tableCondition;
    }

    public void setTableCondition(ExpNode tableCondition) {
        _tableCondition = tableCondition;
    }

    public boolean isJoined() {
        return _isJoined;
    }

    public void setJoined(boolean isJoined) {
        _isJoined = isJoined;
    }

    public void join(ExpNode exp) {

        QueryTableData rightTable = ((ColumnNode) exp.getRightChild()).getColumnData()
                .getColumnTableData();

        // if this table is not joined yet and matches the join condition
        // try to join it with the right table
        if (getJoinTable() == null) {
            if (!rightTable.isJoined() && !rightTable.references(this)) {
                setJoinTable(rightTable);

                rightTable.setJoinCondition(exp);

                rightTable.setJoined(true);

            }
        }

    }

    /**
     * returns true is this table references given dest table
     */
    private boolean references(QueryTableData dest) {

        QueryTableData source = this;

        while (source != null) {
            if (source.equals(dest))
                return true;

            source = source.getJoinTable();
        }

        return false;
    }

    public QueryTemplatePacket getTemplate(QueryResultTypeInternal queryResultType) {
        if (getTableCondition() != null)
            return getTableCondition().getTemplate();
        else
            return new QueryTemplatePacket(this, queryResultType);
    }

    /**
     * Fetch the entries from space that match this table condition
     */
    public void init(ISpaceProxy space, Transaction txn, AbstractDMLQuery query)
            throws Exception {

        IQueryResultSet<IEntryPacket> tableEntries = getTemplate(query.getQueryResultType()).readMultiple(
                space, txn, Integer.MAX_VALUE, query.getReadModifier());

        if (_joinCondition != null)
            setEntriesCursor(_joinCondition.createIndex(this, tableEntries));
        else
            setEntriesCursor(new ScanCursor(tableEntries));

    }

    /**
     * Traverse the expression root(preorder) and create a join index for given table if possible
     */
    public void createJoinIndex(ExpNode root) {
        if (root == null)
            return;

        Stack<ExpNode> stack = new Stack<ExpNode>();

        stack.push(root);
        while (!stack.isEmpty()) {

            ExpNode curr = stack.pop();

            boolean processChildren = curr.createJoinIndex(this);

            if (!processChildren)
                continue;

            if (curr.getLeftChild() != null)
                stack.push(curr.getLeftChild());
            if (curr.getRightChild() != null)
                stack.push(curr.getRightChild());

        }

    }

    public void clear() {
        setEntriesCursor(null);
    }
}
