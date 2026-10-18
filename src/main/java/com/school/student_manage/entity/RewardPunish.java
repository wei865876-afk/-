package com.school.student_manage.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class RewardPunish {
    private Integer rpId;
    private String stuId;
    private String stuName;
    private String rpType;
    private String levelName;
    private BigDecimal amount;
    private String reason;
    private Date rpDate;
}