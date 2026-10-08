package com.wkedong.springcloud.seata.order.feign;

import feign.RequestInterceptor;
import io.seata.core.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把全局事务 XID 透传给下游服务。
 *
 * <p>XID 默认只存在于当前线程的 {@code RootContext} 里；HTTP 调用是一次新的请求，
 * 不带头过去，下游就不知道自己属于哪个全局事务，只会当成一个普通的本地事务执行——
 * 表现就是「下游不参与回滚」。Seata 约定的请求头是 {@code TX_XID}。</p>
 *
 * @author wkedong
 */
@Configuration
public class FeignXidConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignXidConfig.class);

    @Bean
    public RequestInterceptor seataXidRequestInterceptor() {
        return template -> {
            String xid = RootContext.getXID();
            if (xid != null) {
                template.header(RootContext.KEY_XID, xid);
                log.info("Feign 透传 XID：{} -> {}", xid, template.url());
            }
        };
    }
}
