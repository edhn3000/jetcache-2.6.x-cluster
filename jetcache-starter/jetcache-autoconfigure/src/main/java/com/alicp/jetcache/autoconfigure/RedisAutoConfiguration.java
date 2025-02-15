package com.alicp.jetcache.autoconfigure;

import com.alicp.jetcache.CacheBuilder;
import com.alicp.jetcache.CacheConfigException;
import com.alicp.jetcache.external.ExternalCacheBuilder;
import com.alicp.jetcache.redis.RedisCacheBuilder;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.util.Pool;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created on 2016/11/25.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
@Configuration
@Conditional(RedisAutoConfiguration.RedisCondition.class)
public class RedisAutoConfiguration {

    public static final String AUTO_INIT_BEAN_NAME = "redisAutoInit";

    @Bean(name = AUTO_INIT_BEAN_NAME)
    public RedisAutoInit redisAutoInit() {
        return new RedisAutoInit();
    }

    public static class RedisCondition extends JetCacheCondition {
        public RedisCondition() {
            super("redis");
        }
    }

    public static class RedisAutoInit extends ExternalCacheAutoInit {
        public RedisAutoInit() {
            super("redis");
        }

        @Autowired
        private AutoConfigureBeans autoConfigureBeans;

        @Override
        protected CacheBuilder initCache(ConfigTree ct, String cacheAreaWithPrefix) {
            Object jedisObj = parsePool(ct);
            boolean readFromSlave = Boolean.parseBoolean(ct.getProperty("readFromSlave", "False"));
            RedisCacheBuilder.RedisCacheBuilderImpl builder = RedisCacheBuilder.createRedisCacheBuilder()
                    .readFromSlave(readFromSlave);
            if (jedisObj instanceof Pool) {
                builder.jedisPool((Pool<Jedis>) jedisObj);
            } else if (jedisObj instanceof JedisCluster){
                builder.JedisCluster((JedisCluster) jedisObj);
            } else {
                throw new CacheConfigException("unknown jedis pool config");
            }

            List<Object> slavesPool = null;
            int[] slavesPoolWeights = null;
            ConfigTree slaves = ct.subTree("slaves.");
            Set<String> slaveNames = slaves.directChildrenKeys();
            if (slaveNames.size() > 0) {
                slavesPool = new ArrayList<>();
                slavesPoolWeights = new int[slaveNames.size()];
                int i = 0;
                for (String slaveName: slaveNames) {
                    ConfigTree slaveConfig = slaves.subTree(slaveName + ".");
                    slavesPool.add(parsePool(slaveConfig));
                    slavesPoolWeights[i] = Integer.parseInt(slaveConfig.getProperty("weight","100"));
                    i++;
                }
                builder.slaveReadWeights(slavesPoolWeights);
                if (slavesPool.get(0) instanceof Pool) {
                    builder.jedisSlavePools(slavesPool.toArray(new Pool[0]));
                } else if (slavesPool.get(0) instanceof JedisCluster){
                    builder.JedisCluster((JedisCluster) slavesPool.get(0));
                } else {
                    throw new CacheConfigException("unknown jedis slave pool config");
                }
            }


            parseGeneralConfig(builder, ct);

            // eg: "jedisPool.remote.default"
            autoConfigureBeans.getCustomContainer().put("jedisPool." + cacheAreaWithPrefix, jedisObj);

            return builder;
        }

