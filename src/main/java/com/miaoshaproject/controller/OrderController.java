package com.miaoshaproject.controller;

import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EnumBusinessError;
import com.miaoshaproject.mq.MqProducer;
import com.miaoshaproject.response.CommonReturnType;
import com.miaoshaproject.service.ItemService;
import com.miaoshaproject.service.OrderService;
import com.miaoshaproject.service.PromoService;
import com.miaoshaproject.service.model.OrderModel;
import com.miaoshaproject.service.model.UserModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.*;

@Controller("order")
@RequestMapping("/order")
@CrossOrigin(allowCredentials = "true",allowedHeaders = "*")
public class OrderController extends BaseController{
    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired(required = false)
    private OrderService orderService;

    @Autowired
    private MqProducer mqProducer;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private PromoService promoService;

    @Autowired(required = false)
    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(20);
    }

    //生成秒杀令牌
    @RequestMapping(value = "/generatetoken",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generateToken(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name = "promoId",required = false)Integer promoId) throws BusinessException {
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EnumBusinessError.USER_NOT_LOGIN,"请先登录后再下单");
        }
        UserModel userModel = (UserModel)redisTemplate.opsForValue().get(token);
        if(userModel == null) {
            throw new BusinessException(EnumBusinessError.USER_NOT_LOGIN,"请先登录后再下单");
        }

        //获取秒杀访问令牌
        String promoToken = promoService.generateSecondKillToken(promoId,itemId,userModel.getId());
        if(promoToken == null) {
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"生成令牌失败");
        }
        return CommonReturnType.create(token);
    }

    //封装下单请求
    @RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name = "amount")Integer amount,
                                        @RequestParam(name = "promoId",required = false)Integer promoId,
                                        @RequestParam(name = "promoToken",required = false)String promoToken) throws BusinessException {

        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EnumBusinessError.USER_NOT_LOGIN,"请先登录后再下单");
        }
        UserModel userModel = (UserModel)redisTemplate.opsForValue().get(token);
        if(userModel == null) {
            throw new BusinessException(EnumBusinessError.USER_NOT_LOGIN,"请先登录后再下单");
        }

        if(promoId != null) {
            String inRedisPromoToken = (String)redisTemplate.opsForValue().get("promo_ token_" + promoId+"_userId_"+userModel.getId()+"_itemId_"+itemId);
            if(inRedisPromoToken == null) {
                throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀令牌校验失败");
            }
            if(StringUtils.equals(promoToken,inRedisPromoToken)) {
                throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀令牌校验失败");
            }
        }

        //使用线程池来实现队列泄洪(拥塞窗口为20的队列)
        //同步调用submit方法
        Future<Object> future = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //创建订单之前,初始化库存流水init状态
                String stockLogId = itemService.initStockLog(itemId,amount);

                //再完成对应的下单事务型消息机制
                if(!mqProducer.transactionAsyncReduceStock(userModel.getId(),itemId,promoId,amount,stockLogId)){
                    throw new BusinessException(EnumBusinessError.UNKNOWN_ERROR,"下单失败");
                }
                return null;
            }
        });

        try {
            //等待future执行完成
            future.get();
        } catch (InterruptedException e) {
            throw new BusinessException(EnumBusinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            throw new BusinessException(EnumBusinessError.UNKNOWN_ERROR);
        }


        return CommonReturnType.create(null);
    }

}
