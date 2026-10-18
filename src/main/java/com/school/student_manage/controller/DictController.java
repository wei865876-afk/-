package com.school.student_manage.controller;

import com.school.student_manage.entity.Dict;
import com.school.student_manage.service.DictService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/dict")
public class DictController {
    @Autowired
    private DictService dictService;

    @GetMapping("/list")
    public R list() {
        List<Dict> list = dictService.listAll();
        return R.success(list);
    }

    @PostMapping("/add")
    public R add(Dict dict) {
        boolean flag = dictService.add(dict);
        return flag ? R.success("添加成功") : R.error("添加失败");
    }

    @PutMapping("/update")
    public R update(Dict dict) {
        boolean flag = dictService.update(dict);
        return flag ? R.success("修改成功") : R.error("修改失败");
    }

    @DeleteMapping("/delete/{id}")
    public R delete(@PathVariable Integer id) {
        boolean flag = dictService.delete(id);
        return flag ? R.success("删除成功") : R.error("删除失败");
    }
}