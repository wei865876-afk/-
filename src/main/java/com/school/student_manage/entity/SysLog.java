package com.school.student_manage.entity;

import lombok.Data;
import java.util.Date;

@Data
public class SysLog {
    private Integer id;
    private String username;
    private String operDesc;
    private Date operTime;
    private String ip;
}