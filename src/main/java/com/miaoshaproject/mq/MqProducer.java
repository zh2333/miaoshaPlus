package com.miaoshaproject.mq;

import com.alibaba.fastjson.JSON;
import com.miaoshaproject.dao.StockLogDoMapper;
import com.miaoshaproject.dataobject.StockLogDo;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.service.OrderService;
import com.sun.xml.internal.bind.v2.TODO;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Component
public class MqProducer {

    private TransactionMQProducer transactionMQProducer;
    private DefaultMQProducer producer;

    //注入配置文件中的mqserver的地址以及topic名称
    @Value("${mq.nameserver.addr}")
    private String nameAddr;

    @Value("${mq.topicname}")
    private String topicName;

    @Autowired
    private OrderService orderService;

    @Autowired(required = false)
    private StockLogDoMapper stockLogDoMapper;
    // @PostConstruct标签表示这个方法在bean初始化完成之后执行
    @PostConstruct
    public void init() throws MQClientException {
        //初始化producer
        producer = new DefaultMQProducer("producer_grope");
        producer.setNamesrvAddr(nameAddr);
        //启动producer
        producer.start();

        //创建事务型producer
        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();

        //TODO(TransactionListener两个方法的实现)
        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object args) {

                Integer itemId = (Integer) ((Map)args).get("itemId");
                Integer amount = (Integer) ((Map)args).get("amount");
                Integer userId = (Integer) ((Map)args).get("userId");
                Integer promoId = (Integer) ((Map)args).get("promoId");
                String stockLogId = (String)((Map)args).get("stockLogId");

                //创建订单
                try {
                    orderService.createOrder(userId,itemId,promoId,amount,stockLogId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    //创建订单失败回滚消息,将库存流水状态设置为3
                    StockLogDo stockLogDo = stockLogDoMapper.selectByPrimaryKey(stockLogId);
                    stockLogDo.setStatus(3);
                    stockLogDoMapper.updateByPrimaryKeySelective(stockLogDo);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                //创建订单成功则将消息提交,此时消费方可以看到这条消息并可以消费
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                //根据是否扣减库存成功,来判断要返回COMMIT,ROLLBACK还是继续UNKNOW
                String jsonStr = new String(msg.getBody());
                Map<String, Object> map = JSON.parseObject(jsonStr,Map.class);
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");
                String stockLogId = (String) map.get("stockLogId");
                StockLogDo stockLogDo = stockLogDoMapper.selectByPrimaryKey(stockLogId);
                if(stockLogDo == null) {
                    return LocalTransactionState.UNKNOW;
                } else if(stockLogDo.getStatus().intValue() == 2){
                    return LocalTransactionState.COMMIT_MESSAGE;
                } else if(stockLogDo.getStatus().intValue() == 1) {
                    return LocalTransactionState.UNKNOW;
                } else {
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
            }
        });

    }

    //事务型同步库存扣减消息
    public boolean transactionAsyncReduceStock(Integer userId,Integer itemId,Integer promoId, Integer amount, String stockLogId) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);

        //argsMap用于传递消息参数
        Map<String, Object> argsMap = new HashMap<>();
        argsMap.put("itemId", itemId);
        argsMap.put("amount", amount);
        argsMap.put("userId",userId);
        argsMap.put("promoId",promoId);
        argsMap.put("stockLogId",stockLogId);
        Message message = new Message(topicName, "increase", JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        TransactionSendResult transactionSendResult = null;
        try {
             transactionSendResult = transactionMQProducer.sendMessageInTransaction(message,argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }
        if(transactionSendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
            return false;
        } else if(transactionSendResult
        .getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE){
            return true;
        } else {
            return false;
        }

    }

    //同步减库存消息
    public boolean asyncReduceStock(Integer id, Integer amount) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", id);
        bodyMap.put("amount", amount);
        Message message = new Message(topicName, "increase", JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));

        try {
            producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return true;

    }

}
