package com.miaoshaproject.controller;

import com.miaoshaproject.controller.viewobject.ItemVo;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.response.CommonReturnType;
import com.miaoshaproject.service.impl.ItemServiceImpl;
import com.miaoshaproject.service.model.ItemModel;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Controller("/item")
@RequestMapping("/item")
@CrossOrigin(allowCredentials = "true",allowedHeaders = "*")
public class ItemController extends BaseController {

    @Autowired
    private ItemServiceImpl itemService;

    //录入商品
    @RequestMapping(value = "/create",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createItem(@RequestParam(name="title")String title,
                                       @RequestParam(name="description")String description,
                                       @RequestParam(name="price")BigDecimal price,
                                       @RequestParam(name="stock") Integer stock,
                                       @RequestParam(name="imgUrl")String imgUrl) throws BusinessException {

        //封装service请求请求用来创建商品
        ItemModel itemModel = new ItemModel();
        itemModel.setPrice(price);
        itemModel.setStock(stock);
        itemModel.setDescription(description);
        itemModel.setTitle(title);
        itemModel.setImgUrl(imgUrl);

        ItemModel itemModel1ForReturn = itemService.createItem(itemModel);
        ItemVo itemVO = this.convertVoFromModel(itemModel);
        return CommonReturnType.create(itemVO);
    }

    //浏览商品
    @RequestMapping(value = "/getItem",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType getItem(@RequestParam(name = "id")Integer id){
        System.out.println(id);
        ItemModel itemModel = itemService.getItemById(id);

        ItemVo itemVo = convertVoFromModel(itemModel);
        return CommonReturnType.create(itemVo);
    }

    //商品列表页浏览
    @RequestMapping(value = "/list",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType listItem(){
        List<ItemModel> itemModelList = itemService.listItem();
        //使用stream将list内的itemModel转换为ItemVo
        List<ItemVo> itemVoList =  itemModelList.stream().map(itemModel -> {
            ItemVo itemVo = this.convertVoFromModel(itemModel);
            return itemVo;
        }).collect(Collectors.toList());
        return CommonReturnType.create(itemVoList);
    }

    private ItemVo convertVoFromModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemVo itemVo = new ItemVo();
        BeanUtils.copyProperties(itemModel,itemVo);
        if (itemModel.getPromoModel() != null) {
            itemVo.setPromoStatus(itemModel.getPromoModel().getStatus());
            itemVo.setPromoId(itemModel.getPromoModel().getId());
            itemVo.setStartDate(itemModel.getPromoModel().getStartDate().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
            itemVo.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
        } else {
            itemVo.setPromoStatus(0);
        }
        return itemVo;
    }
}
