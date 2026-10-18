package com.school.student_manage.service.impl;

import com.school.student_manage.entity.RewardPunish;
import com.school.student_manage.mapper.RewardPunishMapper;
import com.school.student_manage.service.RewardPunishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class RewardPunishServiceImpl implements RewardPunishService {

    @Autowired
    private RewardPunishMapper rewardPunishMapper;

    @Override
    public List<RewardPunish> listAll() {
        return rewardPunishMapper.listAll();
    }

    @Override
    public List<RewardPunish> listByStuId(String stuId) {
        return rewardPunishMapper.listByStuId(stuId);
    }

    @Override
    public boolean add(RewardPunish rewardPunish) {
        return rewardPunishMapper.insert(rewardPunish) > 0;
    }

    @Override
    public boolean update(RewardPunish rewardPunish) {
        return rewardPunishMapper.update(rewardPunish) > 0;
    }

    @Override
    public boolean delete(Integer rpId) {
        return rewardPunishMapper.deleteById(rpId) > 0;
    }

    @Override
    public List<RewardPunish> greedyAllocate(Map<String, Object> params) {
        List<Map<String, Object>> studentList = rewardPunishMapper.selectStudentAvgScore();
        if (studentList == null || studentList.isEmpty()) {
            return new ArrayList<>();
        }

        List<RewardLevel> levels = new ArrayList<>();
        levels.add(new RewardLevel("一等奖学金", Integer.parseInt(params.get("level1Quota").toString()),
                new BigDecimal(params.get("level1Amount").toString())));
        levels.add(new RewardLevel("二等奖学金", Integer.parseInt(params.get("level2Quota").toString()),
                new BigDecimal(params.get("level2Amount").toString())));
        levels.add(new RewardLevel("三等奖学金", Integer.parseInt(params.get("level3Quota").toString()),
                new BigDecimal(params.get("level3Amount").toString())));
        levels.sort(Comparator.comparing(RewardLevel::getAmount).reversed());

        List<RewardPunish> resultList = new ArrayList<>();
        for (Map<String, Object> stu : studentList) {
            String stuId = stu.get("stu_id").toString();
            String stuName = stu.get("stu_name").toString();
            BigDecimal avgScore = new BigDecimal(stu.get("avg_score").toString());

            for (RewardLevel level : levels) {
                if (level.getQuota() > 0) {
                    RewardPunish rp = new RewardPunish();
                    rp.setStuId(stuId);
                    rp.setStuName(stuName);
                    rp.setRpType("奖励");
                    rp.setLevelName(level.getLevelName());
                    rp.setAmount(level.getAmount());
                    rp.setReason("贪心算法自动分配：平均分" + avgScore.setScale(2, BigDecimal.ROUND_HALF_UP) + "分");
                    resultList.add(rp);

                    level.setQuota(level.getQuota() - 1);
                    break;
                }
            }

            if (levels.stream().allMatch(l -> l.getQuota() <= 0)) {
                break;
            }
        }
        return resultList;
    }

    @Override
    public boolean batchSave(List<RewardPunish> list) {
        if (list == null || list.isEmpty()) return false;
        return rewardPunishMapper.batchInsert(list) > 0;
    }

    static class RewardLevel {
        private String levelName;
        private int quota;
        private BigDecimal amount;

        public RewardLevel(String levelName, int quota, BigDecimal amount) {
            this.levelName = levelName;
            this.quota = quota;
            this.amount = amount;
        }

        public String getLevelName() { return levelName; }
        public int getQuota() { return quota; }
        public void setQuota(int quota) { this.quota = quota; }
        public BigDecimal getAmount() { return amount; }
    }
}