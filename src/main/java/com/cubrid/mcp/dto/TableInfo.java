package com.cubrid.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TableInfo {
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("type")
    private String type;

    public TableInfo() {
    }

    public TableInfo(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
