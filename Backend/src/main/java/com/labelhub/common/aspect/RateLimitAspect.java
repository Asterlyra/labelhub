package com.labelhub.common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.labelhub.common.annotation.RateLimit;
import com.labelhub.common.exception.RateLimitExceededException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Redis Lua based sliding-window rate limit aspect.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript = new DefaultRedisScript<>();

    @PostConstruct
    public void init() {
        rateLimitScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/rate_limit.lua")));
        rateLimitScript.setResultType(Long.class);
    }

    @Around("@annotation(com.labelhub.common.annotation.RateLimit) || @annotation(com.labelhub.common.annotation.RateLimits)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit[] rateLimits = method.getAnnotationsByType(RateLimit.class);

        if (rateLimits.length == 0) {
            return joinPoint.proceed();
        }

        List<RateLimit> sortedRateLimits = Arrays.stream(rateLimits)
                .sorted(Comparator.comparingInt(this::priority))
                .toList();

        for (RateLimit rateLimit : sortedRateLimits) {
            if (!tryAcquire(method, rateLimit)) {
                return handleRateLimitExceeded(joinPoint, rateLimit);
            }
        }

        return joinPoint.proceed();
    }

    private boolean tryAcquire(Method method, RateLimit rateLimit) {
        long intervalMs = calculateIntervalMs(rateLimit.interval(), rateLimit.timeUnit());
        List<String> keys = generateKeys(method, rateLimit.dimensions());

        Long result = stringRedisTemplate.execute(
                rateLimitScript,
                keys,
                String.valueOf(System.currentTimeMillis()),
                "1",
                String.valueOf(intervalMs),
                String.valueOf(rateLimit.count()),
                UUID.randomUUID().toString()
        );

        boolean allowed = result != null && result == 1L;
        if (!allowed) {
            log.debug("Rate limit exceeded, method={}.{}, keys={}, count={} per {} {}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    keys,
                    rateLimit.count(),
                    rateLimit.interval(),
                    rateLimit.timeUnit());
        }
        return allowed;
    }

    private long calculateIntervalMs(long interval, RateLimit.TimeUnit unit) {
        return switch (unit) {
            case MILLISECONDS -> interval;
            case SECONDS -> interval * 1000;
            case MINUTES -> interval * 60 * 1000;
            case HOURS -> interval * 60 * 60 * 1000;
            case DAYS -> interval * 24 * 60 * 60 * 1000;
        };
    }

    private List<String> generateKeys(Method method, RateLimit.Dimension[] dimensions) {
        List<String> keys = new ArrayList<>();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        String hashTag = "{" + className + ":" + methodName + "}";
        String keyPrefix = "ratelimit:" + hashTag;

        for (RateLimit.Dimension dimension : dimensions) {
            switch (dimension) {
                case GLOBAL -> keys.add(keyPrefix + ":global");
                case IP -> keys.add(keyPrefix + ":ip:" + getClientIp());
                case USER -> keys.add(keyPrefix + ":user:" + getCurrentUserId());
            }
        }

        return keys;
    }

    private Object handleRateLimitExceeded(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        if (!rateLimit.fallback().isBlank()) {
            Method fallbackMethod = findFallbackMethod(joinPoint, rateLimit.fallback());
            if (fallbackMethod != null) {
                if (fallbackMethod.getParameterCount() > 0) {
                    return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
                }
                return fallbackMethod.invoke(joinPoint.getTarget());
            }
        }

        throw new RateLimitExceededException("请求过于频繁，请稍后再试");
    }

    private Method findFallbackMethod(ProceedingJoinPoint joinPoint, String fallbackName) {
        Class<?> targetClass = joinPoint.getTarget().getClass();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?>[] parameterTypes = signature.getParameterTypes();

        try {
            Method method = targetClass.getDeclaredMethod(fallbackName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            try {
                Method method = targetClass.getDeclaredMethod(fallbackName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ex) {
                log.warn("Rate limit fallback method not found: {}.{}", targetClass.getSimpleName(), fallbackName);
                return null;
            }
        }
    }

    private int priority(RateLimit rateLimit) {
        boolean globalOnly = rateLimit.dimensions().length == 1
                && rateLimit.dimensions()[0] == RateLimit.Dimension.GLOBAL;
        return globalOnly ? 0 : 1;
    }

    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = firstNonBlank(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getHeader("Proxy-Client-IP"),
                request.getHeader("WL-Proxy-Client-IP"),
                request.getRemoteAddr()
        );

        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip == null ? "unknown" : ip;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private String getCurrentUserId() {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsString();
            }
        } catch (Exception ignored) {
            return "anonymous";
        }
        return "anonymous";
    }
}
