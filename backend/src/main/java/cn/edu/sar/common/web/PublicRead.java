package cn.edu.sar.common.web;

import java.lang.annotation.*;

/** Explicit opt-in for approved public GET/HEAD projections. It never permits anonymous writes. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicRead {}
