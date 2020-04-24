package com.miaoshaproject.service.impl;

import com.miaoshaproject.dao.ItemDoMapper;
import com.miaoshaproject.dao.ItemStockDoMapper;
import com.miaoshaproject.dataobject.ItemDo;
import com.miaoshaproject.dataobject.ItemStockDo;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EnumBusinessError;
import com.miaoshaproject.mq.MqProducer;
import com.miaoshaproject.service.ItemService;
import com.miaoshaproject.service.PromoService;
import com.miaoshaproject.service.model.ItemModel;
import com.miaoshaproject.service.model.PromoModel;
import com.miaoshaproject.validator.ValidationResult;
import com.miaoshaproject.validator.ValidatorImpl;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private MqProducer producer;

    @Autowired(required = false)
    private ItemDoMapper itemDoMapper;

    @Autowired(required = false)
    private ItemStockDoMapper itemStockDoMapper;

    @Autowired(required = false)
    private PromoService promoService;

    @Autowired
    private MqProducer mqProducer;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 从ItemModel获取ItemDo
     * @param itemModel
     * @return
     */
    private ItemDo converItemDoFromItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemDo itemDo = new ItemDo();
        BeanUtils.copyProperties(itemModel,itemDo);
        //priice在数据库中是double,而这里不是,因为double从前端传到控制器会产生精度的损失
        itemDo.setPrice(itemModel.getPrice().doubleValue());
        return itemDo;
    }

    /**
     * itemModel获取itemstock
     * @param itemModel
     * @return
     */
    private ItemStockDo converItemStockFromItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemStockDo itemStockDo = new ItemStockDo();
        itemStockDo.setItemId(itemModel.getId());
        itemStockDo.setStock(itemModel.getStock());
        return itemStockDo;
    }

    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        //校验入参
        ValidationResult result =  validator.validate(itemModel);
        if(result.isHasErrors()){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }
        //转换ItemModel-->dataobject
        ItemDo itemDo = this.converItemDoFromItemModel(itemModel);

        //写入数据库
        itemDoMapper.insertSelective(itemDo);
        System.out.println(itemDo.getId());
        //item的id和stock的item_id关联
        itemModel.setId(itemDo.getId());
        ItemStockDo itemStockDo = this.converItemStockFromItemModel(itemModel);
        itemStockDoMapper.insertSelective(itemStockDo);
        return this.getItemById(itemModel.getId());
    }

    @Override
    public List<ItemModel> listItem() {
        List<ItemDo> itemDoList = itemDoMapper.listItem();
        List<ItemModel> itemModelList = itemDoList.stream().map(itemDo -> {
            ItemStockDo itemStockDo = itemStockDoMapper.selectByItemId(itemDo.getId());
            ItemModel itemModel = this.converItemMoelFromItemDoAndItemStockDo(itemDo,itemStockDo);
            return itemModel;
        }).collect(Collectors.toList());
        return itemModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) {
        ItemDo itemDO=itemDoMapper.selectByPrimaryKey(id);
        if(itemDO==null){
            return null;
        }
        //操作获得库存的数量
        ItemStockDo itemStockDO=itemStockDoMapper.selectByItemId(itemDO.getId());

        //将dataobject->model
        ItemModel itemModel=converItemMoelFromItemDoAndItemStockDo(itemDO,itemStockDO);

        //获取活动商品信息
        PromoModel promoModel=promoService.getPromoByItemId(itemModel.getId());
        if(promoModel!=null&&promoModel.getStatus().intValue()!=3){
            itemModel.setPromoModel(promoModel);
        }
        return itemModel;
    }

    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) {
        //先减去redis中的库存
        long  result = redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount*-1);
        if(result > 0){
            //库存更新成功之后将消息投递到nameServer
            boolean  mqResult = mqProducer.asyncReduceStock(itemId,amount);
            //消息投递不成功,恢复redis中的数据并返回false
            if(!mqResult) {
                redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount.intValue());
                return false;
            }
            //更新库存成功
            return true;
        }else {
            return false;
        }
    }

    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) throws BusinessException {
        itemDoMapper.increaseSales(itemId,amount);
    }

    /**
     * 由itemDo和itemStockDo获取ItemModel
     * @param itemDo
     * @param itemStockDo
     * @return
     */
    private ItemModel converItemMoelFromItemDoAndItemStockDo(ItemDo itemDo,ItemStockDo itemStockDo){
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDo,itemModel);
        itemModel.setPrice(new BigDecimal(itemDo.getPrice()));
        itemModel.setStock(itemStockDo.getStock());

        return itemModel;
    }
}
