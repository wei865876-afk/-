package com.school.student_manage.mapper;

import com.school.student_manage.entity.RewardPunish;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface RewardPunishMapper {
    List<RewardPunish> listAll();
    List<RewardPunish> listByStuId(String stuId);
    int insert(RewardPunish rewardPunish);
    int update(RewardPunish rewardPunish);
    int deleteById(Integer rpId);
    int batchInsert(List<RewardPunish> list);
    List<Map<String, Object>> selectStudentAvgScore();
}