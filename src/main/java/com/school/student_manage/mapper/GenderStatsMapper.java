package com.school.student_manage.mapper;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface GenderStatsMapper {
    List<Map<String, Object>> callGenderStats();
}