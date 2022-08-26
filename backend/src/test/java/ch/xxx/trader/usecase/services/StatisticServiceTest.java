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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.xxx.trader.domain.model.dto.CommonStatisticsDto;
import ch.xxx.trader.domain.model.entity.QuoteBf;
import ch.xxx.trader.domain.model.entity.QuoteBs;

@ExtendWith(MockitoExtension.class)
public class StatisticServiceTest {
	@Mock
	private MyMongoRepository myMongoRepository;
	
	
	@Test
	public void statistic5Years() {
		StatisticService statisticService = new StatisticService(this.myMongoRepository);
		List<QuoteBs> quotesBs = createBsQuotes();
		CommonStatisticsDto dto = new CommonStatisticsDto();
		statisticService.calcStatistics5Years(quotesBs, dto);
		Assertions.assertEquals(dto.getPerformance5Year().longValue(), 800L);
		Assertions.assertEquals(dto.getAvgVolume5Year(), BigDecimal.valueOf(50L));
		Assertions.assertEquals(dto.getRange5Year().getStart(), BigDecimal.TEN);
		Assertions.assertEquals(dto.getRange5Year().getEnd(), BigDecimal.valueOf(90L));
		Assertions.assertEquals(dto.getVolatility5Year(), new BigDecimal("25.81988897471611"));		
	}

	@Test
	public void statistic2Years() {
		StatisticService statisticService = new StatisticService(this.myMongoRepository);
		List<QuoteBf> quotesBf = createBfQuotes();
		CommonStatisticsDto dto = new CommonStatisticsDto();
		statisticService.calcStatistics2Years(quotesBf, dto);
		Assertions.assertEquals(dto.getPerformance2Year().longValue(), 350L);
		Assertions.assertEquals(dto.getAvgVolume2Year(), BigDecimal.valueOf(55L));
		Assertions.assertEquals(dto.getRange2Year().getStart(), BigDecimal.valueOf(20L));
		Assertions.assertEquals(dto.getRange2Year().getEnd(), BigDecimal.valueOf(90L));
		Assertions.assertEquals(dto.getVolatility2Year(), new BigDecimal("22.9128784747792"));		
	}
	
	@Test
	public void statistic1Year() {
		StatisticService statisticService = new StatisticService(this.myMongoRepository);
		List<QuoteBf> quotesBf = createBfQuotes();
		CommonStatisticsDto dto = new CommonStatisticsDto();
		statisticService.calcStatistics1Year(quotesBf, dto);
		Assertions.assertEquals(dto.getPerformance1Year().longValue(), 200L);
		Assertions.assertEquals(dto.getAvgVolume1Year(), BigDecimal.valueOf(60L));
		Assertions.assertEquals(dto.getRange1Year().getStart(), BigDecimal.valueOf(30L));
		Assertions.assertEquals(dto.getRange1Year().getEnd(), BigDecimal.valueOf(90L));
		Assertions.assertEquals(dto.getVolatility1Year(), new BigDecimal("20"));		
	}
	
	private List<QuoteBf> createBfQuotes() {
		return List.of(
				this.createBfQuote(BigDecimal.TEN, BigDecimal.TEN, LocalDate.now().minusYears(4L)),
				this.createBfQuote(BigDecimal.valueOf(20L), BigDecimal.valueOf(20L), LocalDate.now().minusMonths(15L)),
				this.createBfQuote(BigDecimal.valueOf(30L), BigDecimal.valueOf(30L), LocalDate.now().minusMonths(11L)),
				this.createBfQuote(BigDecimal.valueOf(40L), BigDecimal.valueOf(40L), LocalDate.now().minusMonths(5L)),
				this.createBfQuote(BigDecimal.valueOf(50L), BigDecimal.valueOf(50L), LocalDate.now().minusMonths(2L)),
				this.createBfQuote(BigDecimal.valueOf(60L), BigDecimal.valueOf(60L) ,LocalDate.now().minusDays(10L)),
				this.createBfQuote(BigDecimal.valueOf(70L), BigDecimal.valueOf(70L), LocalDate.now().minusDays(10L)),
				this.createBfQuote(BigDecimal.valueOf(80L), BigDecimal.valueOf(80L), LocalDate.now().minusDays(10L)),
				this.createBfQuote(BigDecimal.valueOf(90L), BigDecimal.valueOf(90L), LocalDate.now().minusDays(10L)));
	}
	
	private List<QuoteBs> createBsQuotes() {
		return List.of(
				this.createBsQuote(BigDecimal.TEN, BigDecimal.TEN, LocalDate.now().minusYears(4L)),
				this.createBsQuote(BigDecimal.valueOf(20L), BigDecimal.valueOf(20L), LocalDate.now().minusMonths(15L)),
				this.createBsQuote(BigDecimal.valueOf(30L), BigDecimal.valueOf(30L), LocalDate.now().minusMonths(11L)),
				this.createBsQuote(BigDecimal.valueOf(40L), BigDecimal.valueOf(40L), LocalDate.now().minusMonths(5L)),
				this.createBsQuote(BigDecimal.valueOf(50L), BigDecimal.valueOf(50L), LocalDate.now().minusMonths(2L)),
				this.createBsQuote(BigDecimal.valueOf(60L), BigDecimal.valueOf(60L) ,LocalDate.now().minusDays(10L)),
				this.createBsQuote(BigDecimal.valueOf(70L), BigDecimal.valueOf(70L), LocalDate.now().minusDays(10L)),
				this.createBsQuote(BigDecimal.valueOf(80L), BigDecimal.valueOf(80L), LocalDate.now().minusDays(10L)),
				this.createBsQuote(BigDecimal.valueOf(90L), BigDecimal.valueOf(90L), LocalDate.now().minusDays(10L)));
	}

	private QuoteBs createBsQuote(BigDecimal last, BigDecimal volume, LocalDate localDate) {
		QuoteBs quoteBs = new QuoteBs(null, last, null, null, null, volume, null, null, null);
		quoteBs.setCreatedAt(Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
		return quoteBs;
	}
	
	private QuoteBf createBfQuote(BigDecimal last, BigDecimal volume, LocalDate localDate) {
		QuoteBf quoteBf = new QuoteBf(null, null, null, last, null, null, volume, null);
		quoteBf.setCreatedAt(Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
		return quoteBf;
	}
}