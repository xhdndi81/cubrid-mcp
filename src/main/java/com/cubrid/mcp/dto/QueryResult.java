package com.cubrid.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class QueryResult {
    @JsonProperty("columns")
    private List<ColumnInfo> columns;
    
    @JsonProperty("rows")
    private List<List<Object>> rows;
    
    @JsonProperty("rowCount")
    private int rowCount;
    
    @JsonProperty("truncated")
    private boolean truncated;

    public QueryResult() {
    }

    public QueryResult(List<ColumnInfo> columns, List<List<Object>> rows, int rowCount, boolean truncated) {
        this.columns = columns;
        this.rows = rows;
        this.rowCount = rowCount;
        this.truncated = truncated;
    }

    public List<ColumnInfo> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public void setRows(List<List<Object>> rows) {
        this.rows = rows;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}
