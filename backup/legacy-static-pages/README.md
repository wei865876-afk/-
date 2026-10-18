# 旧版前端页面备份

## 这是什么

这里备份的是学籍管理系统**改造前的旧前端页面**（原生 HTML + layui，非 Vue）。
它们原本位于 `src/main/resources/static/` 目录下，于 2026-10-18 从系统中移除，
替换为新的 AI 原生应用（Vue3，入口 `/app/`）。

## 备份内容

| 文件 | 原路径 | 说明 |
|------|--------|------|
| `login.html` | `src/main/resources/static/login.html` | 旧登录页（原生 AJAX 调 `/user/login`） |
| `admin.html` | `src/main/resources/static/admin.html` | 旧管理员工作台 |
| `student.html` | `src/main/resources/static/student.html` | 旧学生工作台 |

## 为什么移除

- 新应用 `/app/` 已完整覆盖全部功能；
- 旧页面与新应用入口重复，容易混淆（例如直接打开 `login.html` 会看到过时界面）。

## 注意：旧后端代码**没有**被移除

`src/main/java/com/school/student_manage/` 下的 `controller` / `service` / `mapper` /
`entity` / `vo` / `util` 等业务代码**全部保留且未改动**，因为新前端仍在调用这些接口：

- `POST /user/login`、`POST /user/logout`、`GET /user/current`
- 以及 AI 模块内部复用的各类业务查询

因此本备份**只包含静态页面**，不含后端代码。

## 如何恢复

把三个文件复制回原目录即可：

```powershell
Copy-Item backup\legacy-static-pages\*.html src\main\resources\static\ -Force
```

同时需要移除 `SpaForwardConfig` 中对应的重定向规则，否则访问仍会跳到新应用：

```java
// src/main/java/com/school/student_manage/config/SpaForwardConfig.java
registry.addRedirectViewController("/", "/app/");
registry.addRedirectViewController("/login.html", "/app/");
registry.addRedirectViewController("/admin.html", "/app/");
registry.addRedirectViewController("/student.html", "/app/");
```

删掉上述四行后重启应用，旧页面即恢复原状。
