package com.xuan.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装类
 * 基于 MyBatis-Plus 的 IPage 接口
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    private long page;

    /**
     * 每页显示数量
     */
    private long pageSize;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private long totalPages;

    /**
     * 数据列表
     */
    private List<T> records;
    
    /**
     * 从 MyBatis-Plus IPage 构建 PageResult
     */
    public static <T> PageResult<T> fromIPage(IPage<T> page) {
        return new PageResult<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages(),
                page.getRecords()
        );
    }

    /**
     * 构建空的分页结果
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(1, 10, 0, 0, List.of());
    }

    /**
     * 构建分页结果
     */
    public static <T> PageResult<T> of(List<T> records, long total, long page, long pageSize) {
        long totalPages = (total + pageSize - 1) / pageSize;
        return new PageResult<>(page, pageSize, total, totalPages, records);
    }
}
