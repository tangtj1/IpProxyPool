package cn.tangtj.proxypool.task;

import cn.tangtj.proxypool.domain.ProxyRateInfo;
import cn.tangtj.proxypool.domain.ProxyRateLevel;
import cn.tangtj.proxypool.exception.RequestException;
import cn.tangtj.proxypool.pool.HttpClientPool;
import cn.tangtj.proxypool.pool.IpProxyStore;
import cn.tangtj.proxypool.pool.ProxyKeepPool;
import cn.tangtj.proxypool.pool.ProxyTestPool;
import cn.tangtj.proxypool.proxytest.BaiduProxyConnectTest;
import cn.tangtj.proxypool.proxytest.ProxyConnectTest;
import cn.tangtj.proxypool.util.ThreadUtils;
import cn.tangtj.proxypool.util.TimeUtils;
import okhttp3.OkHttpClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author tang
 * @date 2019/9/29
 */
@Component
public class ProxyTestTask {

    private final IpProxyStore testPool;

    private final IpProxyStore keepPool;

    private ProxyConnectTest connectTest = new BaiduProxyConnectTest();

    private HttpClientPool clientPool;

    public ProxyTestTask() {
        testPool = ProxyTestPool.getInstance().getPool();
        keepPool = ProxyKeepPool.getInstance().getPool();
        clientPool = HttpClientPool.getInstance();
    }

    @Scheduled(cron = "0/5 * * * * ?")
    public void test() {
        ThreadUtils.execute(() -> {
            if (testPool.size() == 0) {
                return;
            }
            ProxyRateInfo rateInfo = testPool.poll();
            if (rateInfo.getLastTestTime() + 10 > TimeUtils.currentTimeSecond()) {
                //10s内只检查一次
                System.out.println(rateInfo.getLastTestTime() + " " + TimeUtils.currentTimeSecond());
                System.out.printf(Thread.currentThread().getName() + " 检测ip:%s 10s内已检测.\n", rateInfo.getIp());
                return;
            }
            OkHttpClient client = clientPool.get(rateInfo.getIp(), rateInfo.getPort());
            boolean connectAble = false;
            try {
                System.out.printf(Thread.currentThread().getName() + " 正在检测ip:%s\n", rateInfo.getIp());
                connectTest.test(client, rateInfo.getIp());
                connectAble = true;
            } catch (RequestException e) {
                //请求失败,不通过
            }
            if (connectAble) {
                //加分
                rateInfo.incrRate();
                System.out.printf(Thread.currentThread().getName() + " 检测ip:%s,通过,提高评分,%s\n", rateInfo.getIp(), rateInfo.getRate());
            } else {
                //减分
                rateInfo.downRate();
                System.out.printf(Thread.currentThread().getName() + " 检测ip:%s,不通过,降低评分,%s\n", rateInfo.getIp(), rateInfo.getRate());
            }
            if (rateInfo.getRate() == ProxyRateLevel.A) {
                //放入到另一个池子
                keepPool.push(rateInfo);
            } else if (rateInfo.getRate() != ProxyRateLevel.E) {
                testPool.push(rateInfo);
            }
        });
    }
}