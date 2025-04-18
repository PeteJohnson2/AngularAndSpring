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
package ch.xxx.trader.usecase.services;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import ch.xxx.trader.domain.common.MongoUtils;
import ch.xxx.trader.domain.model.entity.MyMongoRepository;
import ch.xxx.trader.domain.model.entity.QuoteIb;
import ch.xxx.trader.domain.services.MyOrderBookClient;
import ch.xxx.trader.usecase.common.DtoUtils;
import ch.xxx.trader.usecase.mappers.ReportMapper;
import ch.xxx.trader.usecase.services.ServiceUtils.MyTimeFrame;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Service
public class ItbitService {
	private static final Logger LOG = LoggerFactory.getLogger(ItbitService.class);
	public static final String IB_HOUR_COL = "quoteIbHour";
	public static final String IB_DAY_COL = "quoteIbDay";
	public static volatile boolean singleInstanceLock = false;
	private final Map<String, String> currpairs = new HashMap<String, String>();
	private final MyOrderBookClient orderBookClient;
	private final ReportMapper reportMapper;
	private final MyMongoRepository myMongoRepository;
	private final ServiceUtils serviceUtils;
	private final Scheduler mongoScheduler = Schedulers.newBoundedElastic(5, 10, "mongoImport", 10);
	@Value("${single.instance.deployment:false}")
	private boolean singleInstanceDeployment;

	public ItbitService(MyOrderBookClient orderBookClient, ReportMapper reportMapper,
			MyMongoRepository myMongoRepository, ServiceUtils serviceUtils) {
		this.orderBookClient = orderBookClient;
		this.reportMapper = reportMapper;
		this.myMongoRepository = myMongoRepository;
		this.serviceUtils = serviceUtils;
		this.currpairs.put("btcusd", "XBTUSD");
		this.currpairs.put("btceur", "XBTEUR");
	}

	public Mono<String> getOrderbook(String currpair) {
		final String newCurrpair = currpair.equals("btcusd") ? "XBTUSD" : currpair;
		return this.orderBookClient.getOrderbookItbit(newCurrpair);
	}

	public Mono<QuoteIb> insertQuote(Mono<QuoteIb> quote) {
		return this.myMongoRepository.insert(quote);
	}

	public Mono<QuoteIb> currentQuote(String pair) {
		final String newPair = this.currpairs.get(pair);
		Query query = MongoUtils.buildCurrentQuery(Optional.of(newPair));
		return this.myMongoRepository.findOne(query, QuoteIb.class);
	}

	public Flux<QuoteIb> tfQuotes(String timeFrame, String pair) {
		final String newPair = this.currpairs.get(pair);
		return this.serviceUtils.tfQuotes(timeFrame, newPair, QuoteIb.class, IB_HOUR_COL, IB_DAY_COL);		
	}

	public Mono<byte[]> pdfReport(String timeFrame, String pair) {
		final String newPair = this.currpairs.get(pair);
		return this.serviceUtils.pdfReport(timeFrame, newPair, QuoteIb.class, IB_HOUR_COL, IB_DAY_COL, this.reportMapper::convert);		
	}

