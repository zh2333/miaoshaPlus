package com.miaoshaproject.service.impl;

import com.miaoshaproject.dao.PromoDoMapper;
import com.miaoshaproject.dataobject.PromoDo;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EnumBusinessError;
import com.miaoshaproject.service.ItemService;
import com.miaoshaproject.service.PromoService;
import com.miaoshaproject.service.UserService;
import com.miaoshaproject.service.model.ItemModel;
import com.miaoshaproject.service.model.PromoModel;
import com.miaoshaproject.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PromoServiceImpl implements PromoService {
    @Autowired(required = false)
    private PromoDoMapper promoDoMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;
    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        PromoDo promoDo = promoDoMapper.selectByItemId(itemId);

        PromoModel promoModel = this.convertFromDateObject(promoDo);
        if(promoModel == null){
            return null;
        }

        //判断当前时间,秒杀活动是否开始或者当前时间秒杀活动是否已经结束
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else {
            promoModel.setStatus(2);
        }
        return promoModel;
    }

    @Override
    public void publishPromo(Integer promoId) {
        PromoDo promoDo = promoDoMapper.selectByPrimaryKey(promoId);
        if(promoDo.getItemId() == null || promoDo.getItemId() == 0) {
            return;
        }
        ItemModel itemModel = itemService.getItemById(promoDo.getItemId());

        //将库存同步到redis缓存中
        redisTemplate.opsForValue().set("promo_item_stock_"+itemModel.getId(),itemModel.getStock());

        redisTemplate.opsForValue().set("promo_door_count_"+promoId,itemModel.getStock()*5);
    }

    @Override
    public String generateSecondKillToken(Integer promoId,Integer itemId, Integer userId) {

        //先判断库存是否售罄
        if(redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)){
           return null;
        }
        PromoDo promoDo = promoDoMapper.selectByPrimaryKey(promoId);

        PromoModel promoModel = convertFromDateObject(promoDo);
        if(promoModel == null){
            return null;
        }

        //判断当前时间,秒杀活动是否开始或者当前时间秒杀活动是否已经结束
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else {
            promoModel.setStatus(2);
        }
        if(promoModel.getStatus().intValue() != 2) {
            return null;
        }
        //1.校验下单状态,下单的商品是否存在,用户是否合法,购买数量是否正确
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
           return null;
        }

        //校验用户
        UserModel userModel = userService.getUserByIdInCache(userId);
        if(userModel == null){
            return null;
        }

        //获取秒杀大闸的count数量
        long result =  redisTemplate.opsForValue().increment("promo_door_count_"+promoId,-1);
        if(result < 0) {
            return null;
        }

        String  token  = UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue().set("promo_ token_" + promoId+"_userId_"+userId+"_itemId_"+itemId,token);
        redisTemplate.expire("promo_ token_" + promoId+"_userId_"+userId+"_itemId_"+itemId, 5, TimeUnit.MINUTES);

        return token;
    }

    private PromoModel convertFromDateObject(PromoDo promoDo){
        if(promoDo == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDo,promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDo.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDo.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDo.getEndDate()));
        return promoModel;
    }
}
