package com.school.student_manage.vo;

import lombok.Data;

@Data
public class GenderCountVO {
    private String deptName;
    private String majorName;
    private Long maleCount;
    private Long femaleCount;
}