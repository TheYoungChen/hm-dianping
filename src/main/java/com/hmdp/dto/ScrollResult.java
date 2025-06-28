package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    private List<?> list; // 数据列表
    private Long minTime; // 时间戳
    private Integer offset; // 偏移量
}
