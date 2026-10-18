package com.school.student_manage.service;

import com.school.student_manage.entity.Student;
import java.util.List;

public interface StudentService {
    List<Student> selectAll();
    List<Student> getFullInfoList();
    boolean addStudent(Student student);
    boolean updateStudent(Student student);   // 调用 updateById
    boolean deleteStudent(String stuId);      // 调用 deleteByStuId
}