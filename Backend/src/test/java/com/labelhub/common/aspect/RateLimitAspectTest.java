package com.labelhub.common.aspect;

import com.labelhub.common.annotation.RateLimit;
import com.labelhub.common.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitAspectTest {

    private StringRedisTemplate redisTemplate;
    private RateLimitAspect aspect;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        aspect = new RateLimitAspect(redisTemplate);
        aspect.init();
    }

    @Test
    void aroundAllowsRequestWhenRedisReturnsSuccess() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("singleLimit");
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint);

        assertEquals("ok", result);
        verify(joinPoint, times(1)).proceed();
        verify(redisTemplate, times(1))
                .execute(any(DefaultRedisScript.class), anyList(), any(), eq("1"), any(), eq("10"), any());
    }

    @Test
    void aroundRejectsRequestWhenRedisReturnsFailure() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("singleLimit");
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(0L);

        assertThrows(RateLimitExceededException.class, () -> aspect.around(joinPoint));

        verify(joinPoint, never()).proceed();
        verify(redisTemplate, times(1)).execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any());
    }

    @Test
    void aroundChecksEveryRepeatableRateLimitBeforeProceeding() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("repeatableLimit");
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint);

        assertEquals("ok", result);
        verify(joinPoint, times(1)).proceed();
        verify(redisTemplate, times(2)).execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any());
    }

    @Test
    void aroundProceedsWhenMethodHasNoRateLimitAnnotation() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("withoutLimit");
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint);

        assertEquals("ok", result);
        verify(joinPoint, times(1)).proceed();
        verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any());
    }

    private ProceedingJoinPoint mockJoinPoint(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterTypes()).thenReturn(new Class<?>[0]);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.getTarget()).thenReturn(new TestController());

        return joinPoint;
    }

    private static class TestController {

        @RateLimit(dimensions = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
        void singleLimit() {
        }

        @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 100, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
        @RateLimit(dimensions = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
        void repeatableLimit() {
        }

        void withoutLimit() {
        }
    }
}
