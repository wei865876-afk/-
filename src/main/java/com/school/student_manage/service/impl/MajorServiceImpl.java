package com.school.student_manage.service.impl;

import com.school.student_manage.entity.Major;
import com.school.student_manage.mapper.MajorMapper;
import com.school.student_manage.service.MajorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 专业管理业务实现类
 */
@Service
public class MajorServiceImpl implements MajorService {

    @Autowired
    private MajorMapper majorMapper;

    @Override
    public List<Major> listAll() {
        return majorMapper.listAll();
    }

    @Override
    public List<Major> listByDeptId(Integer deptId) {
        return majorMapper.listByDeptId(deptId);
    }

    @Override
    public boolean add(Major major) {
        int rows = majorMapper.insert(major);
        return rows > 0;
    }

    @Override
    public boolean update(Major major) {
        int rows = majorMapper.update(major);
        return rows > 0;
    }

    @Override
    public boolean delete(Integer majorId) {
        int rows = majorMapper.deleteById(majorId);
        return rows > 0;
    }

    @Override
    public Major getById(Integer majorId) {
        return majorMapper.getById(majorId);
    }
}