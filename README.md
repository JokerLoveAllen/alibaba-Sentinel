<img src="https://user-images.githubusercontent.com/9434884/43697219-3cb4ef3a-9975-11e8-9a9c-73f4f537442d.png" alt="Sentinel Logo" width="50%">

# Sentinel: 服务的哨兵

[![Travis Build Status](https://travis-ci.org/alibaba/Sentinel.svg?branch=master)](https://travis-ci.org/alibaba/Sentinel)
[![Codecov](https://codecov.io/gh/alibaba/Sentinel/branch/master/graph/badge.svg)](https://codecov.io/gh/alibaba/Sentinel)
[![Maven Central](https://img.shields.io/maven-central/v/com.alibaba.csp/sentinel-core.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:com.alibaba.csp%20AND%20a:sentinel-core)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Gitter](https://badges.gitter.im/alibaba/Sentinel.svg)](https://gitter.im/alibaba/Sentinel)

## 文档
[中文文档](https://github.com/alibaba/Sentinel/wiki/%E4%BB%8B%E7%BB%8D)

[官方Wiki](https://github.com/alibaba/Sentinel/wiki)

## 快速入门

本次讲解如何快速的使用spring-cloud-start-alibaba集成自定义的sentinel来实现自定义监控服务的功能

### 1 添加依赖
**Note:** Sentinel 需要Java 7 或之后版本.

集成自定义的Sentinel: 在pom文件中设置(注:现在使用spring-cloud-start-alibaba 2.1.0 内部集成的是Sentinel 1.6.3版本,**如果需要升级cloud请升级com.qianlima.sentinel**)

 `pom.xml`.

```xml
<!--配置local 依赖-->
<dependency>
	<groupId>com.qianlima.sentinel</groupId>
	<artifactId>sentinel-core</artifactId>
	<version>1.6.3</version>
</dependency>
<!--移除内部依赖-->
<dependency>
	<groupId>com.alibaba.cloud</groupId>
	<artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
	<version>${alicloud.sentinel.version}</version>
	<exclusions>
		<exclusion>
			<groupId>com.alibaba.csp</groupId>
			<artifactId>sentinel-core</artifactId>
		</exclusion>
	</exclusions>
</dependency>

<!-- mvn 私服-->
<repositories>
        <repository>
            <id>nexus</id>
            <name>Nexus</name>
            <url>http://192.168.30.14:8081/nexus/content/groups/qianlima-public</url>
        </repository>
    </repositories>
```

### 2 配置文件(以yml、yaml为例)
`application-*.yml`.
```yml
spring:
  cloud:
    sentinel:
      transport:
        # 本地服务上报数据信息的端口
        port: 8778
        # 远端sentinel-dashboard的地址和端口
        dashboard: 192.168.30.13:8803
```

### 3 代码配置
` @SentinelResource("foo")`是alibaba-cloud提供的方便监控资源的基于方法的注解
示例配置(客户端):
```java
    @GetMapping("foo")
    @SentinelResource("foo")
    public String foo(){
        return "bar";
    }
```
服务端的配置,需要我们使用相同的注解在服务提供者进行资源配置

## Who is using

![Alibaba Group](https://docs.alibabagroup.com/assets2/images/en/global/logo_header.png)
![Taiping Renshou](http://www.cntaiping.com/tplresource/cms/www/taiping/img/home_new/tp_logo_img.png)
![Shunfeng Technology](https://user-images.githubusercontent.com/9434884/48463502-2f48eb80-e817-11e8-984f-2f9b1b789e2d.png)
![Mandao](https://user-images.githubusercontent.com/9434884/48463559-6cad7900-e817-11e8-87e4-42952b074837.png)
![每日优鲜](https://home.missfresh.cn/statics/img/logo.png)
![二维火](https://user-images.githubusercontent.com/9434884/49358468-bc43de00-f70d-11e8-97fe-0bf05865f29f.png)
![文轩在线](http://static.winxuancdn.com/css/v2/images/logo.png)
![客如云](https://www.keruyun.com/static/krynew/images/logo.png)
![亲宝宝](https://stlib.qbb6.com/wclt/img/home_hd/version1/title_logo.png)
![杭州光云科技](https://www.raycloud.com/images/logo.png)
![金汇金融](https://res.jinhui365.com/r/images/logo2.png?v=1.527)
![闪电购](http://cdn.52shangou.com/shandianbang/official-source/3.1.1/build/images/logo.png)
![拼多多](http://cdn.pinduoduo.com/assets/img/pdd_logo_v3.png)
