package com.school.student_manage.service;

import com.school.student_manage.entity.RewardPunish;
import java.util.List;
import java.util.Map;

public interface RewardPunishService {
    List<RewardPunish> listAll();
    List<RewardPunish> listByStuId(String stuId);
    boolean add(RewardPunish rewardPunish);
    boolean update(RewardPunish rewardPunish);
    boolean delete(Integer rpId);
    List<RewardPunish> greedyAllocate(Map<String, Object> params);
    boolean batchSave(List<RewardPunish> list);
}