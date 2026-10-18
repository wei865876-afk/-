package com.school.student_manage.util;

public class R {
    private Integer code;
    private String msg;
    private Object data;

    // ========== 原有方法保持不变 ==========
    public static R ok(){
        R r = new R();
        r.setCode(0);
        r.setMsg("操作成功");
        return r;
    }

    public static R error(String msg){
        R r = new R();
        r.setCode(-1);
        r.setMsg(msg);
        return r;
    }

    public R put(String key, Object val){
        this.data = val;
        return this;
    }

    // ========== 新增：适配Controller的success调用 ==========
    /**
     * 成功响应，携带数据
     * 适配 R.success(list)、R.success("字符串") 等所有调用
     */
    public static R success(Object data){
        R r = new R();
        r.setCode(0);
        r.setMsg("操作成功");
        r.setData(data);
        return r;
    }

    /**
     * 成功响应，无数据
     */
    public static R success(){
        return success(null);
    }

    // ========== 原有getter/setter保持不变 ==========
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}