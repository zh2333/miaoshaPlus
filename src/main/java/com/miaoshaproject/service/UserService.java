package com.miaoshaproject.service;

import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.service.model.UserModel;

public interface UserService {
    //通过用户id 获取用户信息
    UserModel getUserById(Integer id);
    UserModel getUserByIdInCache(Integer id);
    void register(UserModel userModel) throws BusinessException;

    /**
     *
     * @param telphone 用户注册手机号
     * @param encrptPassword 用户加密后的密码
     * @throws BusinessException
     */
    UserModel validateLogin(String telphone,String encrptPassword) throws BusinessException;

}
