package com.miaoshaproject.service.model;

import java.math.BigDecimal;

//用户下单的交易模型
public class OrderModel {
    //订单号是具有明显的含义的
    private String id;
    //下单的用户的id
    private Integer userId;
    //用户购买的商品的id
    private Integer itemId;

    //秒杀商品id,若非空,则以秒杀商品下单
    private Integer promoId;


    //没见商品的数量
    private Integer amount;
    //商品价格
    private BigDecimal itemPrice;

    public Integer getPromoId() {
        return promoId;
    }

    public void setPromoId(Integer promoId) {
        this.promoId = promoId;
    }

    //总金额
    private BigDecimal orderPrice;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public BigDecimal getOrderPrice() {
        return orderPrice;
    }

    public void setOrderPrice(BigDecimal orderPrice) {
        this.orderPrice = orderPrice;
    }

    public BigDecimal getItemPrice() {
        return itemPrice;
    }

    public void setItemPrice(BigDecimal itemPrice) {
        this.itemPrice = itemPrice;
    }

}
