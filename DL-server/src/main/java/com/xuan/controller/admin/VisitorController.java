package com.xuan.controller.admin;


import com.xuan.annotation.OperationLog;
import com.xuan.constant.AdminRoleConstant;
import com.xuan.context.BaseContext;
import com.xuan.dto.VisitorPageQueryDTO;
import com.xuan.enumeration.OperationType;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IVisitorService;
import com.xuan.vo.VisitorQueryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端访客接口
 */
@Slf4j
@RestController("adminVisitorController")
@RequestMapping("/admin/visitor")
@RequiredArgsConstructor
public class VisitorController {

    private final IVisitorService visitorService;

    /**
     * 获取访客列表
     * @param visitorPageQueryDTO
     * @return
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/page")
    public Result<PageResult> getVisitorList(VisitorPageQueryDTO visitorPageQueryDTO) {
        log.info("获取访客列表,{}", visitorPageQueryDTO);
        PageResult<VisitorQueryVO> pageResult = visitorService.pageQuery(visitorPageQueryDTO);

        // 游客角色隐藏IP地址
        if (BaseContext.getCurrentRole().equals(AdminRoleConstant.VISITOR)) {
            pageResult.getRecords().forEach(v -> v.setIp("游客账号无法查看"));
        }

        return Result.success(pageResult);
    }

    /**
     * 批量封禁访客
     * @param ids
     * @return
     */
    @PutMapping("/block")
    @OperationLog(value = OperationType.UPDATE, target = "visitor", targetId = "#ids")
    public Result batchBlock(@RequestParam List<Long> ids) {
        log.info("批量封禁访客: {}", ids);
        visitorService.batchBlock(ids);
        return Result.success();
    }

    /**
     * 批量解封访客
     * @param ids
     * @return
     */
    @PutMapping("/unblock")
    @OperationLog(value = OperationType.UPDATE, target = "visitor", targetId = "#ids")
    public Result<String> batchUnblock(@RequestParam List<Long> ids) {
        log.info("批量解封访客: {}", ids);
        visitorService.batchUnblock(ids);
        return Result.success();
    }

    /**
     * 批量删除访客
     * @param ids
     * @return
     */
    @DeleteMapping
    @OperationLog(value = OperationType.DELETE, target = "visitor", targetId = "#ids")
    public Result<String> batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除访客: {}", ids);
        visitorService.batchDeleteVisitors(ids);
        return Result.success();
    }
}
