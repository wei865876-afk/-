package com.school.student_manage.service.impl;

import com.school.student_manage.entity.Student;
import com.school.student_manage.entity.SysUser;
import com.school.student_manage.mapper.StudentMapper;
import com.school.student_manage.mapper.SysUserMapper;
import com.school.student_manage.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentServiceImpl implements StudentService {

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public List<Student> selectAll() {
        return studentMapper.selectAll();
    }

    @Override
    public List<Student> getFullInfoList() {
        return studentMapper.selectFullInfo();
    }

    @Transactional
    @Override
    public boolean addStudent(Student student) {
        int rows = studentMapper.insert(student);
        if (rows <= 0) return false;

        SysUser existUser = sysUserMapper.getByUsername(student.getStuId());
        if (existUser == null) {
            SysUser user = new SysUser();
            user.setUsername(student.getStuId());
            user.setPassword("123456");
            user.setRole("student");
            user.setRealName(student.getStuName());
            sysUserMapper.insertUser(user);
        }
        return true;
    }

    @Override
    public boolean updateStudent(Student student) {
        return studentMapper.updateById(student) > 0;   // 使用 updateById
    }

    @Transactional
    @Override
    public boolean deleteStudent(String stuId) {
        int rows = studentMapper.deleteByStuId(stuId);   // 使用 deleteByStuId
        if (rows > 0) {
            // 同步删除登录账号
            sysUserMapper.deleteByUsername(stuId);
            return true;
        }
        return false;
    }
}