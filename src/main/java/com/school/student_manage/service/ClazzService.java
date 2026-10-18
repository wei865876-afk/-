package com.school.student_manage.service;

import com.school.student_manage.entity.Clazz;
import java.util.List;

/**
 * 班级管理业务接口
 */
public interface ClazzService {
    // 查询所有班级列表
    List<Clazz> listAll();

    // 根据专业ID查询下属班级（前端下拉框联动用）
    List<Clazz> listByMajorId(Integer majorId);

    // 新增班级
    boolean add(Clazz clazz);

    // 修改班级信息
    boolean update(Clazz clazz);

    // 根据ID删除班级
    boolean delete(Integer classId);

    // 根据ID查询单个班级详情
    Clazz getById(Integer classId);
}