package com.school.student_manage.service;

import com.school.student_manage.entity.Score;
import java.util.List;

public interface ScoreService {
    List<Score> listAll();
    List<Score> listByStuId(String stuId);   // 新增
    int updateScore(Score score);
    int deleteById(Integer scoreId);
    int addScore(Score score);
}