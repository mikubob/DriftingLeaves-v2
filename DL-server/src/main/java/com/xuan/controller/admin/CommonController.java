package com.xuan.controller.admin;


import com.xuan.result.Result;
import com.xuan.service.CommonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端通用接口
 */
@RestController("adminCommonController")
@RequestMapping("/admin/common")
@Slf4j
@RequiredArgsConstructor
public class CommonController {

    private final CommonService commonService;

    /**
     * 文件上传
     */
    @PostMapping("/upload")
    public Result uploadFile(MultipartFile file){
        log.info("文件上传：{}",file);
        String fileUrl = commonService.uploadFile(file);
        return Result.success(fileUrl);
    }
}
