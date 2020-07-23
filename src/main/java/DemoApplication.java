package com.example.demo;

import com.google.common.primitives.Bytes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.PostConstruct;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }

  @PostConstruct
  public void setup() {
    HttpProxyServer server =
        DefaultHttpProxyServer.bootstrap()
            .withPort(8082)
            .withFiltersSource(getFiltersSource())
            .start();
  }

  private static HttpFiltersSource getFiltersSource() {
    return new HttpFiltersSourceAdapter() {

      @Override
      public int getMaximumRequestBufferSizeInBytes() {
        return 1024 * 1024;
      }

      @Override
      public int getMaximumResponseBufferSizeInBytes() {
        return 1024 * 1024 * 2;
      }

      @Override
      public HttpFilters filterRequest(HttpRequest originalRequest) {

        return new HttpFiltersAdapter(originalRequest) {

          String cloneAndExtractContent(HttpObject httpObject, Charset
              charset) {
            List<Byte> bytes = new ArrayList<Byte>();
            HttpContent httpContent = (HttpContent) httpObject;
            ByteBuf buf = httpContent.content();
            byte[] buffer = new byte[buf.readableBytes()];
            if (buf.readableBytes() > 0) {
              int readerIndex = buf.readerIndex();
              buf.getBytes(readerIndex, buffer);
            }
            for (byte b : buffer) {
              bytes.add(b);
            }
            return new String(Bytes.toArray(bytes), charset);
          }

          @Override
          public HttpResponse clientToProxyRequest(HttpObject httpObject) {

            String content = cloneAndExtractContent(httpObject,
                StandardCharsets.UTF_8);
            if (content.contains("abc")) {
              return getBadGatewayResponse();
            }

            if (httpObject instanceof DefaultFullHttpRequest) {
              DefaultFullHttpRequest request =
                  (DefaultFullHttpRequest) httpObject;

              System.out.println("Method URI : " + request.getMethod()
                  + " " + request.getUri());
              if (request.content().toString().contains("abc")) {
                return getBadGatewayResponse();
              }
            }

            return null;
          }

          private HttpResponse getBadGatewayResponse() {
            String body =
                "<!DOCTYPE HTML \"-//IETF//DTD HTML 2.0//EN\">\n"
                    + "<html><head>\n"
                    + "<title>"
                    + "Bad Gateway"
                    + "</title>\n"
                    + "</head><body>\n"
                    + "An error occurred"
                    + "</body></html>\n";
            byte[] bytes = body.getBytes(Charset.forName("UTF-8"));
            ByteBuf content = Unpooled.copiedBuffer(bytes);
            HttpResponse response =
                new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.BAD_GATEWAY, content);
            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH,
                bytes.length);
            response.headers().set("Content-Type", "text/html; charset=UTF-8");
            response.headers().set("Date", ProxyUtils.formatDate(new Date()));
            response.headers().set(HttpHeaders.Names.CONNECTION, "close");
            return response;
          }
        };
      }
    };
  }
}
