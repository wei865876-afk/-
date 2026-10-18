package com.school.student_manage.controller;

import com.school.student_manage.entity.Student;
import com.school.student_manage.service.StudentService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @GetMapping("/list")
    public R getStudentList() {
        List<Student> list = studentService.selectAll();
        R r = R.ok();
        r.setData(list);
        return r;
    }

    @GetMapping("/fullList")
    public R fullList() {
        List<Student> list = studentService.getFullInfoList();
        R r = R.ok();
        r.setData(list);
        return r;
    }

    @PostMapping("/add")
    public R addStudent(Student student) {
        boolean flag = studentService.addStudent(student);
        return flag ? R.success("新增学生成功") : R.error("新增学生失败");
    }

    // 修改：使用 @PutMapping
    @PutMapping("/update")
    public R updateStudent(Student student) {
        boolean flag = studentService.updateStudent(student);
        return flag ? R.success("修改成功") : R.error("修改失败");
    }

    @DeleteMapping("/delete/{stuId}")
    public R deleteStudent(@PathVariable String stuId) {
        boolean flag = studentService.deleteStudent(stuId);
        return flag ? R.success("删除成功") : R.error("删除失败");
    }
}