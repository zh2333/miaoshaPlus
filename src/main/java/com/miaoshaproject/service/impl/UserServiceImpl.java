package com.miaoshaproject.service.impl;

import com.miaoshaproject.dao.UserDoMapper;
import com.miaoshaproject.dao.UserPasswordDoMapper;
import com.miaoshaproject.dataobject.UserDo;
import com.miaoshaproject.dataobject.UserPasswordDo;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EnumBusinessError;
import com.miaoshaproject.service.UserService;
import com.miaoshaproject.service.model.UserModel;
import com.miaoshaproject.validator.ValidationResult;
import com.miaoshaproject.validator.ValidatorImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {
    @Autowired(required = false)
    private UserDoMapper userDoMapper;

    @Autowired(required = false)
    private UserPasswordDoMapper userPasswordDoMapper;

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private RedisTemplate redisTemplate;
    @Override
    public UserModel getUserById(Integer id) {
        UserDo userDo = userDoMapper.selectByPrimaryKey(id);
        if(userDo == null){
            return null;
        }
        //通过用户id用户加密密码信息
        UserPasswordDo userPasswordDo = userPasswordDoMapper.selectByUserId(userDo.getId());

        return convertFromDataObject(userDo,userPasswordDo);
    }

    @Override
    public UserModel getUserByIdInCache(Integer id) {
        UserModel userModel = (UserModel)redisTemplate.opsForValue().get("user_validate_"+id);
        if(userModel == null ) {
            userModel = this.getUserById(id);
            redisTemplate.opsForValue().set("user_validate_"+id,userModel);
            redisTemplate.expire("user_validate_"+id,10, TimeUnit.MINUTES);
        }
        return userModel;
    }

    @Override
    @Transactional
    public void register(UserModel userModel) throws BusinessException {
        if(userModel == null){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        //这种写法太恶心,可以基于javax提供的校验器接口自己实现一个校验器
//        if(StringUtils.isEmpty(userModel.getName())
//                || userModel.getGender() == null
//                || userModel.getAge() == null
//                || StringUtils.isEmpty(userModel.getTelephone())){
//            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR);
//        }

        ValidationResult result = validator.validate(userModel);
        if(result.isHasErrors()){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }


        //数据库中存入信息,将密码和其他的用户信息拆开存入两张表
        UserDo userDo = convertFromUserModel(userModel);
        try{
            userDoMapper.insertSelective(userDo);
        }catch(DuplicateKeyException ex){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"手机号已重复");
        }
        userModel.setId(userDo.getId());
        UserPasswordDo userPasswordDo = convertPasswordFromUserModel(userModel);
        userPasswordDoMapper.insertSelective(userPasswordDo);
        return;
    }

    /**
     * 校验用户登录信息
     * 1.根据用户手机号查找出用户的所有信息包括密码
     * 2.校验两个加密后的密码是否相等
     * @param telphone 用户注册手机号
     * @param encrptPassword 用户加密后的密码
     * @return
     * @throws BusinessException
     */
    @Override
    public UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException {
        //1.通过手机号获取用户信息
        UserDo userDo = userDoMapper.selectByTelphone(telphone);
        if(userDo == null){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR);
        }

        UserPasswordDo userPasswordDo = userPasswordDoMapper.selectByUserId(userDo.getId());
        UserModel userModel = convertFromDataObject(userDo,userPasswordDo);
        //2.检查通过手机号查找得到的用户信息是否相匹配
        if(!StringUtils.equals(encrptPassword,userModel.getEncryptPassword())){
            throw new BusinessException(EnumBusinessError.USER_LOGIN_FAIL);
        }
        return userModel;
    }

    /**
     * 从UserModel中分离UserPasswordDo
     * @param userModel
     * @return
     */
    private UserPasswordDo convertPasswordFromUserModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        UserPasswordDo userPasswordDo = new UserPasswordDo();
        userPasswordDo.setEncrptPassword(userModel.getEncryptPassword());
        userPasswordDo.setUserId(userModel.getId());
        return userPasswordDo;
    }

    /**
     * 从UserModel中分离userDo
     * @param userModel
     * @return
     */
    private UserDo convertFromUserModel(UserModel userModel){
        if (userModel == null){
            return null;
        }
        UserDo userDo = new UserDo();
        BeanUtils.copyProperties(userModel,userDo);
        return userDo;
    }

    /**
     * 用户密码和userDo在数据库中是分开存放的,但是逻辑上来说应该是一个实体
     *  因此要新建一个实体用来封装UserPasswordDo和UserDo
     * @param userDo
     * @param userPasswordDo
     * @return
     */
    private UserModel convertFromDataObject(UserDo userDo, UserPasswordDo userPasswordDo){
       if(userDo == null){
           return null;
       }
        UserModel userModel = new UserModel();
        BeanUtils.copyProperties(userDo,userModel);
        if(userPasswordDo != null ){
            userModel.setEncryptPassword(userPasswordDo.getEncrptPassword());
        }
        return userModel;
    }

}
