package com.xuan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 省份访客数量DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProvinceCountDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 省份
    private String province;

    // 访客数量
    private Integer count;
}
