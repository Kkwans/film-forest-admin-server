package com.test.mini.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserVO {
    private Long id;
    private String username;
    private String email;
    private String nickname;
}
