package com.school.student_manage.controller;

import com.school.student_manage.entity.Score;
import com.school.student_manage.service.ScoreService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/score")
public class ScoreController {
    @Resource
    private ScoreService scoreService;

    // 查询列表：支持按学号过滤
    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam(required = false) String stuId) {
        List<Score> list;
        if (stuId != null && !stuId.isEmpty()) {
            list = scoreService.listByStuId(stuId);
        } else {
            list = scoreService.listAll();
        }
        Map<String, Object> res = new HashMap<>();
        res.put("code", 0);
        res.put("msg", "success");
        res.put("data", list);
        return res;
    }

    // 新增成绩
    @PostMapping("/add")
    public Map<String, Object> add(Score score) {
        Map<String, Object> res = new HashMap<>();
        int row = scoreService.addScore(score);
        if (row > 0) {
            res.put("code", 0);
            res.put("msg", "success");
        } else {
            res.put("code", 500);
            res.put("msg", "新增失败");
        }
        return res;
    }

    // 修改成绩
    @PostMapping("/update")
    public Map<String, Object> update(Integer scoreId, String subject, BigDecimal score) {
        Map<String, Object> res = new HashMap<>();
        Score scoreObj = new Score();
        scoreObj.setScoreId(scoreId);
        scoreObj.setSubject(subject);
        scoreObj.setScore(score);
        int row = scoreService.updateScore(scoreObj);
        if (row > 0) {
            res.put("code", 0);
            res.put("msg", "success");
        } else {
            res.put("code", 500);
            res.put("msg", "修改失败");
        }
        return res;
    }

    // 删除成绩
    @DeleteMapping("/delete/{scoreId}")
    public Map<String, Object> delete(@PathVariable Integer scoreId) {
        Map<String, Object> res = new HashMap<>();
        int row = scoreService.deleteById(scoreId);
        if (row > 0) {
            res.put("code", 0);
            res.put("msg", "success");
        } else {
            res.put("code", 500);
            res.put("msg", "删除失败");
        }
        return res;
    }
}