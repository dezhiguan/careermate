package com.careermate.agent.dag;

/** B2：可重试的瞬时错误（超时/限流/脏输出等）。 */
public class TransientException extends RuntimeException {
    public TransientException(String message) {
        super(message);
    }
}
