package com.miaoshaproject.validator;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;


import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

/**
 * 自定义校验类
 */
@Component
public class ValidatorImpl implements InitializingBean {
    private Validator validator;
    //实现校验方法并返回校验结果
    public ValidationResult validate(Object  bean){
        ValidationResult result = new ValidationResult();
        //如果不符合校验规则,set就不为空
        Set<ConstraintViolation<Object>> constraintViolationSet =  validator.validate(bean);
        if(constraintViolationSet.size() > 0){
            result.setHasErrors(true);
            //遍历校验结果set,提取那个字段出现那个错误
            constraintViolationSet.forEach(constraintViolation -> {
                String errMsg = constraintViolation.getMessage();
                String propertyName = constraintViolation.getPropertyPath().toString();
                result.getErrorMsgMap().put(propertyName,errMsg);
            });
        }
        return result;
    }

    //Bean初始化完成之后回调这个函数
    @Override
    public void afterPropertiesSet() throws Exception {
        //将hibernate validator通过工厂的初始化方式使其初始化
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }
}
