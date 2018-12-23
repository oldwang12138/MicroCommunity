package com.java110.property.kafka;

import com.alibaba.fastjson.JSONObject;
import com.java110.property.smo.IPropertyServiceSMO;
import com.java110.common.constant.KafkaConstant;
import com.java110.common.constant.ResponseConstant;
import com.java110.common.constant.StatusConstant;
import com.java110.common.exception.InitConfigDataException;
import com.java110.common.exception.InitDataFlowContextException;
import com.java110.common.kafka.KafkaFactory;
import com.java110.core.base.controller.BaseController;
import com.java110.core.context.BusinessServiceDataFlow;
import com.java110.core.factory.DataTransactionFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.HashMap;
import java.util.Map;

/**
 * kafka侦听
 * Created by wuxw on 2018/4/15.
 */
public class PropertyServiceKafka extends BaseController {

    @Autowired
    private IPropertyServiceSMO propertyServiceSMOImpl;

    @KafkaListener(topics = {"propertyServiceTopic"})
    public void listen(ConsumerRecord<?, ?> record) {
        logger.info("kafka的key: " + record.key());
        logger.info("kafka的value: " + record.value().toString());
        String orderInfo = record.value().toString();
        BusinessServiceDataFlow businessServiceDataFlow = null;
        JSONObject responseJson = null;
        try {
            Map<String, String> headers = new HashMap<String, String>();
            //预校验
            preValiateOrderInfo(orderInfo);
            businessServiceDataFlow = this.writeDataToDataFlowContext(orderInfo, headers);
            responseJson = propertyServiceSMOImpl.service(businessServiceDataFlow);
        }catch (InitDataFlowContextException e){
            logger.error("请求报文错误,初始化 BusinessServiceDataFlow失败"+orderInfo,e);
            responseJson = DataTransactionFactory.createNoBusinessTypeBusinessResponseJson(orderInfo,ResponseConstant.RESULT_PARAM_ERROR,e.getMessage(),null);
        }catch (InitConfigDataException e){
            logger.error("请求报文错误,加载配置信息失败"+orderInfo,e);
            responseJson = DataTransactionFactory.createNoBusinessTypeBusinessResponseJson(orderInfo,ResponseConstant.RESULT_PARAM_ERROR,e.getMessage(),null);
        }catch (Exception e){
            logger.error("请求订单异常",e);
            responseJson = DataTransactionFactory.createBusinessResponseJson(businessServiceDataFlow,ResponseConstant.RESULT_CODE_ERROR,e.getMessage()+e,
                    null);
        }finally {
            logger.debug("当前请求报文：" + orderInfo +", 当前返回报文：" +responseJson.toJSONString());
            //只有business 和 instance 过程才做通知消息
            if(!StatusConstant.REQUEST_BUSINESS_TYPE_BUSINESS.equals(responseJson.getString("businessType"))
                    && !StatusConstant.REQUEST_BUSINESS_TYPE_INSTANCE.equals(responseJson.getString("businessType"))){
                return ;
            }
            try {
                KafkaFactory.sendKafkaMessage(KafkaConstant.TOPIC_NOTIFY_CENTER_SERVICE_NAME, "", responseJson.toJSONString());
            }catch (Exception e){
                logger.error("用户服务通知centerService失败"+responseJson,e);
                //这里保存异常信息
            }
        }
    }


    /**
     * 这里预校验，请求报文中不能有 dataFlowId
     * @param orderInfo
     */
    private void preValiateOrderInfo(String orderInfo) {
       /* if(JSONObject.parseObject(orderInfo).getJSONObject("orders").containsKey("dataFlowId")){
            throw new BusinessException(ResponseConstant.RESULT_CODE_ERROR,"报文中不能存在dataFlowId节点");
        }*/
    }

    public IPropertyServiceSMO getPropertyServiceSMOImpl() {
        return propertyServiceSMOImpl;
    }

    public void setPropertyServiceSMOImpl(IPropertyServiceSMO propertyServiceSMOImpl) {
        this.propertyServiceSMOImpl = propertyServiceSMOImpl;
    }
}