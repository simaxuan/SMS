package com.exam.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端单页应用(SPA)路由回退（单机部署）。
 * <p>前端构建产物(index.html 及 assets/ 下的 JS/CSS/图片等)由 Spring Boot 默认静态资源处理器
 * 从 {@code classpath:/static/} 托管，MIME 类型按扩展名正确推断(.js → application/javascript 等)。
 * 本控制器只负责「客户端路由深链回退」：当用户直接访问或刷新 /students、/students/123 这类
 * <b>不含文件扩展名</b> 的导航路由时，统一转发到 index.html 交给前端 vue-router 渲染。
 * <p>关键点：<b>必须排除静态资源目录 assets 以及带扩展名的请求</b>。否则 @RequestMapping 的优先级
 * 高于静态资源处理器，会拦截 /assets/*.js 并返回 index.html，导致浏览器因严格的模块脚本 MIME 校验
 * 报错 "Expected a JavaScript module script but the server responded with a MIME type of text/html"。
 * 因此这里用负向预查把 assets、api、h2-console、actuator、error 排除在回退之外；
 * 带扩展名的请求(如 /favicon.ico、/assets/x.js)因首段含点，本就不匹配，交由静态处理器正确返回。
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
        "/{path:(?!api|h2-console|actuator|error|assets)[^\\.]*}/**",
        "/{path:(?!api|h2-console|actuator|error|assets)[^\\.]*}/"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
