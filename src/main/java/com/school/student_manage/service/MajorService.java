package com.school.student_manage.service;

import com.school.student_manage.entity.Major;
import java.util.List;

/**
 * 专业管理业务接口
 */
public interface MajorService {
    // 查询所有专业列表
    List<Major> listAll();

    // 根据院系ID查询下属专业（前端下拉框联动用）
    List<Major> listByDeptId(Integer deptId);

    // 新增专业
    boolean add(Major major);

    // 修改专业信息
    boolean update(Major major);

    // 根据ID删除专业
    boolean delete(Integer majorId);

    // 根据ID查询单个专业详情
    Major getById(Integer majorId);
}