package com.careermate.mcp;

public final class McpConstants {

    public static final String JSONRPC_VERSION = "2.0";
    public static final String PROTOCOL_VERSION = "2025-06-18";
    public static final String SERVER_NAME = "careermate-mcp";
    public static final String SERVER_VERSION = "0.1.0";

    public static final String URI_RESUME_VERSIONS = "careermate://resume/versions";
    public static final String URI_WORKSPACE_JD = "careermate://workspace/jd";
    public static final String URI_TASKS_TODO = "careermate://tasks/todo";

    public static final String TOOL_SEARCH_JD = "search_jd";
    public static final String TOOL_GET_RESUME = "get_resume";
    public static final String TOOL_CREATE_TASK = "create_task";

    public static final int MAX_QUERY_LENGTH = 500;
    public static final int MAX_TOP_K = 20;
    public static final int DEFAULT_TOP_K = 5;
    public static final int MAX_JD_WORKSPACES = 20;

    public static final int ERROR_PARSE = -32700;
    public static final int ERROR_INVALID_REQUEST = -32600;
    public static final int ERROR_METHOD_NOT_FOUND = -32601;
    public static final int ERROR_INVALID_PARAMS = -32602;
    public static final int ERROR_INTERNAL = -32603;
    public static final int ERROR_UNAUTHORIZED = -32001;
    public static final int ERROR_FORBIDDEN = -32003;
    public static final int ERROR_DISABLED = -32004;

    private McpConstants() {
    }
}
