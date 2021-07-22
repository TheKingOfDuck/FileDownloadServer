package net.thekingofduck.filedownloadserver;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import net.thekingofduck.filedownloadserver.config.Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FileDownloadServerApplication {

    public static void main(String[] args) {

        //处理自定义参数
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            if ("-p".equals(arg)| "--path".equals(arg)){
                Config.PATH = args[i+1];
            }
        }

        SpringApplication.run(FileDownloadServerApplication.class, args);
    }

}
