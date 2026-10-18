package com.school.student_manage.entity;

import lombok.Data;
import java.util.Date;

@Data
public class Archive {
    private Integer id;
    private String stuId;
    private String stuName;
    private String fileUrl;
    private Date uploadTime;
}