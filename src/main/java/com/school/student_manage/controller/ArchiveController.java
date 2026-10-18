package com.school.student_manage.controller;

import com.school.student_manage.entity.Archive;
import com.school.student_manage.service.ArchiveService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/archive")
public class ArchiveController {
    @Autowired
    private ArchiveService archiveService;

    @GetMapping("/list")
    public R list() {
        List<Archive> list = archiveService.listAll();
        return R.success(list);
    }

    @PostMapping("/add")
    public R add(Archive archive) {
        boolean flag = archiveService.add(archive);
        return flag ? R.success("添加成功") : R.error("添加失败");
    }

    @PutMapping("/update")
    public R update(Archive archive) {
        boolean flag = archiveService.update(archive);
        return flag ? R.success("修改成功") : R.error("修改失败");
    }

    @DeleteMapping("/delete/{id}")
    public R delete(@PathVariable Integer id) {
        boolean flag = archiveService.delete(id);
        return flag ? R.success("删除成功") : R.error("删除失败");
    }
}