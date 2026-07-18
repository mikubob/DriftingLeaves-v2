package com.xuan.controller.admin;


import com.xuan.constant.AdminRoleConstant;
import com.xuan.context.BaseContext;
import com.xuan.dto.ViewPageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IViewService;
import com.xuan.vo.ViewQueryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端浏览记录接口
 */
@Slf4j
@RestController("adminViewController")
@RequestMapping("/admin/view")
@RequiredArgsConstructor
public class ViewController {

    private final IViewService viewService;

    /**
     * 获取浏览记录列表
     * @param viewPageQueryDTO
     * @return
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/page")
    public Result<PageResult> getViewList(ViewPageQueryDTO viewPageQueryDTO) {
        log.info("获取浏览记录列表,{}", viewPageQueryDTO);
        PageResult<ViewQueryVO> pageResult = viewService.pageQuery(viewPageQueryDTO);

        // 游客角色隐藏IP地址
        if (BaseContext.getCurrentRole().equals(AdminRoleConstant.VISITOR)) {
            pageResult.getRecords().forEach(v -> v.setIpAddress("游客账号无法查看"));
        }

        return Result.success(pageResult);
    }

    /**
     * 批量删除浏览记录
     * @param ids
     * @return
     */
    @DeleteMapping
    public Result batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除浏览记录,{}", ids);
        viewService.batchDelete(ids);
        return Result.success();
    }
}
