package com.miaoshaproject.service;

import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.service.model.OrderModel;

public interface OrderService {
    //1.通过页面传来秒杀商品id
//    OrderModel  createOrder(Integer userId,Integer itemId,Integer amount) throws BusinessException;

    //2.直接在下单接口内判断对应的商品是否存在秒杀活动中,若存在则以秒杀活动价格下单
    OrderModel  createOrder(Integer userId,Integer itemId,Integer promoId,Integer amount, String stockLogId) throws BusinessException;
}
