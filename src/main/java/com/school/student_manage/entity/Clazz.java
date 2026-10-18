package com.school.student_manage.entity;

import lombok.Data;

@Data
public class Clazz {
    private Integer classId;
    private String className;
    private Integer majorId;
    private Integer studentCount;
}