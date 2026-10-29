package com.labelhub.common.exception;

public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException() {
        super(ErrorCode.RATE_LIMITED);
    }

    public RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMITED.getCode(), message);
    }
}
