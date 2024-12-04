package com.alicp.jetcache.redis;

import java.io.Closeable;
import java.util.Map;
import java.util.Set;

import redis.clients.jedis.Response;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ZAddParams;

/**
 * JedisPipeline
 * 
 * @author fengyq
 * @version 1.0
 * @date 2023-03-21
 * 
 */
interface JedisPipeline extends Closeable {

    void sync();

    void close();

    Response<Boolean> exists(String key);

    Response<Long> expire(String key, int seconds);

    Response<Long> ttl(String key);
    
    Response<Long> del(String key);

//    Response<Long> del(String... keys);

    Response<String> set(String key, String value);

    Response<String> set(String key, String value, SetParams params);

    Response<String> setex(String key, int seconds, String value);

    Response<String> get(String key);

    Response<String> getSet(String key, String value);

//    Response<List<String>> mget(String... keys);

    Response<Long> incr(String key);

    Response<Long> decr(String key);
    
    Response<byte[]> get(byte[] key);

    Response<String> set(byte[] key, byte[] value);

    Response<String> setex(byte[] key, int seconds, byte[] value);

    //// list
    Response<Long> rpush(String key, String... strings);

    Response<Long> lpush(String key, String... strings);

    Response<Long> llen(String key);

    Response<Long> lrem(String key, long count, String value);

    Response<String> lpop(String key);

    Response<String> rpop(String key);

//    Response<List<String>> blpop(int timeout, String... keys);

//    Response<List<String>> brpop(int timeout, String... keys);

//    Response<String> rpoplpush(String srckey, String dstkey);

//    Response<String> brpoplpush(String source, String destination, int timeout);

    //// hash
    Response<Long> hset(String key, String field, String value);

    Response<String> hget(String key, String field);

    Response<Long> hsetnx(String key, String field, String value);

    Response<Long> hdel(String key, String... field);

    Response<Long> hlen(String key);

    Response<Map<String, String>> hgetAll(String key);

    Response<Long> hset(byte[] key, byte[] field, byte[] value);

    Response<byte[]> hget(byte[] key, byte[] field);

    //// set
    Response<Long> sadd(String key, String... members);

    Response<Set<String>> smembers(String key);

    Response<Long> srem(String key, String... members);

    Response<Set<String>> spop(String key, long count);

    Response<Long> scard(String key);

    Response<Boolean> sismember(String key, String member);

    //// zset
    Response<Long> zadd(String key, double score, String member);

    Response<Long> zadd(String key, double score, String member, ZAddParams params);

    Response<Long> zrem(String key, String... members);

    Response<Set<String>> zrange(String key, long start, long stop);

    Response<Set<String>> zrangeByScore(String key, double min, double max);

    Response<Long> zremrangeByScore(String key, double min, double max);

    //// script
//    Response<Object> eval(String script, List<String> keys, List<String> args);

}
