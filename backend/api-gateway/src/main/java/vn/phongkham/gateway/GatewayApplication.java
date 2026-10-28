package vn.phongkham.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;
import java.util.UUID;

@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args){SpringApplication.run(GatewayApplication.class,args);}
    @Bean WebFilter correlationIdFilter(){return (exchange,chain)->{
        String incoming=exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        String id=incoming!=null&&incoming.matches("[A-Za-z0-9._-]{1,80}")?incoming:UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set("X-Correlation-ID",id);
        return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().header("X-Correlation-ID",id).build()).build());
    };}
}

