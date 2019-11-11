/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.repository.metric;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.MetricEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.FlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.repository.rule.InMemoryRuleRepositoryAdapter;
import com.alibaba.csp.sentinel.dashboard.util.AppQpsAlarmUtil;
import com.alibaba.csp.sentinel.util.StringUtil;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Caches metrics data in a period of time in memory.
 *
 * @author Carpenter Lee
 * @author Eric Zhao
 */
@Component
public class InMemoryMetricsRepository implements MetricsRepository<MetricEntity> {

    private static final long MAX_METRIC_LIVE_TIME_MS = 1000 * 60 * 5;

    /**
     * {@code app -> resource -> timestamp -> metric}
     */
    private Map<String, Map<String, ConcurrentLinkedHashMap<Long, MetricEntity>>> allMetrics = new ConcurrentHashMap<>();

    /**
     * {@code app -> resource -> customMetics}
     */
    private Map<String, Map<String, ScheduledCheckMetics>> customMetricsMap = new ConcurrentHashMap<>();

    private ScheduledThreadPoolExecutor scheduledCheckThreadPool = new ScheduledThreadPoolExecutor(1,
            (r) -> new Thread(r,"scheduled-check-thread"));

    @Autowired
    private InMemoryRuleRepositoryAdapter<FlowRuleEntity> repository;

    @Value("${scheduled.check.initialInSecond}")
    private Integer initialDelay;

    @Value("${scheduled.check.periodInSecond}")
    private Integer period;

    @Value("${dingding.callback.url}")
    private String notifyUrl;

    /**
     * 执行定时检测任务
     */
    @PostConstruct
    private void postConstructProcess(){
        scheduledCheckTask(initialDelay,period,TimeUnit.SECONDS);
    }
    /***
     * thread： 代表当前处理该资源的线程数；
     * pass： 代表一秒内到来到的请求；-> entity.getPassQps()
     * blocked： 代表一秒内被流量控制的请求数量； -> entity.getBlockQps()
     * success： 代表一秒内成功处理完的请求；-> entity.getSuccessQps()
     * total： 代表到一秒内到来的请求以及被阻止的请求总和； -> entity.getPassQps() + entity.getBlockQps()
     * RT： 代表一秒内该资源的平均响应时间；-> entity.getRt()
     * 1m-pass： 则是一分钟内到来的请求；
     * 1m-block： 则是一分钟内被阻止的请求；
     * 1m-all： 则是一分钟内到来的请求和被阻止的请求的总和；
     * exception： 则是一秒内业务本身异常的总和。 -> entity.getExceptionQps()
     * @param entity 保存的信息
     */
    @Override
    public synchronized void save(MetricEntity entity) {
        if (entity == null || StringUtil.isBlank(entity.getApp())) {
            return;
        }
        allMetrics.computeIfAbsent(entity.getApp(), e -> new ConcurrentHashMap<>(16))
                .computeIfAbsent(entity.getResource(), e -> new ConcurrentLinkedHashMap.Builder<Long, MetricEntity>()
                        .maximumWeightedCapacity(MAX_METRIC_LIVE_TIME_MS).weigher((key, value) -> {
                            // Metric older than {@link #MAX_METRIC_LIVE_TIME_MS} will be removed.
                            int weight = (int)(System.currentTimeMillis() - key);
                            // weight must be a number greater than or equal to one
                            return Math.max(weight, 1);
                        }).build()).put(entity.getTimestamp().getTime(), entity);
//        String str = String.format("passQps:%d\tblockQps:%d\tsuccessQps:%d\texceptionQps:%d\tresultTime:%.2f",entity.getPassQps(),
//                entity.getBlockQps(),
//                entity.getSuccessQps(),
//                entity.getExceptionQps(),
//                entity.getRt());
        //TODO: 以entity.getApp() 区分不同的聚簇节点, 比如dubbo消费方和dubbo服务方

        List<FlowRuleEntity> appAllFlow = repository.findAllByApp(entity.getApp());
        for(FlowRuleEntity flowRuleEntity : appAllFlow){
            if(flowRuleEntity.getResource().equals(entity.getResource())){
                ScheduledCheckMetics currentCustomMetics =  customMetricsMap
                        .computeIfAbsent(entity.getApp(),e -> new ConcurrentHashMap<>(4))
                        .computeIfAbsent(entity.getResource(),e -> new ScheduledCheckMetics());
                currentCustomMetics.maxExpQpsInTimeWindow = Math.max(currentCustomMetics.maxExpQpsInTimeWindow,entity.getExceptionQps().intValue());
                currentCustomMetics.maxLimitQpsInTimeWindow = Math.max(currentCustomMetics.maxLimitQpsInTimeWindow,entity.getPassQps().intValue());
            }
        }
    }

    @Override
    public synchronized void saveAll(Iterable<MetricEntity> metrics) {
        if (metrics == null) {
            return;
        }
        metrics.forEach(this::save);
    }

