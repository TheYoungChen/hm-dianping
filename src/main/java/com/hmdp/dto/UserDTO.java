package com.hmdp.dto;

import lombok.Data;

// 只存储部分信息
@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
