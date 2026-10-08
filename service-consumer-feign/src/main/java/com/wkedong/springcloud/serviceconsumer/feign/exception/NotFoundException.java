package com.wkedong.springcloud.serviceconsumer.feign.exception;

/**
 * 下游资源不存在（404）：由自定义 ErrorDecoder 翻译而来。
 *
 * @author wkedong
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
