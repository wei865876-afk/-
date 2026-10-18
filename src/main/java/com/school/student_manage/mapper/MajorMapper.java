package com.school.student_manage.mapper;

import com.school.student_manage.entity.Major;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface MajorMapper {
    // 查询所有专业
    List<Major> listAll();

    // 根据院系ID查询下属专业（前端下拉框联动用）
    List<Major> listByDeptId(Integer deptId);

    // 新增专业
    int insert(Major major);

    // 修改专业
    int update(Major major);

    // 根据ID删除专业
    int deleteById(Integer majorId);

    // 根据ID查询单个专业
    Major getById(Integer majorId);
}