package com.xuan.controller.home;

import com.xuan.annotation.RateLimit;
import com.xuan.dto.VisitorRecordDTO;
import com.xuan.result.Result;
import com.xuan.service.IVisitorService;
import com.xuan.vo.VisitorRecordVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页端访客接口
 */
@RestController("homeVisitorController")
@RequestMapping("/home/visitor")
@Slf4j
@RequiredArgsConstructor
public class VisitorController {

    private final IVisitorService visitorService;

    /**
     * 记录访客访问信息
     * @param visitorRecordDTO
     * @param httpRequest
     * @return
     */
    @PostMapping("/record")
    @RateLimit(type = RateLimit.Type.IP, tokens = 10, burstCapacity = 15,
            timeWindow = 60, message = "请求过于频繁，请稍后再试")
    public Result<VisitorRecordVO> recordVisitorViewInfo(@Valid @RequestBody VisitorRecordDTO visitorRecordDTO,
                                                         HttpServletRequest httpRequest) {
        log.info("记录访客访问信息:{}", visitorRecordDTO);
        VisitorRecordVO visitorRecordVO = visitorService.recordVisitorViewInfo(visitorRecordDTO, httpRequest);
        return Result.success(visitorRecordVO);
    }
}
