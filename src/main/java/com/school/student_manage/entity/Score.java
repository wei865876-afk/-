package com.school.student_manage.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class Score {
    private Integer scoreId;
    private String stuId;
    private String stuName;
    private String subject;
    private BigDecimal score;
}