package com.school.student_manage.mapper;

import com.school.student_manage.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SysUserMapper {
    SysUser selectUser(String username, String password, String role);
    List<SysUser> listAll();
    int insertUser(SysUser user);
    int deleteUser(Integer id);
    int updateUser(SysUser user);          // 改名为 updateUser，与 Service 调用一致
    SysUser getByUsername(String username);
    int deleteByUsername(String username); // 新增：按用户名删除
}