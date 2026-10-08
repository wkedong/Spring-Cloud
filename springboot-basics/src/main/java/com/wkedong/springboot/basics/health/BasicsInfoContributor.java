package com.wkedong.springboot.basics.health;

import com.wkedong.springboot.basics.config.BasicsProperties;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * 自定义 info 贡献者：让 /actuator/info 输出对运维有用的上下文。
 * <p>
 * 教学点：/actuator/info 是「部署信息面板」——哪个版本、哪个环境、谁负责，
 * 出问题时不用登机器就能确认「这个实例跑的是哪套配置」。
 *
 * @author wkedong
 */
@Component
public class BasicsInfoContributor implements InfoContributor {

    private final BasicsProperties properties;

    public BasicsInfoContributor(BasicsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("basics", new java.util.LinkedHashMap<String, Object>() {{
            put("appName", properties.getAppName());
            put("environment", properties.getEnvironment());
            put("features", properties.getFeatures());
            put("contacts", properties.getContacts());
            put("javaVersion", System.getProperty("java.version"));
            put("os", System.getProperty("os.name"));
        }});
    }
}
