package com.alicp.jetcache.redis;

import com.alicp.jetcache.external.ExternalCacheConfig;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.util.Pool;

/**
 * Created on 2016/10/7.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class RedisCacheConfig<K, V> extends ExternalCacheConfig<K, V> {

    private Pool<Jedis> jedisPool;
    private Pool<Jedis>[] jedisSlavePools;
    private boolean readFromSlave;
    private int[] slaveReadWeights;
    private JedisCluster jedisCluster;

    public Pool<Jedis> getJedisPool() {
        return jedisPool;
    }

    public void setJedisPool(Pool<Jedis> jedisPool) {
        this.jedisPool = jedisPool;
    }

    public Pool<Jedis>[] getJedisSlavePools() {
        return jedisSlavePools;
    }

    public void setJedisSlavePools(Pool<Jedis>... jedisSlavePools) {
        this.jedisSlavePools = jedisSlavePools;
    }

    public boolean isReadFromSlave() {
        return readFromSlave;
    }

    public void setReadFromSlave(boolean readFromSlave) {
        this.readFromSlave = readFromSlave;
    }

    public int[] getSlaveReadWeights() {
        return slaveReadWeights;
    }

    public void setSlaveReadWeights(int... slaveReadWeights) {
        this.slaveReadWeights = slaveReadWeights;
    }

    /**
     * @return the jedisCluster
     */
    public JedisCluster getJedisCluster() {
        return jedisCluster;
    }

    /**
     * @param jedisCluster the jedisCluster to set
     */
    public void setJedisCluster(JedisCluster jedisCluster) {
        this.jedisCluster = jedisCluster;
    }
}
