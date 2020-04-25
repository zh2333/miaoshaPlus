package com.miaoshaproject.service.impl;

import com.miaoshaproject.dao.OrderDoMapper;
import com.miaoshaproject.dao.SequenceDoMapper;
import com.miaoshaproject.dataobject.OrderDo;
import com.miaoshaproject.dataobject.SequenceDo;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EnumBusinessError;
import com.miaoshaproject.service.ItemService;
import com.miaoshaproject.service.OrderService;
import com.miaoshaproject.service.UserService;
import com.miaoshaproject.service.model.ItemModel;
import com.miaoshaproject.service.model.OrderModel;
import com.miaoshaproject.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;
    @Autowired(required = false)
    private OrderDoMapper orderDoMapper;
    @Autowired(required = false)
    private SequenceDoMapper sequenceDoMapper;


    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId,Integer amount) throws BusinessException {
        //1.校验下单状态,下单的商品是否存在,用户是否合法,购买数量是否正确
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"商品信息不存在");
        }

        //校验用户
        UserModel userModel = userService.getUserByIdInCache(userId);
        if(userModel == null){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"用户信息不存在");
        }

        if(amount <=0 || amount > 99){
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"数量信息不正确");
        }

        //校验活动信息
        if(promoId != null){
            //校验对应活动是否存在于这个使适用商品
            if(promoId.intValue() != itemModel.getPromoModel().getId()){
                throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");
            }else if(itemModel.getPromoModel().getStatus().intValue() != 2){
                throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");
            }
        }

        //2.落单减库存
        boolean result = itemService.decreaseStock(itemId,amount);
        if(!result){
            throw new BusinessException(EnumBusinessError.STOCK_NOT_ENOUGH);
        }
        //3.订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if(promoId != null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));

        //4.生成交易流水号(OrderDo中的id)
        orderModel.setId(generateOrderNo());
        OrderDo orderDo = this.convertFromOrderModel(orderModel);
        orderDoMapper.insertSelective(orderDo);

        //加上商品的销量
        itemService.increaseSales(itemId,amount);

        //在最近一个transactional标签执行完成之后执行afterCommit里面的内容
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public  void afterCommit() {
                //异步更新库存
                boolean mqResult = itemService.asyncDescreaseStock(itemId, amount);
                //消息发送失败,需要回滚redis内存
//                if(!mqResult) {
//                    itemService.increaseStock(itemId, amount);
//                    throw new BusinessException(EnumBusinessError.MQ_SEND_FAIL);
//                }
            }
        });



        //5.返回前端
        return orderModel;
    }

    /**
     * 生成交易订单号
     * 生成规则:
     * 1.订单号16位
     * 2.前8位是时间信息,年月日
     * 3.中间6位是自增序列
     * 4.最后两位是分库分表位
     * @return
     */
    //为这个方法开启一个新的事物,五路使用这个方法的事物是否执行成功,这段代码的执行结果都会被提交
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String generateOrderNo(){
        StringBuilder orderNo = new StringBuilder();
        //2.前8位是时间信息,年月日
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-","");
        orderNo.append(nowDate);
        //3.中间6位是自增序列
        int sequence = 0;
        SequenceDo sequenceDo = sequenceDoMapper.getSequenceByName("order_info");
        sequence = sequenceDo.getCurrentValue();
        sequenceDo.setCurrentValue(sequenceDo.getCurrentValue()+sequenceDo.getStep());
        sequenceDoMapper.updateByPrimaryKeySelective(sequenceDo);
        String sequenceStr = String.valueOf(sequence);
        for (int i = 0; i < 6 - sequenceStr.length() ; i++) {
            orderNo.append(0);
        }
        orderNo.append(sequenceStr);
        //4.最后两位是分库分表位,暂时写死
        orderNo.append("00");
        return orderNo.toString();
    }

    /**
     * 从order获取orderDo
     * @param orderModel
     * @return
     */
    private OrderDo convertFromOrderModel(OrderModel orderModel){
        if(orderModel == null){
            return null;
        }
        OrderDo orderDo = new OrderDo();
        BeanUtils.copyProperties(orderModel,orderDo);
        orderDo.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDo.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDo;
    }

}
