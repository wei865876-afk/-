package com.school.student_manage.entity;

import lombok.Data;

@Data
public class Dict {
    private Integer id;
    private String dictKey;
    private String dictValue;
    private Integer sort;
}