package com.wkedong.springcloud.serviceconsumer.controller;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * @author wkedong
 * 消费者
 * 2019/1/5
 */
@RestController
public class ConsumerController {
    // log4j 1.x → slf4j
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final RestTemplate restTemplate;

    @Autowired
    public ConsumerController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping(value = "/testGet")
    public String testGet() {
        logger.info("===<call testGet>===");
        return restTemplate.getForObject("http://service-producer/testGet", String.class);
    }

    @PostMapping(value = "/testPost")
    public String testPost(@RequestParam("name") String name) {
        logger.info("===<call testPost>===");
        JSONObject json = new JSONObject();
        json.put("name", name);
        return restTemplate.postForObject("http://service-producer/testPost", json, String.class);
    }

    @PostMapping(value = "/testFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String testFile(@RequestParam("file") MultipartFile file) {
        logger.info("===<call testFile>===");
        File path = null;
        if (file != null) {
            try {
                // 原 demo 硬编码 Windows 路径 D:\tempFile，改为跨平台临时目录
                String filePath = System.getProperty("java.io.tmpdir") + File.separator + "tempFile";
                path = new File(filePath); //判断文件路径下的文件夹是否存在，不存在则创建
                if (!path.exists()) {
                    path.mkdirs();
                }
                File savedFile = new File(filePath + File.separator + file.getOriginalFilename());
                boolean isCreateSuccess = savedFile.createNewFile(); // 是否创建文件成功
                if (isCreateSuccess) {
                    //将文件写入
                    file.transferTo(savedFile);
                }
                HttpHeaders headers = new HttpHeaders();
                FileSystemResource fileSystemResource = new FileSystemResource(savedFile);
                MediaType type = MediaType.parseMediaType("multipart/form-data");
                headers.setContentType(type);
                MultiValueMap<String, Object> param = new LinkedMultiValueMap<>();
                param.add("file", fileSystemResource);
                HttpEntity<MultiValueMap<String, Object>> files = new HttpEntity<>(param, headers);
                return restTemplate.postForObject("http://service-producer/testFile", files, String.class);
            } catch (Exception e) {
                // e.printStackTrace() 改为落日志
                logger.error("multipart 文件临时保存失败", e);
            } finally {
                if (path != null) {
                    path.delete();
                }
            }
        }
        return "文件有误";
    }
}
