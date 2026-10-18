package com.school.student_manage.service;

import com.school.student_manage.entity.SysUser;
import java.util.List;

public interface SysUserService {
    SysUser login(String username, String password, String role);
    List<SysUser> listAll();
    boolean addUser(SysUser user);
    boolean deleteUser(Integer id);
    boolean updateUser(SysUser user);
}