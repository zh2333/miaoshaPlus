package com.miaoshaproject.service;

import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.service.model.ItemModel;

import java.util.List;

public interface ItemService {

    //创建商品
    ItemModel createItem(ItemModel itemModel) throws BusinessException;
    //商品信息浏览
    List<ItemModel> listItem();
    //商品信息详情浏览
    ItemModel getItemById(Integer id);
    //库存扣减
    boolean decreaseStock(Integer itemId,Integer amount);
    //库存回滚
    boolean increaseStock(Integer itemId,Integer amount);
    //验证item以及promo是否有效,model缓存模型
    ItemModel getItemByIdInCache(Integer id);
    //异步更新库存
    boolean asyncDescreaseStock(Integer itemId,Integer amount);
    //商品销量增加
    void increaseSales(Integer itemId,Integer amount) throws BusinessException;
}
