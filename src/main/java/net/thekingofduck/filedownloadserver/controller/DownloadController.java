package net.thekingofduck.filedownloadserver.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;

import net.thekingofduck.filedownloadserver.config.Config;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 * @Project: FileDownloadServer
 * @ClassName: DownloadController
 * @Author: TheKingOfDuck
 * @Date:2021/7/22 12:46 上午
 * Github:https://github.com/TheKingOfDuck
 * When I wirting my code, only God and I know what it does. After a while, only God knows.
 */
@RestController
public class DownloadController {

    static Log log = LogFactory.get(Thread.currentThread().getStackTrace()[1].getClassName());


    @RequestMapping("/**")
    public void download(HttpServletRequest request, HttpServletResponse response, @RequestHeader(required = false) String range) {

        String filePath = Config.PATH + request.getRequestURI();
        String ip = request.getRemoteHost();
        log.info(String.format("%s 请求文件 %s",ip,filePath));

        if (!filePath.contains("..")){
            //文件位置
            File file = new File(filePath);
            if (!file.exists()){
                log.error(String.format("%s 请求的文件%s不存在",ip,filePath));
                return;
            }
            //开始下载位置
            long startByte = 0;
            //结束下载位置
            long endByte = file.length() - 1;

            //有range的话
            if (range != null && range.contains("bytes=") && range.contains("-")) {
                //坑爹地方一：http状态码要为206
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

                range = range.substring(range.lastIndexOf("=") + 1).trim();
                String[] ranges = range.split("-");
                try {
                    //判断range的类型
                    if (ranges.length == 1) {
                        //类型一：bytes=-2343
                        if (range.startsWith("-")) {
                            endByte = Long.parseLong(ranges[0]);
                        }
                        //类型二：bytes=2343-
                        else if (range.endsWith("-")) {
                            startByte = Long.parseLong(ranges[0]);
                        }
                    }
                    //类型三：bytes=22-2343
                    else if (ranges.length == 2) {
                        startByte = Long.parseLong(ranges[0]);
                        endByte = Long.parseLong(ranges[1]);
                    }

                } catch (NumberFormatException e) {
                    startByte = 0;
                    endByte = file.length() - 1;
                }
            } else {
                //没有ranges即全部一次性传输，需要用200状态码，这一行应该可以省掉，因为默认返回是200状态码
                response.setStatus(HttpServletResponse.SC_OK);
            }

            //要下载的长度（endByte为总长度-1，这时候要加回去）
            long contentLength = endByte - startByte + 1;
            //文件名
            String fileName = file.getName();
            //文件类型
            String contentType = request.getServletContext().getMimeType(fileName);


            //各种响应头设置
            //参考资料：https://www.ibm.com/developerworks/cn/java/joy-down/index.html
            //坑爹地方二：看代码
            response.setHeader("Accept-Ranges", "bytes");
            response.setContentType(contentType);
            response.setHeader("Content-Type", contentType);
            //参考资料：http://hw1287789687.iteye.com/blog/2188500
            String[] filenames = filePath.split("/");
            response.setHeader("Content-Disposition", "inline;filename=" + filenames[filenames.length -1 ]);
            response.setHeader("Content-Length", String.valueOf(contentLength));
            //坑爹地方三：Content-Range，格式为
            // [要下载的开始位置]-[结束位置]/[文件总大小]
            response.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + file.length());
            log.info(ip + " 请求bytes的范围 " + startByte + "-" + endByte + " 文件大小:" + (file.length() -1));


            BufferedOutputStream outputStream = null;
            RandomAccessFile randomAccessFile = null;
            //已传送数据大小
            long transmitted = 0;
            try {
                randomAccessFile = new RandomAccessFile(file, "r");
                outputStream = new BufferedOutputStream(response.getOutputStream());
                byte[] buff = new byte[4096];
                int len = 0;
                randomAccessFile.seek(startByte);
                //坑爹地方四：判断是否到了最后不足4096（buff的length）个byte这个逻辑（(transmitted + len) <= contentLength）要放前面！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
                //不然会先读取randomAccessFile，造成后面读取位置出错，找了一天才发现问题所在
                while ((transmitted + len) <= contentLength && (len = randomAccessFile.read(buff)) != -1) {
                    outputStream.write(buff, 0, len);
                    transmitted += len;
                    //停一下，方便测试（本地下载传输速度特别快，没反应过来就下载好了），实际生产环境中用的时候需要删除这一行
//                    Thread.sleep(1);
                }
                //处理不足buff.length部分
                if (transmitted < contentLength) {
                    len = randomAccessFile.read(buff, 0, (int) (contentLength - transmitted));
                    outputStream.write(buff, 0, len);
                    transmitted += len;
                }

                outputStream.flush();
                response.flushBuffer();
                randomAccessFile.close();
                log.info(ip + " 下载" + startByte + "-" + endByte + "完成 已传输:" + (transmitted - 1));

            } catch (ClientAbortException e) {
                log.info(ip+ " 下载" + startByte + "-" + endByte+1 + "中断 已传输:" + transmitted);
                //捕获此异常表示拥护停止下载
            } catch (IOException e) {
                log.error(e.getMessage());
            } finally {
                try {
                    if (randomAccessFile != null) {
                        randomAccessFile.close();
                    }
                } catch (IOException e) {
                    log.error(e.getMessage());
                }
            }
        }
    }
}
