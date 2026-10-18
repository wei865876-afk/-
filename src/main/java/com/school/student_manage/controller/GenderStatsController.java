package com.school.student_manage.controller;

import com.school.student_manage.service.GenderStatsService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/genderStats")
public class GenderStatsController {
    @Autowired
    private GenderStatsService genderStatsService;

    @GetMapping("/list")
    public R list() {
        List<Map<String, Object>> data = genderStatsService.getGenderStats();
        return R.success(data);
    }
}