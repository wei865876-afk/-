package com.school.student_manage.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ChangeApply {
    private Integer applyId;
    private String stuId;
    private String stuName;
    private String changeType;
    private String remark;
    private String status;
    private Date submitTime;
}