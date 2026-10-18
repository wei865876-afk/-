package com.school.student_manage.mapper;

import java.util.List;
import java.util.Map;

public interface StatisticsMapper {
    List<Map<String, Object>> selectClassScoreStat();
}