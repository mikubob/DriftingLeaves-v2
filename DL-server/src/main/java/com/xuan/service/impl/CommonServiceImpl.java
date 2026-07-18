package com.xuan.service.impl;

import cn.hutool.core.lang.UUID;
import com.xuan.constant.MessageConstant;
import com.xuan.exception.UploadFileErrorException;
import com.xuan.properties.ImageProperties;
import com.xuan.service.CommonService;
import com.xuan.utils.AliOssUtil;
import com.xuan.utils.ImageCompressUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 通用服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommonServiceImpl implements CommonService {

    private final ImageProperties imageProperties;
    private final AliOssUtil aliOssUtil;
    private final ImageCompressUtil imageCompressUtil;

    /**
     * 文件上传
     * @param file 文件
     * @return 文件访问路径
     */
    @Override
    public String uploadFile(MultipartFile file) {
        if (file.isEmpty()){
            throw new UploadFileErrorException(MessageConstant.FILE_EMPTY);
        }

        try {
            //获取文件名
            String fileName = file.getOriginalFilename();
            //获取文件名后缀
            String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
            //获取文件字节数组
            byte[] bytes = file.getBytes();

            //如果是图片，先压缩后上传
            if (aliOssUtil.getFileCategory(extension).equals("image")){
                bytes = imageCompressUtil.compress(file);
                extension = imageProperties.getOutPutFormat();
            }

            //获取uuid文件名
            String uuidFileName = UUID.fastUUID() + "." + extension;
            //上传文件
            return aliOssUtil.upload(bytes, extension, uuidFileName);
        }catch (IOException e){
            throw new UploadFileErrorException(MessageConstant.UPLOAD_FAILED);
        }
    }
}
