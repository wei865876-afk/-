package com.school.student_manage.mapper;

import com.school.student_manage.entity.Student;
import java.util.List;

public interface StudentMapper {
    List<Student> selectAll();
    List<Student> selectFullInfo();
    int insert(Student student);
    int updateById(Student student);
    int deleteByStuId(String stuId);
}