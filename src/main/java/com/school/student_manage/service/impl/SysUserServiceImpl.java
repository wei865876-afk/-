package com.school.student_manage.service.impl;

import com.school.student_manage.entity.SysUser;
import com.school.student_manage.mapper.SysUserMapper;
import com.school.student_manage.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SysUserServiceImpl implements SysUserService {
    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public SysUser login(String username, String password, String role) {
        return sysUserMapper.selectUser(username, password, role);
    }

    @Override
    public List<SysUser> listAll() {
        return sysUserMapper.listAll();
    }

    @Override
    public boolean addUser(SysUser user) {
        return sysUserMapper.insertUser(user) > 0;
    }

    @Override
    public boolean updateUser(SysUser user) {
        return sysUserMapper.updateUser(user) > 0;   // 改用 updateUser
    }

    @Override
    public boolean deleteUser(Integer id) {
        return sysUserMapper.deleteUser(id) > 0;
    }
}