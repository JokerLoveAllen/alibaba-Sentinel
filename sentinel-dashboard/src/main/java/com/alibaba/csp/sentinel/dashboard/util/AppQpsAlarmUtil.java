package com.alibaba.csp.sentinel.dashboard.util;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description
 * @Author Lun_qs
 * @Date 2019/8/7 18:22
 * @Version 0.0.1
 */
public class AppQpsAlarmUtil {
    /**
     * 允许最大的qps的数量
     */
    public static final Map<String,Integer> ALLOW_APP_MAX_QPS_COUNT = new ConcurrentHashMap<>();

    private static final CloseableHttpAsyncClient HTTP_ASYNC_CLIENT;

    private static Logger logger = LoggerFactory.getLogger(AppQpsAlarmUtil.class);

    static{
        IOReactorConfig ioConfig = IOReactorConfig.custom()
                .setConnectTimeout(5000)
                .setSoTimeout(5000)
                .setIoThreadCount(Runtime.getRuntime().availableProcessors() / 2)
                .build();

        HTTP_ASYNC_CLIENT = HttpAsyncClients.custom()
                .setRedirectStrategy(new DefaultRedirectStrategy() {
                    @Override
                    protected boolean isRedirectable(final String method) {
                        return false;
                    }
                })
                //Assigns maximum total connection value.
                .setMaxConnTotal(100)
                //Assigns maximum connection per route value.
                .setMaxConnPerRoute(100)
                .setDefaultIOReactorConfig(ioConfig)
                .build();
        HTTP_ASYNC_CLIENT.start();
    }

    /**
     * 发送报警信息
     * @param url 报警地址回调
     */
    public static void sendAlarmInfo(String url,String alarmMsg){
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("Content-Type", "application/json; utf-8");
        JSONObject jsonString = new JSONObject();
        JSONObject textString = new JSONObject();
        jsonString.put("msgtype","text");
        jsonString.put("text",textString);
        textString.put("content",alarmMsg);
        StringEntity requestEntity = new StringEntity(
                jsonString.toJSONString(),
                ContentType.APPLICATION_JSON);
        httpPost.setEntity(requestEntity);
        HTTP_ASYNC_CLIENT.execute(httpPost, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse httpResponse) {
                try{
                    String body = EntityUtils.toString(httpResponse.getEntity(), "utf-8");
                    logger.info("发送通知成功:{}",body);
                }catch (Exception except){
                    logger.error("解析报警返回值异常",except);
                }
            }
            @Override
            public void failed(Exception e) {
                logger.error("发送报警信息失败",e);
            }

            @Override
            public void cancelled() {
                httpPost.abort();
            }
        });
    }
}
