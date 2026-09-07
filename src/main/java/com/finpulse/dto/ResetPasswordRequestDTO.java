package com.finpulse.dto;

import lombok.Data;

@Data
public class ResetPasswordRequestDTO {

    private String token;
    private String newPassword;
}
