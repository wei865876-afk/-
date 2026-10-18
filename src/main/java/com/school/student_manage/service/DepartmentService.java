package com.school.student_manage.service;

import com.school.student_manage.entity.Department;
import java.util.List;

/**
 * 院系管理业务接口
 */
public interface DepartmentService {
    // 查询所有院系列表
    List<Department> listAll();

    // 新增院系
    boolean add(Department department);

    // 修改院系信息
    boolean update(Department department);

    // 根据ID删除院系
    boolean delete(Integer deptId);

    // 根据ID查询单个院系详情
    Department getById(Integer deptId);
}