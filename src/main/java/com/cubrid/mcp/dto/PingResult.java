package com.cubrid.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PingResult {
    @JsonProperty("ok")
    private boolean ok;
    
    @JsonProperty("serverTime")
    private String serverTime;
    
    @JsonProperty("db")
    private String db;

    public PingResult() {
    }

    public PingResult(boolean ok, String serverTime, String db) {
        this.ok = ok;
        this.serverTime = serverTime;
        this.db = db;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getServerTime() {
        return serverTime;
    }

    public void setServerTime(String serverTime) {
        this.serverTime = serverTime;
    }

    public String getDb() {
        return db;
    }

    public void setDb(String db) {
        this.db = db;
    }
}
