package com.school.student_manage.controller;

import com.school.student_manage.entity.Department;
import com.school.student_manage.service.DepartmentService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 院系管理控制器
 */
@RestController
@RequestMapping("/department")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    /**
     * 查询所有院系列表
     * 请求方式：GET
     * 地址：/department/list
     */
    @GetMapping("/list")
    public R list() {
        List<Department> list = departmentService.listAll();
        return R.success(list);
    }

    /**
     * 新增院系
     * 请求方式：POST
     * 地址：/department/add
     * 请求体：JSON格式的院系信息
     */
    @PostMapping("/add")
    public R add(@RequestBody Department department) {
        boolean flag = departmentService.add(department);
        return flag ? R.success("新增院系成功") : R.error("新增院系失败");
    }

    /**
     * 修改院系信息
     * 请求方式：PUT
     * 地址：/department/update
     * 请求体：JSON格式的院系信息
     */
    @PutMapping("/update")
    public R update(@RequestBody Department department) {
        boolean flag = departmentService.update(department);
        return flag ? R.success("修改院系成功") : R.error("修改院系失败");
    }

    /**
     * 根据ID删除院系
     * 请求方式：DELETE
     * 地址：/department/delete/{deptId}
     */
    @DeleteMapping("/delete/{deptId}")
    public R delete(@PathVariable Integer deptId) {
        boolean flag = departmentService.delete(deptId);
        return flag ? R.success("删除院系成功") : R.error("删除院系失败");
    }
}