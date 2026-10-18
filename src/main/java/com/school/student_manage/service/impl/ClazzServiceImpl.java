package com.school.student_manage.service.impl;

import com.school.student_manage.entity.Clazz;
import com.school.student_manage.mapper.ClazzMapper;
import com.school.student_manage.service.ClazzService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 班级管理业务实现类
 */
@Service
public class ClazzServiceImpl implements ClazzService {

    @Autowired
    private ClazzMapper clazzMapper;

    @Override
    public List<Clazz> listAll() {
        return clazzMapper.listAll();
    }

    @Override
    public List<Clazz> listByMajorId(Integer majorId) {
        return clazzMapper.listByMajorId(majorId);
    }

    @Override
    public boolean add(Clazz clazz) {
        int rows = clazzMapper.insert(clazz);
        return rows > 0;
    }

    @Override
    public boolean update(Clazz clazz) {
        int rows = clazzMapper.update(clazz);
        return rows > 0;
    }

    @Override
    public boolean delete(Integer classId) {
        int rows = clazzMapper.deleteById(classId);
        return rows > 0;
    }

    @Override
    public Clazz getById(Integer classId) {
        return clazzMapper.getById(classId);
    }
}