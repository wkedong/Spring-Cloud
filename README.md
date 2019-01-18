# SpringCloudDemo
## 1.项目父工程(Parent)

> 该工程负责所有子工程的dependencyManagement

新建一个基础Maven工程

==pom.xml==文件如下:

```
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>1.5.2.RELEASE</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>1.8</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <modules>
        <module>eureka</module>
        <module>config</module>
        <module>zuul</module>
        <module>zipkin</module>
        <module>service-producer</module>
        <module>service-consumer</module>
        <module>service-consumer-feign</module>
        <module>service-consumer-ribbon</module>
        <module>service-consumer-ribbon-hystrix</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>Dalston.SR1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <!-- compiler -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <encoding>${project.build.sourceEncoding}</encoding>
                </configuration>
            </plugin>
            <!-- resources -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <configuration>
                    <encoding>${project.build.sourceEncoding}</encoding>
                </configuration>
            </plugin>
        </plugins>
    </build>
```
这里使用的org.springframework.boot版本为==1.5.2.RELEASE==

依赖的org.springframework.cloud版本为==Dalston.SR1==

## 2.服务注册中心(Spring Cloud Eureka)
> Spring Cloud Eureka是Spring Cloud Netflix项目下的服务治理模块。而Spring Cloud Netflix项目是Spring Cloud的子项目之一，主要内容是对Netflix公司一系列开源产品的包装，它为Spring Boot应用提供了自配置的Netflix OSS整合。通过一些简单的注解，开发者就可以快速的在应用中配置一下常用模块并构建庞大的分布式系统。它主要提供的模块包括：服务发现（Eureka），断路器（Hystrix），智能路由（Zuul），客户端负载均衡（Ribbon）等。

### 创建一个"服务注册中心"工程

在父工程中新建一个基础Spring的Maven Moudle工程命名为eureka

==pom.xml==文件如下:

```
  <parent>
        <groupId>com.wkedong.springcloud</groupId>
        <artifactId>parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath/><!-- lookup parent from repository -->
    </parent>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-eureka-server</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
        </repository>
    </repositories>
```
---
申明一个eureka服务很简单，通过@EnableEurekaServer注解启动一个应用作为服务注册中心，提供给其他应用进行注册访问。

```
/**
 * @author wkedong
 */
@EnableEurekaServer
@SpringBootApplication
public class EurekaApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(EurekaApplication.class).web(true).run(args);
    }

}
```
@EnableEurekaServer：表示该应用注册为eureka服务注册中心，提供给其他应用使用

@SpringBootApplication：spring boot提供了一个统一的注解@SpringBootApplication，作为应用标识

@SpringBootApplication = (默认属性)@Configuration + @EnableAutoConfiguration + @ComponentScan。
1. @Configuration：提到@Configuration就要提到他的搭档@Bean。使用这两个注解就可以创建一个简单的spring配置类，可以用来替代相应的xml配置文件。
@Configuration的注解类标识这个类可以使用Spring IoC容器作为bean定义的来源。@Bean注解告诉Spring，一个带有@Bean的注解方法将返回一个对象，该对象应该被注册为在Spring应用程序上下文中的bean。
2. @EnableAutoConfiguration：能够自动配置spring的上下文，试图猜测和配置你想要的bean类，通常会自动根据你的类路径和你的bean定义自动配置。
3. @ComponentScan：会自动扫描指定包下的全部标有@Component的类，并注册成bean，当然包括@Component下的子注解@Service,@Repository,@Controller。

---

启动一个注册中心所有需要一些基础的配置，这里使用yml格式作为配置文件

==application.yml==如下:

```
server:
  port: 6060    #服务端口号为 6060
eureka:
  instance:
    hostname: localhost #主机名
  client:
    registerWithEureka: false #禁止注册中心注册自己
    fetchRegistry: false      #禁止检索服务
    serviceUrl:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/ #注册中心地址
```

---

此时我们启动应用并访问 http://localhost:6060/ 将会看到下面的页面，由于我们没有注册其他的服务，所以没有发现任何服务。

