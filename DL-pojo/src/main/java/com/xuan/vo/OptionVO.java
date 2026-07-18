package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 选项VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 标签
    private String label;

    // 值
    private String value;
}