    @Override
    public synchronized List<MetricEntity> queryByAppAndResourceBetween(String app, String resource,
                                                                        long startTime, long endTime) {
        List<MetricEntity> results = new ArrayList<>();
        if (StringUtil.isBlank(app)) {
            return results;
        }
        Map<String, ConcurrentLinkedHashMap<Long, MetricEntity>> resourceMap = allMetrics.get(app);
        if (resourceMap == null) {
            return results;
        }
        ConcurrentLinkedHashMap<Long, MetricEntity> metricsMap = resourceMap.get(resource);
        if (metricsMap == null) {
            return results;
        }
        for (Entry<Long, MetricEntity> entry : metricsMap.entrySet()) {
            if (entry.getKey() >= startTime && entry.getKey() <= endTime) {
                results.add(entry.getValue());
            }
        }
        return results;
    }

    @Override
    public List<String> listResourcesOfApp(String app) {
        List<String> results = new ArrayList<>();
        if (StringUtil.isBlank(app)) {
            return results;
        }
        // resource -> timestamp -> metric
        Map<String, ConcurrentLinkedHashMap<Long, MetricEntity>> resourceMap = allMetrics.get(app);
        if (resourceMap == null) {
            return results;
        }
        final long minTimeMs = System.currentTimeMillis() - 1000 * 60;
        Map<String, MetricEntity> resourceCount = new ConcurrentHashMap<>(32);

        for (Entry<String, ConcurrentLinkedHashMap<Long, MetricEntity>> resourceMetrics : resourceMap.entrySet()) {
            for (Entry<Long, MetricEntity> metrics : resourceMetrics.getValue().entrySet()) {
                if (metrics.getKey() < minTimeMs) {
                    continue;
                }
                MetricEntity newEntity = metrics.getValue();
                if (resourceCount.containsKey(resourceMetrics.getKey())) {
                    MetricEntity oldEntity = resourceCount.get(resourceMetrics.getKey());
                    oldEntity.addPassQps(newEntity.getPassQps());
                    oldEntity.addRtAndSuccessQps(newEntity.getRt(), newEntity.getSuccessQps());
                    oldEntity.addBlockQps(newEntity.getBlockQps());
                    oldEntity.addExceptionQps(newEntity.getExceptionQps());
                    oldEntity.addCount(1);
                } else {
                    resourceCount.put(resourceMetrics.getKey(), MetricEntity.copyOf(newEntity));
                }
            }
        }
        // Order by last minute b_qps DESC.
        return resourceCount.entrySet()
                .stream()
                .sorted((o1, o2) -> {
                    MetricEntity e1 = o1.getValue();
                    MetricEntity e2 = o2.getValue();
                    int t = e2.getBlockQps().compareTo(e1.getBlockQps());
                    if (t != 0) {
                        return t;
                    }
                    return e2.getPassQps().compareTo(e1.getPassQps());
                })
                .map(Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 定时检测任务
     * @param initialDelay 初始化延迟
     * @param period 定时时间
     * @param timeUnit 定时单位
     */
    private void scheduledCheckTask(int initialDelay, int period, TimeUnit timeUnit){
        scheduledCheckThreadPool.scheduleAtFixedRate(()->{
            for(Map.Entry<String,Map<String,ScheduledCheckMetics>> entry :customMetricsMap.entrySet()){
                List<FlowRuleEntity> ruleEntities = this.repository.findAllByApp(entry.getKey());
                for(FlowRuleEntity item : ruleEntities){
                    ScheduledCheckMetics customMetics = entry.getValue().get(item.getResource());
                    if(customMetics==null){
                        System.out.printf("未获取到%s相关统计信息\n",item.getResource());
                        continue;
                    }
                    if(customMetics.maxLimitQpsInTimeWindow >= item.getLimitQps()){
                        String alarmLimitMsg = String.format("QPS告警提示!\n当前应用:%s\n当前资源:%s\n限制报警QPS:%d\n当前QPS:%d",
                                item.getApp(),item.getResource(),item.getLimitQps(),customMetics.maxLimitQpsInTimeWindow );
                        AppQpsAlarmUtil.sendAlarmInfo(notifyUrl,alarmLimitMsg);
                        System.out.printf("%s 触发报警机制\n", LocalTime.now());
                    }
                    if(customMetics.maxExpQpsInTimeWindow >= item.getExpQps()){
                        String alarmExpMsg = String.format("异常告警提示!\n当前应用:%s\n当前资源:%s\n限制异常QPS:%d\n当前异常QPS:%d",
                                item.getApp(),item.getResource(),item.getExpQps(),customMetics.maxExpQpsInTimeWindow);
                        AppQpsAlarmUtil.sendAlarmInfo(notifyUrl,alarmExpMsg);
                        System.out.printf("%s 触发异常机制\n", LocalTime.now());
                    }
                    //reset
                    customMetics.maxExpQpsInTimeWindow = customMetics.maxLimitQpsInTimeWindow = 0;
                }
            }
        },initialDelay,period,timeUnit);
    }

    /**
     * 定时检测的时间窗口类
     */
    private static class ScheduledCheckMetics{
        /**
         * 时间窗口内最大的异常值
         */
        int maxLimitQpsInTimeWindow;
        /**
         * 时间窗口内最大的限流值
         */
        int maxExpQpsInTimeWindow;
    }
}
