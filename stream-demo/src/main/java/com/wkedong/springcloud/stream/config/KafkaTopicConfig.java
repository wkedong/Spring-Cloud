package com.wkedong.springcloud.stream.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * 用代码显式声明输入 topic 的分区数（KafkaAdmin + NewTopic）。
 * <p>
 * 为什么必须显式声明（本模块踩过的坑）：
 * <ul>
 *   <li>Kafka 里「一个分区只能被同一个消费者组内的一个消费者消费」——
 *       所以**分区数就是消费并行度的上限**；</li>
 *   <li>Binder 的消费端在自动创建 topic 时只会建 **1 个分区**（producer.partitionCount 只在
 *       生产端建 topic 时生效，谁先建谁说了算），单分区下「同组多实例负载均衡」根本观察不到：</li>
 *   <li>因此这里用 KafkaAdmin 在应用启动时就把 topic 建成 3 分区，
 *       让 8230/8231 两个实例能被分配到不同分区（见文档「动手验证」）。</li>
 * </ul>
 * 生产环境的真实做法通常是「运维预建 topic 并固定分区数」：分区数只能增不能减，
 * 且增加分区会打乱「相同 key 落同一分区」的路由，进而影响顺序性，改之前要评估。
 *
 * @author wkedong
 */
@Configuration
public class KafkaTopicConfig {

    /** 输入 topic：与 application.yml 里 consume-in-0 / transform-in-0 / streamSend-out-0 的 destination 一致 */
    public static final String INPUT_TOPIC = "stream-demo-topic";

    /**
     * KafkaAdmin：Boot 会用它把下面声明的 {@link NewTopic} 真正建到 broker 上
     * （已存在时不会重复创建；分区数不一致时会按 NewTopic 扩容）。
     * <p>
     * 这里用方法参数把 NewTopic 注入进来，是为了保证「NewTopic Bean 先于 KafkaAdmin 初始化」——
     * KafkaAdmin 是在 afterPropertiesSet 阶段从容器里捞 NewTopic 的，顺序反了就建不出 topic。
     */
    @Bean
    public KafkaAdmin kafkaAdmin(NewTopic streamDemoTopic,
                                 @Value("${spring.cloud.stream.kafka.binder.brokers:127.0.0.1:9092}") String brokers) {
        Map<String, Object> configs = new HashMap<String, Object>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        return new KafkaAdmin(configs);
    }

    /** 输入 topic：3 个分区、单副本（本地单节点 broker 的副本数只能为 1）。 */
    @Bean
    public NewTopic streamDemoTopic(@Value("${stream-demo.topic-partitions:3}") int partitions) {
        return new NewTopic(INPUT_TOPIC, partitions, (short) 1);
    }
}