![](https://raw.githubusercontent.com/wkedong/upload/master/%E5%9B%BE%E7%89%871.png?token=AXLpXQnpNYnwSqeNW83VhEwcNXQLV9Vsks5cQXSAwA%3D%3D)

# 3.服务提供方(service-producer)
> 有了服务注册中心，下面来新建一个服务的提供方即==service-producer==，并向eureka中注册自己

在父工程中新建一个基础Spring的Maven Moudle工程命名为service-producer

==pom.xml==如下:

```
 <parent>
        <groupId>com.wkedong.springcloud</groupId>
        <artifactId>parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>

    <properties>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-eureka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>fastjson</artifactId>
            <version>1.2.39</version>
        </dependency>
        <!-- 与数据库操作相关的依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>1.3.2</version>
        </dependency>
        <!-- 使用数据源 -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid</artifactId>
            <version>1.0.14</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
```
由于demo中后续使用到了MyBatis和json的相关操作，所以在service-producer中我们事先依赖了阿里的fastjson和mybatis相应的jar包。

---

然后我们对工程下的application.yml做一些配置工作，具体如下：

```
eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:6060/eureka/  #注册中心服务地址
server:
  port: 6070  #当前服务端口
spring:
  application:
    name: service-producer    #当前服务ID
  datasource: #数据库信息
    url: jdbc:mysql://127.0.0.1:3306/springclouddemo?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8
    username: root
    password: 123456
    driver-class-name: com.mysql.jdbc.Driver
#mybatis config
mybatis:
  type-aliases-package: com.wkedong.springcloud.serviceproducer.entity #entity实体对象所在的路径位置
```
通过spring:application:name属性，我们可以指定微服务的名称后续在调用的时候只需要使用该名称就可以进行服务的访问。

eureka:client:serviceUrl:defaultZone属性对应服务注册中心的配置内容，指定服务注册中心的位置。

为了在本机上测试区分服务提供方和服务注册中心，使用server:port属性设置不同的端口。

---

接下来我们来实现服务的提供，应用启动类Application，具体如下：

```
/**
 * @author wkedong
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.wkedong.springcloud.serviceproducer.mapper")
public class ServiceProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceProducerApplication.class, args);
    }

}
```
@MapperScan:扫描mybatis所配置的mapper路径

---
新建一个controller类来实现 /prodvide 和 /test 接口

==ProducerController.java==:

```
/**
 * @author wkedong
 * <p>
 * 2019/1/14
 */
@RestController
public class ProducerController {
    @Autowired
    ProducerService producerService;

    @RequestMapping(value = "/provide", method = RequestMethod.POST)
    public String producer(@RequestBody JSONObject jsonRequest) {
        return producerService.producer(jsonRequest);
    }

    @RequestMapping(value = "/test", method = RequestMethod.GET)
    public String test(){
        return producerService.test();
    }
}
```
controller调用的Service，新建了ProducerService接口类及ProducerServiceImpl实现类
，分别如下：

```
/**
 * @author wkedong
 * <p>
 * 2019/1/15
 */
public interface ProducerService {
    String producer(JSONObject jsonRequest);

    String test();
}
```

```
/**
 * @author wkedong
 * <p>
 * 2019/1/14
 */
@Service
public class ProducerServiceImpl implements ProducerService {
    private Logger logger = LoggerFactory.getLogger(ProducerServiceImpl.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EurekaInstanceConfig eurekaInstanceConfig;

    @Value("${server.port}")
    private int serverPort = 0;

    @Override
    public String producer(@RequestBody JSONObject jsonRequest) {
        UserEntity userEntity = userMapper.getOne(1);
        this.logger.info("/provide, instanceId:{}, host:{}", eurekaInstanceConfig.getInstanceId(), eurekaInstanceConfig.getHostName(false));
        return "Hello, Spring Cloud! My port is " + String.valueOf(serverPort) + " Name is " + jsonRequest.toString()
                + "My name is " + userEntity.getName() + " age is " + userEntity.getBirthday() + " area is " + userEntity.getAddress();
    }

    @Override
    public String test() {
        List<UserEntity> users = userMapper.getAll();
        UserEntity userEntity = userMapper.getOne(1);
        //2、使用JSONArray
        String usersStr = JSONArray.toJSONString(users);
        //1、使用JSONObject
        String userEntityStr = JSONObject.toJSONString(userEntity);
        return usersStr + "          " + userEntityStr;
    }
}
```
数据库操作的mapper文件如下：

```
/**
 * @author wkedong
 * <p>
 * 2019/1/15
 */
public interface UserMapper {

    @Select("SELECT * FROM user")
    List<UserEntity> getAll();

    @Select("SELECT * FROM user WHERE id = #{id}")
    UserEntity getOne(int id);

    @Insert("INSERT INTO user(name,birthday,address) VALUES(#{name}, #{birthday}, #{address})")
    void insert(UserEntity user);

    @Update("UPDATE user SET name=#{name},birthday=#{birthday},address=#{address} WHERE id =#{id}")
    void update(UserEntity user);

    @Delete("DELETE FROM user WHERE id =#{id}")
    void delete(int id);
}
```
对应的实体类如下：

```
/**
 * @author wkedong
 * 测试实体
 * 2019/1/15
 */
@RefreshScope
@Component//加入到Spring容器
public class UserEntity {

    private String name;
    private String birthday;
    private String address;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
```
启动该工程后，再次访问：http://localhost:6060/ 可以如下图内容，我们定义的服务被成功注册了。
![](https://raw.githubusercontent.com/wkedong/upload/master/%E5%9B%BE%E7%89%871.png?token=AXLpXQnpNYnwSqeNW83VhEwcNXQLV9Vsks5cQXSAwA%3D%3D)
访问 http://localhost:6070/test 得到以下返回值：

```
[{"address":"南京","birthday":"1994-12-21","name":"王克东"}] {"address":"南京","birthday":"1994-12-21","name":"王克东"}
```
