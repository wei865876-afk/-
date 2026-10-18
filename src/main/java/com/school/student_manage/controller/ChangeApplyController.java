package com.school.student_manage.controller;

import com.school.student_manage.entity.ChangeApply;
import com.school.student_manage.service.ChangeApplyService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/change")
public class ChangeApplyController {
    @Autowired
    private ChangeApplyService changeApplyService;

    @GetMapping("/apply/list")
    public R applyList(@RequestParam(required = false) String stuId) {
        List<ChangeApply> list;
        if (stuId != null && !stuId.isEmpty()) {
            list = changeApplyService.listByStuId(stuId);
        } else {
            list = changeApplyService.listAll();
        }
        return R.success(list);
    }

    @PostMapping("/apply/add")
    public R applyAdd(ChangeApply apply) {
        boolean flag = changeApplyService.add(apply);
        return flag ? R.success("申请已提交") : R.error("提交失败");
    }

    @GetMapping("/check/list")
    public R checkList() {
        List<ChangeApply> list = changeApplyService.listPending();
        return R.success(list);
    }

    @PutMapping("/check/audit/{applyId}")
    public R audit(@PathVariable Integer applyId, @RequestParam String status) {
        boolean flag = changeApplyService.audit(applyId, status);
        return flag ? R.success("操作成功") : R.error("操作失败");
    }

    @DeleteMapping("/check/delete/{applyId}")
    public R delete(@PathVariable Integer applyId) {
        boolean flag = changeApplyService.delete(applyId);
        return flag ? R.success("删除成功") : R.error("删除失败");
    }
}