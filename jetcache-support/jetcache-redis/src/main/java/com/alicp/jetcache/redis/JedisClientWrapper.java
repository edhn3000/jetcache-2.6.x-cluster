package com.alicp.jetcache.redis;

import java.io.Closeable;
import java.util.List;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.params.SetParams;

class JedisClientWrapper implements Closeable {
    
    private Jedis jedis;
    
    private JedisCluster jedisCluster;
    
    JedisClientWrapper(Jedis jedis) {
        this.jedis = jedis;
    }
    
    JedisClientWrapper(JedisCluster jedis) {
        this.jedisCluster = jedis;
    }
    
    byte[] get(byte[] key) {
        if (jedisCluster != null) {
            return jedisCluster.get(key);
        }
        return jedis.get(key);
    }
    
    List<byte[]> mget(final byte[]... keys) {
        if (jedisCluster != null) {
            return jedisCluster.mget(keys);
        }
        return jedis.mget(keys);
    }
    
    String psetex(final byte[] key, final long milliseconds, final byte[] value) {
        if (jedisCluster != null) {
            return jedisCluster.psetex(key, milliseconds, value);
        }
        return jedis.psetex(key, milliseconds, value);
    }
    
    Long del(final byte[] key) {
        if (jedisCluster != null) {
            return jedisCluster.del(key);
        }
        return jedis.del(key);
    }
    
    Long del(final byte[]... keys) {
        if (jedisCluster != null) {
            return jedisCluster.del(keys);
        }
        return jedis.del(keys);
    }
    
    String set(final byte[] key, final byte[] value, final SetParams params) {
        if (jedisCluster != null) {
            return jedisCluster.set(key, value, params);
        }
        return jedis.set(key, value, params);
    }
    
    JedisClusterPipelineWrapper pipelined() {
        if (jedisCluster != null) {
            return new JedisClusterPipelineWrapper(jedisCluster);
        }
        return new JedisClusterPipelineWrapper(jedis.pipelined());
    }
    
    public void close() {
        if (jedis != null) {
            jedis.close();
        }
    }

}
