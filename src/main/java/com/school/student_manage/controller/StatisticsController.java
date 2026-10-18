package com.school.student_manage.controller;

import com.school.student_manage.service.StatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/score")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    // 班级成绩统计接口 前端url:/score/stat
    @GetMapping("/stat")
    public Map<String, Object> stat() {
        List<Map<String, Object>> dataList = statisticsService.getClassScoreStat();
        Map<String, Object> res = new HashMap<>();
        // layui固定返回格式 code=0成功
        res.put("code", 0);
        res.put("msg", "success");
        res.put("data", dataList);
        return res;
    }
}