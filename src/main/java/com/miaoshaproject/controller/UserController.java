package com.miaoshaproject.controller;

import com.miaoshaproject.controller.viewobject.UserVO;
import com.miaoshaproject.dao.UserDoMapper;
import com.miaoshaproject.dao.UserPasswordDoMapper;
import com.miaoshaproject.dataobject.UserDo;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EnumBusinessError;
import com.miaoshaproject.response.CommonReturnType;
import com.miaoshaproject.service.UserService;
import com.miaoshaproject.service.model.UserModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

@Controller("user")
@RequestMapping("/user")
@CrossOrigin(allowCredentials = "true",allowedHeaders = "*")
public class UserController extends BaseController{

    @Autowired
    private UserService userService;
    @Autowired
    private HttpServletRequest httpServletRequest;

    //用户登录
    @RequestMapping(value = "/login",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
   public CommonReturnType login(@RequestParam(name="telphone")String telphone,@RequestParam(name="password")String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        //入参校验
        if(StringUtils.isEmpty(telphone) || StringUtils.isEmpty(password)){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        //调用业务层方法校验密码是否正确
        UserModel userModel = userService.validateLogin(telphone,this.EncodeByMd5(password));

        //将登陆凭证加入到用户登录成功的session内部
        this.httpServletRequest.getSession().setAttribute("IS_LOGIN",true);
        this.httpServletRequest.getSession().setAttribute("LOGIN_USER",userModel);
        return CommonReturnType.create(null);
   }



    //用户注册
    @RequestMapping(value = "/register",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType register(@RequestParam(name = "telphone")String telphone,
                                     @RequestParam(name = "otpCode")String otpCode,
                                     @RequestParam(name = "name")String name,
                                     @RequestParam(name = "gender")Integer gender,
                                     @RequestParam(name = "age")Integer age,
                                     @RequestParam(name = "password")String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {

        //1.验证验证码是否正确
        String inSessionotpCode = (String)this.httpServletRequest.getSession().getAttribute(telphone);
        if(!com.alibaba.druid.util.StringUtils.equals(otpCode,inSessionotpCode)){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"短信验证码不符合");
        }
        //用户注册流程
        UserModel userModel = new UserModel();
        userModel.setName(name);
        userModel.setAge(age);
        userModel.setGender(new Byte(String.valueOf(gender.intValue())));
        userModel.setTelephone(telphone);
        userModel.setRegisterMode("byPhone");
        userModel.setEncryptPassword(this.EncodeByMd5(password));
        userService.register(userModel);
        return CommonReturnType.create(null);
    }

    //加密用户密码
    public String EncodeByMd5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        //确定计算方式
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        BASE64Encoder base64en = new BASE64Encoder();

        //加密字符串
        String newStr = base64en.encode(md5.digest(str.getBytes("utf-8")));
        return newStr;
    }
    //用户获取opt验证码短信接口
    @RequestMapping(value = "/getotp",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType getOpt(@RequestParam(name = "telphone")String telphone){
        //1.按照一定的规则生成Otp验证码
        Random random = new Random();
        int randomInt = random.nextInt(99999);
        randomInt += 10000;
        String optCode = String.valueOf(randomInt);
        //2.将OPT验证码同对应用户的手机号关联,使用httpSession的方式将验证码和telephone关联
        httpServletRequest.getSession().setAttribute(telphone,optCode);

        //3.将OPT验证码通过短信发送给用户(暂时不做)
        System.out.println("telephone = "+telphone+"& optCode = "+optCode);

        return CommonReturnType.create(null);
    }
    @RequestMapping("/get")
    @ResponseBody
    public CommonReturnType getUser(@RequestParam(name = "id") Integer id) throws BusinessException {//将地址栏上输入的参数和方法形参绑定
        //调用service获取对应用户对象
        UserModel userModel = userService.getUserById(id);
        if(userModel == null){
            throw new BusinessException(EnumBusinessError.USER_NOT_EXIST);
        }
        UserVO userVO =  convertFromModel(userModel);
        return CommonReturnType.create(userVO);
    }

    /**
     * 将核心领域模型用户对象装换为可供UI使用的viewobject
     * @param userModel
     * @return
     */
    public UserVO convertFromModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userModel,userVO);
        return userVO;
    }

}
