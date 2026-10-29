-- Atomic multi-dimension sliding-window rate limiter.
-- KEYS[1..N]: rate limit dimension keys
-- ARGV[1]: current timestamp in milliseconds
-- ARGV[2]: requested permits
-- ARGV[3]: window interval in milliseconds
-- ARGV[4]: max permits in the window
-- ARGV[5]: request id

local now_ms = tonumber(ARGV[1])
local permits = tonumber(ARGV[2])
local interval = tonumber(ARGV[3])
local max_tokens = tonumber(ARGV[4])
local request_id = ARGV[5]

for i, key in ipairs(KEYS) do
    local value_key = key .. ":value"
    local permits_key = key .. ":permits"

    if redis.call("exists", value_key) == 0 then
        redis.call("set", value_key, max_tokens)
    end

    local expired_values = redis.call("zrangebyscore", permits_key, 0, now_ms - interval)
    if #expired_values > 0 then
        local expired_count = 0
        for _, v in ipairs(expired_values) do
            local p = tonumber(string.match(v, ":(%d+)$"))
            if p then
                expired_count = expired_count + p
            end
        end

        redis.call("zremrangebyscore", permits_key, 0, now_ms - interval)

        if expired_count > 0 then
            local curr_v = tonumber(redis.call("get", value_key) or max_tokens)
            local next_v = math.min(max_tokens, curr_v + expired_count)
            redis.call("set", value_key, next_v)
        end
    end

    local current_val = tonumber(redis.call("get", value_key) or max_tokens)
    if current_val < permits then
        return 0
    end
end

for i, key in ipairs(KEYS) do
    local value_key = key .. ":value"
    local permits_key = key .. ":permits"
    local permit_record = request_id .. ":" .. permits

    redis.call("zadd", permits_key, now_ms, permit_record)

    local current_v = tonumber(redis.call("get", value_key) or max_tokens)
    redis.call("set", value_key, current_v - permits)

    local expire_time = math.ceil(interval * 2 / 1000)
    if expire_time < 1 then expire_time = 1 end
    redis.call("expire", value_key, expire_time)
    redis.call("expire", permits_key, expire_time)
end

return 1
