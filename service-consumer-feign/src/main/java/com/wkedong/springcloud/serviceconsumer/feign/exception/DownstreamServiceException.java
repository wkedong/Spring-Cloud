package com.wkedong.springcloud.serviceconsumer.feign.exception;

/**
 * 下游服务错误（5xx）：由自定义 ErrorDecoder 翻译而来。
 * <p>
 * 有了它，业务代码可以按语义分别处理：
 * <ul>
 *   <li>捕获 {@link NotFoundException}：资源不存在，返回空结果或默认值；</li>
 *   <li>捕获 {@link DownstreamServiceException}：下游故障，走降级逻辑并告警。</li>
 * </ul>
 * 而不是 catch 一个 FeignException 再去 status() 里猜含义。
 *
 * @author wkedong
 */
public class DownstreamServiceException extends RuntimeException {

    public DownstreamServiceException(String message) {
        super(message);
    }
}
