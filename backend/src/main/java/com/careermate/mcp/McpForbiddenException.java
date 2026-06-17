package com.careermate.mcp;

public class McpForbiddenException extends RuntimeException {

    public McpForbiddenException() {
        super("forbidden");
    }
}
