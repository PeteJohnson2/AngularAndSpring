/**
 *    Copyright 2016 Sven Loesekann

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package ch.xxx.trader.adapter.cron;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import ch.xxx.trader.domain.model.dto.WrapperCb;
import ch.xxx.trader.domain.model.entity.QuoteBf;
import ch.xxx.trader.domain.model.entity.QuoteBs;
import ch.xxx.trader.domain.model.entity.QuoteCb;
import ch.xxx.trader.domain.model.entity.QuoteIb;
import ch.xxx.trader.usecase.services.BitfinexService;
import ch.xxx.trader.usecase.services.BitstampService;
import ch.xxx.trader.usecase.services.CoinbaseService;
import ch.xxx.trader.usecase.services.ItbitService;
import ch.xxx.trader.usecase.services.MyUserService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Component
public class ScheduledTask {
	private static final Logger log = LoggerFactory.getLogger(ScheduledTask.class);
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

	private static final String URLBS = "https://www.bitstamp.net/api";
	private static final String URLCB = "https://api.coinbase.com/v2";
	private static final String URLIB = "https://api.itbit.com";
	private static final String URLBF = "https://api.bitfinex.com";

	private final WebClient webClient;
	private final BitstampService bitstampService;
	private final BitfinexService bitfinexService;
	private final ItbitService itbitService;
	private final CoinbaseService coinbaseService;
	private final MyUserService myUserService;
	private Disposable bitstampDisposable = Mono.empty().subscribe();
	private Disposable coinbaseDisposable = Mono.empty().subscribe();
	private Disposable itbitDisposable = Mono.empty().subscribe();
	private Disposable bitfinexDisposable = Mono.empty().subscribe();

	public ScheduledTask(WebClient webClient, BitstampService bitstampService, MyUserService myUserService,
			BitfinexService bitfinexService, ItbitService itbitService, CoinbaseService coinbaseService) {
		this.webClient = webClient;
		this.bitstampService = bitstampService;
		this.bitfinexService = bitfinexService;
		this.itbitService = itbitService;
		this.coinbaseService = coinbaseService;
		this.myUserService = myUserService;
	}

//	@PostConstruct
//	public void init() {
//		
//	}

	@Scheduled(fixedRate = 90000)
	@Order(1)
	public void updateLoggedOutUsers() {
		this.myUserService.updateLoggedOutUsers();
	}

	@Scheduled(fixedRate = 60000, initialDelay = 3000)
	@SchedulerLock(name = "BitstampQuoteBTC_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitstampQuoteBTC() throws InterruptedException {
		String currPair = "btceur";
		insertBsQuote(currPair);
	}

	private void insertBsQuote(String currPair) {
		this.bitstampDisposable.dispose();
		Date start = new Date();
		Mono<QuoteBs> request = this.webClient.get()
				.uri(String.format("%s/v2/ticker/%s/", ScheduledTask.URLBS, currPair))
				.accept(MediaType.APPLICATION_JSON).exchangeToMono(response -> response.bodyToMono(QuoteBs.class))
				.map(res -> {
					res.setPair(currPair);
//				log.info(res.toString());
					return res;
				});
		this.bitstampDisposable = this.bitstampService.insertQuote(request).then().subscribe(response -> {
			if (log.isDebugEnabled()) {
				log.debug("BitstampQuote " + currPair + " " + dateFormat.format(new Date()) + " "
						+ (new Date().getTime() - start.getTime()) + "ms");
			}
		}, error -> log.error("Bitstamp " + currPair + " insert error " + dateFormat.format(new Date())));
	}

	@Scheduled(fixedRate = 60000, initialDelay = 6000)
	@SchedulerLock(name = "BitstampQuoteETH_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitstampQuoteETH() throws InterruptedException {
		String currPair = "etheur";
		this.insertBsQuote(currPair);
	}

	@Scheduled(fixedRate = 60000, initialDelay = 9000)
	@SchedulerLock(name = "BitstampQuoteLTC_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitstampQuoteLTC() throws InterruptedException {
		String currPair = "ltceur";
		this.insertBsQuote(currPair);
	}

	@Scheduled(fixedRate = 60000, initialDelay = 12000)
	@SchedulerLock(name = "BitstampQuoteXRP_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitstampQuoteXRP() throws InterruptedException {
		String currPair = "xrpeur";
		this.insertBsQuote(currPair);
	}

	@Scheduled(fixedRate = 60000, initialDelay = 15000)
	@SchedulerLock(name = "CoinbaseQuote_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertCoinbaseQuote() {
		this.coinbaseDisposable.dispose();
		Date start = new Date();
		Mono<QuoteCb> request = this.webClient.get().uri(ScheduledTask.URLCB + "/exchange-rates?currency=BTC")
				.accept(MediaType.APPLICATION_JSON).exchangeToMono(response -> response.bodyToMono(WrapperCb.class))
				.flatMap(resp -> Mono.just(resp.getData())).flatMap(resp2 -> {
//				log.info(resp2.getRates().toString());
					return Mono.just(resp2.getRates());
				});
		this.coinbaseDisposable = this.coinbaseService.insertQuote(request).then().subscribe(response -> {
			if (log.isDebugEnabled()) {
				log.debug("CoinbaseQuote " + dateFormat.format(new Date()) + " "
						+ (new Date().getTime() - start.getTime()) + "ms");
			}
		}, error -> log.error("Coinbase insert error " + dateFormat.format(new Date())));
	}

	@Scheduled(fixedRate = 60000, initialDelay = 21000)
	@SchedulerLock(name = "ItbitUsdQuote_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertItbitUsdQuote() {
		this.itbitDisposable.dispose();
		String currPair = "XBTUSD";
		Date start = new Date();
		Mono<QuoteIb> request = this.webClient.get()
				.uri(String.format("%s/v1/markets/%s/ticker", ScheduledTask.URLIB, currPair))
				.accept(MediaType.APPLICATION_JSON).exchangeToMono(response -> response.bodyToMono(QuoteIb.class))
				.map(res -> {
//				log.info(res.toString());
					return res;
				});
		this.itbitDisposable = this.itbitService.insertQuote(request).then().subscribe(response -> {
			if (log.isDebugEnabled()) {
				log.debug("ItbitQuote " + currPair + " " + dateFormat.format(new Date()) + " "
						+ (new Date().getTime() - start.getTime()) + "ms");
			}
		}, error -> log.error("Itbit " + currPair + " insert error " + dateFormat.format(new Date())));
	}

	@Scheduled(fixedRate = 60000, initialDelay = 24000)
	@SchedulerLock(name = "BitstampQuoteBTCUSD_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitstampQuoteBTCUSD() throws InterruptedException {
		String currPair = "btcusd";
		this.insertBsQuote(currPair);
	}

	@Scheduled(fixedRate = 60000, initialDelay = 27000)
	@SchedulerLock(name = "BitstampQuoteETHUSD_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitstampQuoteETHUSD() throws InterruptedException {
		String currPair = "ethusd";
		this.insertBsQuote(currPair);
	}

	@Scheduled(fixedRate = 60000, initialDelay = 30000)
	@SchedulerLock(name = "BitstampQuoteLTCUSD_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitstampQuoteLTCUSD() throws InterruptedException {
		String currPair = "ltcusd";
		this.insertBsQuote(currPair);
	}

	@Scheduled(fixedRate = 60000, initialDelay = 33000)
	@SchedulerLock(name = "BitstampQuoteXRPUSD_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitstampQuoteXRPUSD() throws InterruptedException {
		String currPair = "xrpusd";
		this.insertBsQuote(currPair);
	}

	@Scheduled(fixedRate = 60000, initialDelay = 36000)
	@SchedulerLock(name = "BitfinexQuoteBTCUSD_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitfinexQuoteBTCUSD() throws InterruptedException {
		String currPair = "btcusd";
		insertBfQuote(currPair);
	}

	private void insertBfQuote(String currPair) {
		this.bitfinexDisposable.dispose();
		Date start = new Date();
		Mono<QuoteBf> request = this.webClient.get()
				.uri(String.format("%s/v1/pubticker/%s", ScheduledTask.URLBF, currPair))
				.accept(MediaType.APPLICATION_JSON).exchangeToMono(response -> response.bodyToMono(QuoteBf.class))
				.map(res -> {
					res.setPair(currPair);
//				log.info(res.toString());
					return res;
				});
		this.bitfinexDisposable = this.bitfinexService.insertQuote(request).then().subscribe(response -> {
			if (log.isDebugEnabled()) {
				log.debug("BitfinexQuote " + currPair + " " + dateFormat.format(new Date()) + " "
						+ (new Date().getTime() - start.getTime()) + "ms");
			}
		}, error -> log.error("Bitfinex " + currPair + " insert error " + dateFormat.format(new Date())));
	}

	@Scheduled(fixedRate = 60000, initialDelay = 39000)
	@SchedulerLock(name = "BitfinexQuoteETHUSD_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitfinexQuoteETHUSD() throws InterruptedException {
		String currPair = "ethusd";
		this.insertBfQuote(currPair);
	}

	@Scheduled(fixedRate = 60000, initialDelay = 42000)
	@SchedulerLock(name = "BitfinexQuoteLTCUSD_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitfinexQuoteLTCUSD() throws InterruptedException {
		String currPair = "ltcusd";
		this.insertBfQuote(currPair);
	}

	@Scheduled(fixedRate = 60000, initialDelay = 45000)
	@SchedulerLock(name = "BitfinexQuoteXRPUSD_scheduledTask", lockAtLeastFor = "PT50S", lockAtMostFor = "PT55S")
	public void insertBitfinexQuoteXRPUSD() throws InterruptedException {
		String currPair = "xrpusd";
		this.insertBfQuote(currPair);
	}
}