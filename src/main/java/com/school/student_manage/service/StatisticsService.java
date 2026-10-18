package com.school.student_manage.service;

import java.util.List;
import java.util.Map;

public interface StatisticsService {
    List<Map<String, Object>> getClassScoreStat();
}