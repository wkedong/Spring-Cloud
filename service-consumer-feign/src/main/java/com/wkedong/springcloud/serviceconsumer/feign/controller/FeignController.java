package com.wkedong.springcloud.serviceconsumer.feign.controller;

import com.wkedong.springcloud.serviceconsumer.feign.service.FeignService;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author wkedong
 * FeignDemo
 * 2019/1/14
 */
@RestController
public class FeignController {

    @Autowired
    FeignService feignService;

    @GetMapping("/testFeign")
    public String testFeign() {
        return feignService.testFeign();
    }

    @PostMapping(value = "/testFeignFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String testFeignFile(@RequestParam("file") MultipartFile file) {
        File path = null;
        if (file != null) {
            try {
                String filePath = "D:\\tempFile";
                //判断文件路径下的文件夹是否存在，不存在则创建
                path = new File(filePath);
                if (!path.exists()) {
                    path.mkdirs();
                }
                File tempFile = new File(filePath + "\\" + file.getOriginalFilename());
                // 是否创建文件成功
                boolean isCreateSuccess = tempFile.createNewFile();
                if (isCreateSuccess) {
                    //将文件写入
                    file.transferTo(tempFile);
                }
                DiskFileItem fileItem = (DiskFileItem) new DiskFileItemFactory().createItem("file",
                        MediaType.MULTIPART_FORM_DATA_VALUE, true, file.getOriginalFilename());

                try (InputStream input = new FileInputStream(tempFile); OutputStream os = fileItem.getOutputStream()) {
                    IOUtils.copy(input, os);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid file: " + e, e);
                }
                MultipartFile multi = new CommonsMultipartFile(fileItem);
                return feignService.testFeignFile(multi);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (path != null) {
                    path.delete();
                }
            }
        }
        return "文件有误";
    }

}
