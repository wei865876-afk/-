package com.school.student_manage.controller;

import com.school.student_manage.entity.Major;
import com.school.student_manage.service.MajorService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 专业管理控制器
 */
@RestController
@RequestMapping("/major")
public class MajorController {

    @Autowired
    private MajorService majorService;

    /**
     * 查询所有专业列表
     */
    @GetMapping("/list")
    public R list() {
        List<Major> list = majorService.listAll();
        return R.success(list);
    }

    /**
     * 根据院系ID查询下属专业（前端下拉框联动）
     * 地址：/major/listByDeptId?deptId=1
     */
    @GetMapping("/listByDeptId")
    public R listByDeptId(Integer deptId) {
        List<Major> list = majorService.listByDeptId(deptId);
        return R.success(list);
    }

    /**
     * 新增专业
     */
    @PostMapping("/add")
    public R add(@RequestBody Major major) {
        boolean flag = majorService.add(major);
        return flag ? R.success("新增专业成功") : R.error("新增专业失败");
    }

    /**
     * 修改专业信息
     */
    @PutMapping("/update")
    public R update(@RequestBody Major major) {
        boolean flag = majorService.update(major);
        return flag ? R.success("修改专业成功") : R.error("修改专业失败");
    }

    /**
     * 根据ID删除专业
     */
    @DeleteMapping("/delete/{majorId}")
    public R delete(@PathVariable Integer majorId) {
        boolean flag = majorService.delete(majorId);
        return flag ? R.success("删除专业成功") : R.error("删除专业失败");
    }
}