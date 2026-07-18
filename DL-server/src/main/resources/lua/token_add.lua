-- Token 添加脚本
-- 原子性地检查数量、添加 Token 并设置过期时间
--
-- KEYS[1]: Redis Key (token:active:userId)
-- ARGV[1]: Token 字符串
-- ARGV[2]: 过期时间 (毫秒)
-- ARGV[3]: 最大数量限制
--
-- 返回值:
--   1: 成功
--   0: 失败 (数量超限)

local key = KEYS[1]
local token = ARGV[1]
local ttl = tonumber(ARGV[2])
local maxCount = tonumber(ARGV[3])

-- 检查当前 Token 数量
local currentCount = redis.call('SCARD', key)

-- 如果已达到上限，返回失败
if currentCount >= maxCount then
    return 0
end

-- 添加 Token 到 Set
redis.call('SADD', key, token)

-- 设置过期时间 (毫秒)
redis.call('PEXPIRE', key, ttl)

return 1
