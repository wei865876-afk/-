package com.school.student_manage.service.impl;

import com.school.student_manage.entity.Department;
import com.school.student_manage.mapper.DepartmentMapper;
import com.school.student_manage.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 院系管理业务实现类
 */
@Service
public class DepartmentServiceImpl implements DepartmentService {

    // 注入院系Mapper
    @Autowired
    private DepartmentMapper departmentMapper;

    @Override
    public List<Department> listAll() {
        // 直接调用Mapper查询所有数据
        return departmentMapper.listAll();
    }

    @Override
    public boolean add(Department department) {
        // 插入成功返回受影响行数，大于0即为成功
        int rows = departmentMapper.insert(department);
        return rows > 0;
    }

    @Override
    public boolean update(Department department) {
        int rows = departmentMapper.update(department);
        return rows > 0;
    }

    @Override
    public boolean delete(Integer deptId) {
        int rows = departmentMapper.deleteById(deptId);
        return rows > 0;
    }

    @Override
    public Department getById(Integer deptId) {
        return departmentMapper.getById(deptId);
    }
}