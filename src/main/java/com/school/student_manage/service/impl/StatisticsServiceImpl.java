package com.school.student_manage.service.impl;

import com.school.student_manage.mapper.StatisticsMapper;
import com.school.student_manage.service.StatisticsService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Resource
    private StatisticsMapper statisticsMapper;

    @Override
    public List<Map<String, Object>> getClassScoreStat() {
        return statisticsMapper.selectClassScoreStat();
    }
}