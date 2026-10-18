package com.school.student_manage.controller;

import com.school.student_manage.entity.RewardPunish;
import com.school.student_manage.service.RewardPunishService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rewardPunish")
public class RewardPunishController {

    @Autowired
    private RewardPunishService rewardPunishService;

    @GetMapping("/list")
    public R list() {
        List<RewardPunish> list = rewardPunishService.listAll();
        return R.success(list);
    }

    @GetMapping("/listByStuId")
    public R listByStuId(String stuId) {
        List<RewardPunish> list = rewardPunishService.listByStuId(stuId);
        return R.success(list);
    }

    @PostMapping("/add")
    public R add(@RequestBody RewardPunish rewardPunish) {
        boolean flag = rewardPunishService.add(rewardPunish);
        return flag ? R.success("新增奖惩记录成功") : R.error("新增奖惩记录失败");
    }

    @PutMapping("/update")
    public R update(@RequestBody RewardPunish rewardPunish) {
        boolean flag = rewardPunishService.update(rewardPunish);
        return flag ? R.success("修改奖惩记录成功") : R.error("修改奖惩记录失败");
    }

    @DeleteMapping("/delete/{rpId}")
    public R delete(@PathVariable Integer rpId) {
        boolean flag = rewardPunishService.delete(rpId);
        return flag ? R.success("删除奖惩记录成功") : R.error("删除奖惩记录失败");
    }

    @PostMapping("/greedyAllocate")
    public R greedyAllocate(@RequestBody Map<String, Object> params) {
        List<RewardPunish> list = rewardPunishService.greedyAllocate(params);
        return R.success(list);
    }

    @PostMapping("/batchSave")
    public R batchSave(@RequestBody List<RewardPunish> list) {
        boolean flag = rewardPunishService.batchSave(list);
        return flag ? R.success("批量保存成功，共" + list.size() + "条记录") : R.error("保存失败");
    }
}