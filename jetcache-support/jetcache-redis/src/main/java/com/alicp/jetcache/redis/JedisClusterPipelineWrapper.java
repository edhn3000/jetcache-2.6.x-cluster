package com.alicp.jetcache.redis;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import redis.clients.jedis.BinaryJedisCluster;
import redis.clients.jedis.Client;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisClusterConnectionHandler;
import redis.clients.jedis.JedisClusterInfoCache;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSlotBasedConnectionHandler;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.PipelineBase;
import redis.clients.jedis.exceptions.JedisMovedDataException;
import redis.clients.jedis.exceptions.JedisRedirectionException;
import redis.clients.jedis.util.JedisClusterCRC16;
import redis.clients.jedis.util.SafeEncoder;

/**
 * JedisClusterPipeline
 * 
 * @author fengyq
 * @version 1.0
 * @date 2023-05-12
 * 
 */
class JedisClusterPipelineWrapper extends PipelineBase implements Closeable {
    
    private Pipeline pipelined;
    
    private static final Field FIELD_CONNECTION_HANDLER;
    private static final Field FIELD_CACHE; 
    private static final Field FIELD_CLIENT; 
    static {
        FIELD_CONNECTION_HANDLER = getField(BinaryJedisCluster.class, "connectionHandler");
        FIELD_CACHE = getField(JedisClusterConnectionHandler.class, "cache");
        FIELD_CLIENT = getField(Pipeline.class, "client");
    }
        
    private JedisSlotBasedConnectionHandler connectionHandler;
    private JedisClusterInfoCache clusterInfoCache;
    private Queue<Client> clients = new LinkedList<Client>();   // 根据顺序存储每个命令对应的Client
    private Map<JedisPool, Jedis> jedisMap = new HashMap<JedisPool, Jedis>();   // 用于缓存连接
    private boolean hasDataInBuf = false;   // 是否有数据在缓存区
    
    public JedisClusterPipelineWrapper(JedisCluster jedis) {
        connectionHandler = getValue(jedis, FIELD_CONNECTION_HANDLER);
        clusterInfoCache = getValue(connectionHandler, FIELD_CACHE);
    }
    
    public JedisClusterPipelineWrapper(Pipeline pipelined) {
        this.pipelined = pipelined;
    }

    /**
     * 刷新集群信息，当集群信息发生变更时调用
     * @param 
     * @return
     */
    private void refreshCluster() {
        connectionHandler.renewSlotCache();
    }

    /**
     * 同步读取所有数据. 与syncAndReturnAll()相比，sync()只是没有对数据做反序列化
     */
    public void sync() {
        if (this.pipelined != null) {
            this.pipelined.sync();
            return ;
        }
        innerSync(null);
    }

    /**
     * 同步读取所有数据 并按命令顺序返回一个列表
     *
     * @return 按照命令的顺序返回所有的数据
     */
    public List<Object> syncAndReturnAll() {
        List<Object> responseList = new ArrayList<>();

        innerSync(responseList);

        return responseList;
    }


    private void innerSync(List<Object> responseList) {
        HashSet<Client> clientSet = new HashSet<>();
        
        try {
            for (Client client : clients) {
                // 在sync()调用时其实是不需要解析结果数据的，但是如果不调用get方法，发生了JedisMovedDataException这样的错误应用是不知道的，此时通过get()来触发错误。
                Object data = generateResponse(client.getOne()).get();
                if (responseList != null) {
                    responseList.add(data);
                }
                
                // size相同说明所有的client都已经添加，就不用再调用add方法了
                if (clientSet.size() != jedisMap.size()) {
                    clientSet.add(client);
                }
            }
        } catch (JedisRedirectionException e) {
            if (e instanceof JedisMovedDataException) {
                // if MOVED redirection occurred, rebuilds cluster's slot cache,
                // recommended by Redis cluster specification
                refreshCluster();
            }

            throw e;
        } finally {
            if (clientSet.size() != jedisMap.size()) {
                // 所有还没有执行过的client要保证执行(flush)，防止放回连接池后后面的命令被污染
                for (Jedis jedis : jedisMap.values()) {
                    if (clientSet.contains(jedis.getClient())) {
                        continue;
                    }
                    
                    flushCachedData(jedis);
                }
            }
            
            hasDataInBuf = false;
            close();
        }
    }
    
    @Override
    public void close() {
        if (this.pipelined != null) {
            this.pipelined.close();
            return ;
        }
        clean();
        
        clients.clear();
        
        for (Jedis jedis : jedisMap.values()) {
            if (hasDataInBuf) {
                flushCachedData(jedis);
            }
            
            jedis.close();
        }
        
        jedisMap.clear();
        
        hasDataInBuf = false;
    }
    
    private void flushCachedData(Jedis jedis) {
        try {
            jedis.getClient().getOne();
        } catch (RuntimeException ex) {
        }
    }
    
    @Override
    protected Client getClient(String key) {
        if (this.pipelined != null) {
            return getValue(this.pipelined, FIELD_CLIENT);
        }
        
        byte[] bKey = SafeEncoder.encode(key);

        return getClient(bKey);
    }

    @Override
    protected Client getClient(byte[] key) {
        if (this.pipelined != null) {
            return getValue(this.pipelined, FIELD_CLIENT);
        }
        Jedis jedis = getJedis(JedisClusterCRC16.getSlot(key));
        
        Client client = jedis.getClient();
        clients.add(client);
        
        return client;
    }
    
    private Jedis getJedis(int slot) {
        JedisPool pool = clusterInfoCache.getSlotPool(slot);
        
        // 根据pool从缓存中获取Jedis
        Jedis jedis = jedisMap.get(pool);
        if (null == jedis) {
            jedis = pool.getResource();
            jedisMap.put(pool, jedis);
        }

        hasDataInBuf = true;
        return jedis;
    }
    
    private static Field getField(Class<?> cls, String fieldName) {
        try {
            Field field = cls.getDeclaredField(fieldName);
            field.setAccessible(true);
            
            return field;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("cannot find or access field '" + fieldName + "' from " + cls.getName(), e);
        } catch (SecurityException e) {
            throw new RuntimeException("cannot find or access field '" + fieldName + "' from " + cls.getName(), e);
        }
    }
    
    @SuppressWarnings({"unchecked" })
    private static <T> T getValue(Object obj, Field field) {
        try {
            return (T)field.get(obj);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
