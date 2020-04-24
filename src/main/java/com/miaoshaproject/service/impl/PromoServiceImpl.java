package com.miaoshaproject.service.impl;

import com.miaoshaproject.dao.PromoDoMapper;
import com.miaoshaproject.dataobject.PromoDo;
import com.miaoshaproject.service.ItemService;
import com.miaoshaproject.service.PromoService;
import com.miaoshaproject.service.model.ItemModel;
import com.miaoshaproject.service.model.PromoModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PromoServiceImpl implements PromoService {
    @Autowired(required = false)
    private PromoDoMapper promoDoMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;
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
