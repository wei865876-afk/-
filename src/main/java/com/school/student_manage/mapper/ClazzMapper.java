package com.school.student_manage.mapper;

import com.school.student_manage.entity.Clazz;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ClazzMapper {
    // 查询所有班级
    List<Clazz> listAll();

    // 根据专业ID查询下属班级（前端下拉框联动用）
    List<Clazz> listByMajorId(Integer majorId);

    // 新增班级
    int insert(Clazz clazz);

    // 修改班级
    int update(Clazz clazz);

    // 根据ID删除班级
    int deleteById(Integer classId);

    // 根据ID查询单个班级
    Clazz getById(Integer classId);
}