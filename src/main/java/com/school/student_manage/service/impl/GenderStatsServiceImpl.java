package com.school.student_manage.service.impl;

import com.school.student_manage.mapper.GenderStatsMapper;
import com.school.student_manage.service.GenderStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GenderStatsServiceImpl implements GenderStatsService {
    @Autowired
    private GenderStatsMapper genderStatsMapper;

    @Override
    public List<Map<String, Object>> getGenderStats() {
        return genderStatsMapper.callGenderStats();
    }
}