package top.zxylearn.chatserver.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(
            @Value("${aliyun.oss.endpoint}") String endpoint,
            @Value("${aliyun.oss.accessKeyId}") String accessKeyId,
            @Value("${aliyun.oss.accessKeySecret}") String accessKeySecret) {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}
