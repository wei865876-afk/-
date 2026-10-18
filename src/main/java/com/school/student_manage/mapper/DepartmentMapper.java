package com.school.student_manage.mapper;

import com.school.student_manage.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DepartmentMapper {
    // 查询所有院系列表
    List<Department> listAll();

    // 新增院系，返回受影响的行数
    int insert(Department department);

    // 修改院系信息
    int update(Department department);

    // 根据院系ID删除
    int deleteById(Integer deptId);

    // 根据ID查询单个院系详情
    Department getById(Integer deptId);
}