	private void createIbHourlyAvg() {
		LocalDateTime startAll = LocalDateTime.now();
		MyTimeFrame timeFrame = this.serviceUtils.createTimeFrame(IB_HOUR_COL, QuoteIb.class, true);
		SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
		Calendar now = Calendar.getInstance();
		now.setTime(Date.from(LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
		while (timeFrame.end().before(now)) {
			Date start = new Date();
			Query query = new Query();
			query.addCriteria(
					Criteria.where(DtoUtils.CREATEDAT).gt(timeFrame.begin().getTime()).lt(timeFrame.end().getTime()));
			// Itbit
			Mono<Collection<QuoteIb>> collectIb = this.myMongoRepository.find(query, QuoteIb.class)
					.timeout(Duration.ofSeconds(5L)).doOnError(ex -> LOG.warn("Itbit prepare hour data failed", ex))
					.onErrorResume(ex -> Mono.empty()).subscribeOn(this.mongoScheduler)
					.collectMultimap(quote -> quote.getPair(), quote -> quote)
					.map(multimap -> multimap.keySet().stream()
							.map(key -> makeIbQuoteHour(key, multimap, timeFrame.begin(), timeFrame.end()))
							.collect(Collectors.toList()))
					.flatMap(myList -> Mono
							.just(myList.stream().flatMap(Collection::stream).collect(Collectors.toList())));
			collectIb.filter(Predicate.not(Collection::isEmpty))
					.flatMap(myColl -> this.myMongoRepository.insertAll(Mono.just(myColl), IB_HOUR_COL)
							.timeout(Duration.ofSeconds(5L))
							.doOnError(ex -> LOG.warn("Itbit prepare hour data failed", ex))
							.onErrorResume(ex -> Mono.empty()).subscribeOn(this.mongoScheduler).collectList())
					.subscribeOn(this.mongoScheduler).block();

			timeFrame.begin().add(Calendar.DAY_OF_YEAR, 1);
			timeFrame.end().add(Calendar.DAY_OF_YEAR, 1);
			LOG.info("Prepared Itbit Hour Data for: " + sdf.format(timeFrame.begin().getTime()) + " Time: "
					+ (new Date().getTime() - start.getTime()) + "ms");
		}
		LOG.info(this.serviceUtils.createAvgLogStatement(startAll, "Prepared Itbit Hourly Data Time:"));
	}

	private void createIbDailyAvg() {
		LocalDateTime startAll = LocalDateTime.now();
		MyTimeFrame timeFrame = this.serviceUtils.createTimeFrame(IB_DAY_COL, QuoteIb.class, false);
		SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
		Calendar now = Calendar.getInstance();
		now.setTime(Date.from(LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
		while (timeFrame.end().before(now)) {
			Date start = new Date();
			Query query = new Query();
			query.addCriteria(
					Criteria.where(DtoUtils.CREATEDAT).gt(timeFrame.begin().getTime()).lt(timeFrame.end().getTime()));
			// Itbit
			Mono<Collection<QuoteIb>> collectIb = this.myMongoRepository.find(query, QuoteIb.class)
					.timeout(Duration.ofSeconds(5L)).doOnError(ex -> LOG.warn("Itbit prepare day data failed", ex))
					.onErrorResume(ex -> Mono.empty()).subscribeOn(this.mongoScheduler)
					.collectMultimap(quote -> quote.getPair(), quote -> quote)
					.map(multimap -> multimap.keySet().stream()
							.map(key -> makeIbQuoteDay(key, multimap, timeFrame.begin(), timeFrame.end()))
							.collect(Collectors.toList()))
					.flatMap(myList -> Mono
							.just(myList.stream().flatMap(Collection::stream).collect(Collectors.toList())));
			collectIb.filter(Predicate.not(Collection::isEmpty))
					.flatMap(myColl -> this.myMongoRepository.insertAll(Mono.just(myColl), IB_DAY_COL)
							.timeout(Duration.ofSeconds(5L))
							.doOnError(ex -> LOG.warn("Itbit prepare day data failed", ex))
							.onErrorResume(ex -> Mono.empty()).subscribeOn(this.mongoScheduler).collectList())
					.block();

			timeFrame.begin().add(Calendar.DAY_OF_YEAR, 1);
			timeFrame.end().add(Calendar.DAY_OF_YEAR, 1);
			LOG.info("Prepared Itbit Day Data for: " + sdf.format(timeFrame.begin().getTime()) + " Time: "
					+ (new Date().getTime() - start.getTime()) + "ms");
		}
		LOG.info(this.serviceUtils.createAvgLogStatement(startAll, "Prepared Itbit Daily Data Time:"));
	}

	public Mono<String> createIbAvg() {
		Mono<String> result = Mono.empty();
		if ((this.singleInstanceDeployment && !ItbitService.singleInstanceLock) || !this.singleInstanceDeployment) {
			ItbitService.singleInstanceLock = true;
			result = this.myMongoRepository.ensureIndex(IB_HOUR_COL, DtoUtils.CREATEDAT)
					.subscribeOn(this.mongoScheduler).timeout(Duration.ofMinutes(5L))
					.onErrorContinue((ex, val) -> LOG.info("ensureIndex(" + IB_HOUR_COL + ") failed.", ex))
//					.doOnError(ex -> LOG.info("ensureIndex(" + IB_HOUR_COL + ") failed.", ex))
					.then(this.myMongoRepository.ensureIndex(IB_DAY_COL, DtoUtils.CREATEDAT)
							.subscribeOn(this.mongoScheduler).timeout(Duration.ofMinutes(5L))
//							.doOnError(ex -> LOG.info("ensureIndex(" + IB_DAY_COL + ") failed.", ex))
							.onErrorContinue((ex, val) -> LOG.info("ensureIndex(" + IB_DAY_COL + ") failed.", ex)))
					.map(value -> this.createHourDayAvg()).timeout(Duration.ofHours(2L))
//					.doOnError(ex -> LOG.info("createIbAvg() failed.", ex))					
					.onErrorContinue((ex, val) -> LOG.info("createIbAvg() failed.", ex))
					.subscribeOn(this.mongoScheduler);			
		}
		return result;
	}

	private String createHourDayAvg() {
		LOG.info("createHourDayAvg()");
		CompletableFuture<String> future5 = CompletableFuture.supplyAsync(() -> {
			this.createIbHourlyAvg();
			return "createIbHourlyAvg() Done.";
		}, CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS));
		CompletableFuture<String> future6 = CompletableFuture.supplyAsync(() -> {
			this.createIbDailyAvg();
			return "createIbDailyAvg() Done.";
		}, CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS));
		String combined = Stream.of(future5, future6).map(CompletableFuture::join).collect(Collectors.joining(" "));
		LOG.info(combined);
		return "done";
	}

	private Collection<QuoteIb> makeIbQuoteHour(String key, Map<String, Collection<QuoteIb>> multimap, Calendar begin,
			Calendar end) {
		List<Calendar> hours = this.serviceUtils.createDayHours(begin);
		List<QuoteIb> hourQuotes = new LinkedList<QuoteIb>();
		for (int i = 0; i < 24; i++) {
			QuoteIb quoteIb = new QuoteIb(key, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
					BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
					BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
					BigDecimal.ZERO, new Date());
			quoteIb.setCreatedAt(hours.get(i).getTime());
			final int x = i;
			long count = multimap.get(key).stream().filter(quote -> {
				return quote.getCreatedAt().after(hours.get(x).getTime())
						&& quote.getCreatedAt().before(hours.get(x + 1).getTime());
			}).count();
			if (count > 2) {
				QuoteIb hourQuote = multimap.get(key).stream().filter(quote -> {
					return quote.getCreatedAt().after(hours.get(x).getTime())
							&& quote.getCreatedAt().before(hours.get(x + 1).getTime());
				}).reduce(quoteIb, (q1, q2) -> avgIbQuote(q1, q2, count));
				// hourQuote.setPair(key);
				hourQuotes.add(hourQuote);
			}
		}
		return hourQuotes;
	}

	private Collection<QuoteIb> makeIbQuoteDay(String key, Map<String, Collection<QuoteIb>> multimap, Calendar begin,
			Calendar end) {
		List<QuoteIb> hourQuotes = new LinkedList<QuoteIb>();
		QuoteIb quoteIb = new QuoteIb(key, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new Date());
		quoteIb.setCreatedAt(begin.getTime());
		long count = multimap.get(key).stream().filter(quote -> {
			return quote.getCreatedAt().after(begin.getTime()) && quote.getCreatedAt().before(end.getTime());
		}).count();
		if (count > 2) {
			QuoteIb hourQuote = multimap.get(key).stream().filter(quote -> {
				return quote.getCreatedAt().after(begin.getTime()) && quote.getCreatedAt().before(end.getTime());
			}).reduce(quoteIb, (q1, q2) -> avgIbQuote(q1, q2, count));
			// hourQuote.setPair(key);
			hourQuotes.add(hourQuote);
		}
		return hourQuotes;
	}

	private QuoteIb avgIbQuote(QuoteIb q1, QuoteIb q2, long count) {
		QuoteIb myQuote = new QuoteIb(q1.getPair(), this.serviceUtils.avgHourValue(q1.getBid(), q2.getBid(), count),
				this.serviceUtils.avgHourValue(q1.getBidAmt(), q2.getBidAmt(), count),
				this.serviceUtils.avgHourValue(q1.getAsk(), q2.getAsk(), count),
				this.serviceUtils.avgHourValue(q1.getAskAmt(), q2.getAskAmt(), count),
				this.serviceUtils.avgHourValue(q1.getLastPrice(), q2.getLastPrice(), count),
				this.serviceUtils.avgHourValue(q1.getStAmt(), q2.getStAmt(), count),
				this.serviceUtils.avgHourValue(q1.getVolume24h(), q2.getVolume24h(), count),
				this.serviceUtils.avgHourValue(q1.getVolumeToday(), q2.getVolumeToday(), count),
				this.serviceUtils.avgHourValue(q1.getHigh24h(), q2.getHigh24h(), count),
				this.serviceUtils.avgHourValue(q1.getLow24h(), q2.getLow24h(), count),
				this.serviceUtils.avgHourValue(q1.getOpenToday(), q2.getOpenToday(), count),
				this.serviceUtils.avgHourValue(q1.getHighToday(), q2.getHighToday(), count),
				this.serviceUtils.avgHourValue(q1.getLowToday(), q2.getLowToday(), count),
				this.serviceUtils.avgHourValue(q1.getVwapToday(), q2.getVwapToday(), count),
				this.serviceUtils.avgHourValue(q1.getVwap24h(), q2.getVwap24h(), count), q1.getServerTimeUTC());
		myQuote.setCreatedAt(q1.getCreatedAt());
		return myQuote;
	}
}
