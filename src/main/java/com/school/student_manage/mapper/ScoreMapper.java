package com.school.student_manage.mapper;

import com.school.student_manage.entity.Score;
import java.util.List;

public interface ScoreMapper {
    List<Score> listAll();
    List<Score> listByStuId(String stuId);   // 新增
    int updateScore(Score score);
    int deleteById(Integer scoreId);
    int insert(Score score);
}