package com.careermate.agent.dag;

/** B2：不可重试的永久错误（参数错误/权限不足/4xx）。 */
public class PermanentException extends RuntimeException {
    public PermanentException(String message) {
        super(message);
    }
}
