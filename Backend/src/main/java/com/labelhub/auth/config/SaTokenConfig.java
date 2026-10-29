package com.labelhub.auth.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {

            // 放行：登录、注册、接口文档、静态资源
            SaRouter.match("/api/auth/**", "/doc.html", "/swagger-ui/**", "/v3/api-docs/**").stop();

            // 后台管理接口：需要 PROJECT_ADMIN 角色
            SaRouter.match("/admin/api/**")
                    .check(r -> StpUtil.checkRole("PROJECT_ADMIN"));

            // 前台业务接口：仅需登录
            SaRouter.match("/api/**")
                    .check(r -> StpUtil.checkLogin());

        })).addPathPatterns("/**");
    }
}
