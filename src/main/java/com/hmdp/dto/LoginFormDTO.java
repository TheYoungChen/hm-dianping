package com.hmdp.dto;

import lombok.Data;

// 登录时输入框中填写的数据
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
