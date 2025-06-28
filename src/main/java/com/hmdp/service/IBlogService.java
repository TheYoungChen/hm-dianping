package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    // 查看热门blog
    Result queryHotBlog(Integer current);

    // 根据id查询blog
    Result queryBlogById(Long id);

    // 查看点是否点赞
    Result likeBlog(Long id);

    // 查看点赞列表
    Result queryBlogLikes(Long id);

    // 保存blog
    Result saveBlog(Blog blog);

    // 查看关注列表
    Result queryBlogOfFollow(Long max, Integer offset);
}
