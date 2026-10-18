package com.school.student_manage.controller;

import com.school.student_manage.entity.Clazz;
import com.school.student_manage.service.ClazzService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班级管理控制器
 * 注意：实体类名为Clazz，对应数据库class表
 */
@RestController
@RequestMapping("/class")
public class ClazzController {

    @Autowired
    private ClazzService clazzService;

    /**
     * 查询所有班级列表
     */
    @GetMapping("/list")
    public R list() {
        List<Clazz> list = clazzService.listAll();
        return R.success(list);
    }

    /**
     * 根据专业ID查询下属班级（前端下拉框联动）
     * 地址：/class/listByMajorId?majorId=1
     */
    @GetMapping("/listByMajorId")
    public R listByMajorId(Integer majorId) {
        List<Clazz> list = clazzService.listByMajorId(majorId);
        return R.success(list);
    }

    /**
     * 新增班级
     */
    @PostMapping("/add")
    public R add(@RequestBody Clazz clazz) {
        boolean flag = clazzService.add(clazz);
        return flag ? R.success("新增班级成功") : R.error("新增班级失败");
    }

    /**
     * 修改班级信息
     */
    @PutMapping("/update")
    public R update(@RequestBody Clazz clazz) {
        boolean flag = clazzService.update(clazz);
        return flag ? R.success("修改班级成功") : R.error("修改班级失败");
    }

    /**
     * 根据ID删除班级
     */
    @DeleteMapping("/delete/{classId}")
    public R delete(@PathVariable Integer classId) {
        boolean flag = clazzService.delete(classId);
        return flag ? R.success("删除班级成功") : R.error("删除班级失败");
    }
}