        private Object parsePool(ConfigTree ct) {
            GenericObjectPoolConfig poolConfig = parsePoolConfig(ct);

            Map<String, Object> cluster = ct.subTree("cluster"/*there is no dot*/).getProperties();
            String host = ct.getProperty("host", (String) null);
            int port = Integer.parseInt(ct.getProperty("port", "0"));
            int timeout = Integer.parseInt(ct.getProperty("timeout", String.valueOf(Protocol.DEFAULT_TIMEOUT)));
            int connectionTimeout = Integer.parseInt(ct.getProperty("connectionTimeout", String.valueOf(timeout)));
            int soTimeout = Integer.parseInt(ct.getProperty("soTimeout", String.valueOf(timeout)));
            String password = ct.getProperty("password", (String) null);
            int database = Integer.parseInt(ct.getProperty("database", String.valueOf(Protocol.DEFAULT_DATABASE)));
            String clientName = ct.getProperty("clientName", (String) null);
            boolean ssl = Boolean.parseBoolean(ct.getProperty("ssl", "false"));

            String masterName = ct.getProperty("masterName", (String) null);
            String sentinels = ct.getProperty("sentinels", (String) null);//ip1:port,ip2:port

            Pool<Jedis> jedisPool;
            if (sentinels == null) {
                if (cluster == null || cluster.size() == 0) {
                    Objects.requireNonNull(host, "host/port or sentinels/masterName is required");
                    if (port == 0) {
                        throw new IllegalStateException("host/port or sentinels/masterName is required");
                    }
                    jedisPool = new JedisPool(poolConfig, host, port, timeout, password, database, clientName, ssl);
                } else {
                    int maxAttempt = Integer.parseInt(ct.getProperty("maxAttempt", "5"));
                    Set<HostAndPort> hostAndPortSet = new HashSet<>();
                    cluster.values().stream().map(String::valueOf).forEach(uri->{
                        if (uri.contains(",")) { // 支持配置文件list一行写法
                            hostAndPortSet.addAll(Arrays.stream(uri.split(",")).map(String::trim)
                                    .map(i -> i.toString().split(":"))
                                    .map(hostAndPort -> new HostAndPort(hostAndPort[0], Integer.parseInt(hostAndPort[1])))
                                    .collect(Collectors.toSet()));
                        } else { // 多行写法
                            String[] hostAndPort = uri.split(":");
                            hostAndPortSet.add(new HostAndPort(hostAndPort[0], Integer.parseInt(hostAndPort[1])));
                        }
                    });
                    return new JedisCluster(hostAndPortSet, connectionTimeout, soTimeout, maxAttempt, password,
                            clientName, poolConfig, ssl);

                }
            } else {
                Objects.requireNonNull(masterName, "host/port or sentinels/masterName is required");
                String[] strings = sentinels.split(",");
                HashSet<String> sentinelsSet = new HashSet<>();
                for (String s : strings) {
                    if (s != null && !s.trim().equals("")) {
                        sentinelsSet.add(s.trim());
                    }
                }
                jedisPool = new JedisSentinelPool(masterName, sentinelsSet, poolConfig, timeout, password, database, clientName);
            }
            return jedisPool;
        }

        private GenericObjectPoolConfig parsePoolConfig(ConfigTree ct) {
            try {
                // Spring Boot 2.0 removed RelaxedDataBinder class. Binder class not exists in 1.X
                if (ClassUtils.isPresent("org.springframework.boot.context.properties.bind.Binder",
                        this.getClass().getClassLoader())) {
                    // Spring Boot 2.0+
                    String prefix = ct.subTree("poolConfig").getPrefix().toLowerCase();

                    // invoke following code by reflect
                    // Binder binder = Binder.get(environment);
                    // return binder.bind(name, Bindable.of(GenericObjectPoolConfig.class)).get();

                    Class<?> binderClass = Class.forName("org.springframework.boot.context.properties.bind.Binder");
                    Class<?> bindableClass = Class.forName("org.springframework.boot.context.properties.bind.Bindable");
                    Class<?> bindResultClass = Class.forName("org.springframework.boot.context.properties.bind.BindResult");
                    Method getMethodOnBinder = binderClass.getMethod("get", Environment.class);
                    Method getMethodOnBindResult = bindResultClass.getMethod("get");
                    Method bindMethod = binderClass.getMethod("bind", String.class, bindableClass);
                    Method ofMethod = bindableClass.getMethod("of", Class.class);
                    Object binder = getMethodOnBinder.invoke(null, environment);
                    Object bindable = ofMethod.invoke(null, GenericObjectPoolConfig.class);
                    Object bindResult = bindMethod.invoke(binder, prefix, bindable);
                    return (GenericObjectPoolConfig) getMethodOnBindResult.invoke(bindResult);
                } else {
                    // Spring Boot 1.X
                    GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
                    Map<String, Object> props = ct.subTree("poolConfig.").getProperties();

                    // invoke following code by reflect
                    //RelaxedDataBinder binder = new RelaxedDataBinder(poolConfig);
                    //binder.bind(new MutablePropertyValues(props));

                    Class<?> relaxedDataBinderClass = Class.forName("org.springframework.boot.bind.RelaxedDataBinder");
                    Class<?> mutablePropertyValuesClass = Class.forName("org.springframework.beans.MutablePropertyValues");
                    Constructor<?> c1 = relaxedDataBinderClass.getConstructor(Object.class);
                    Constructor<?> c2 = mutablePropertyValuesClass.getConstructor(Map.class);
                    Method bindMethod = relaxedDataBinderClass.getMethod("bind", PropertyValues.class);
                    Object binder = c1.newInstance(poolConfig);
                    bindMethod.invoke(binder, c2.newInstance(props));
                    return poolConfig;
                }
            } catch (Throwable ex) {
                throw new CacheConfigException("parse poolConfig fail", ex);
            }

        }
    }


}
