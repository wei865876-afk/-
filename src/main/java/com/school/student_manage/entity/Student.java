package com.school.student_manage.entity;

public class Student {
    private String stuId;
    private String stuName;
    private String className;
    private String status;
    private Integer classId;
    private String gender;
    // 联查字段
    private String dept;
    private String major;
    private String classname;

    // 完整 getter/setter
    public String getStuId() { return stuId; }
    public void setStuId(String stuId) { this.stuId = stuId; }
    public String getStuName() { return stuName; }
    public void setStuName(String stuName) { this.stuName = stuName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getClassId() { return classId; }
    public void setClassId(Integer classId) { this.classId = classId; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    public String getClassname() { return classname; }
    public void setClassname(String classname) { this.classname = classname; }
}