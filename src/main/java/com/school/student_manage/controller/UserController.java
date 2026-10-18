package com.school.student_manage.controller;

import com.school.student_manage.entity.SysUser;
import com.school.student_manage.service.SysUserService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private SysUserService userService;

    @PostMapping("/login")
    public R login(String username, String password, String role, HttpSession session) {
        SysUser user = userService.login(username, password, role);
        if (user == null) {
            return R.error("账号、密码或身份错误");
        }
        session.setAttribute("loginUser", user);
        return R.ok().put("user", user);
    }

    // 改为 /current，与学生端保持一致
    @GetMapping("/current")
    public R getCurrentUser(HttpSession session) {
        SysUser user = (SysUser) session.getAttribute("loginUser");
        if (user == null) {
            return R.error("未登录");
        }
        return R.success(user);
    }

    @GetMapping("/list")
    public R userList() {
        List<SysUser> list = userService.listAll();
        return R.success(list);
    }

    @PostMapping("/add")
    public R addUser(SysUser user) {
        boolean flag = userService.addUser(user);
        return flag ? R.success("添加成功") : R.error("添加失败");
    }

    // 改为 @PutMapping，与前端 PUT 请求匹配
    @PutMapping("/update")
    public R updateUser(SysUser user) {
        boolean flag = userService.updateUser(user);
        return flag ? R.success("修改成功") : R.error("修改失败");
    }

    @DeleteMapping("/delete/{id}")
    public R deleteUser(@PathVariable Integer id) {
        boolean flag = userService.deleteUser(id);
        return flag ? R.success("删除成功") : R.error("删除失败");
    }
}