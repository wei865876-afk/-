package com.school.student_manage.service.impl;

import com.school.student_manage.entity.Score;
import com.school.student_manage.mapper.ScoreMapper;
import com.school.student_manage.service.ScoreService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class ScoreServiceImpl implements ScoreService {
    @Resource
    private ScoreMapper scoreMapper;

    @Override
    public List<Score> listAll() {
        return scoreMapper.listAll();
    }

    @Override
    public List<Score> listByStuId(String stuId) {
        return scoreMapper.listByStuId(stuId);
    }

    @Override
    public int updateScore(Score score) {
        return scoreMapper.updateScore(score);
    }

    @Override
    public int deleteById(Integer scoreId) {
        return scoreMapper.deleteById(scoreId);
    }

    @Override
    public int addScore(Score score) {
        return scoreMapper.insert(score);
    }
}