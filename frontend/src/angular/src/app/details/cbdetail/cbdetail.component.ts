/*
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
import {
  Component,
  OnInit,
  LOCALE_ID,
  Inject,
  DestroyRef,
  inject,
} from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import {
  trigger,
  state,
  animate,
  transition,
  style,
} from "@angular/animations";
import { BehaviorSubject, Observable, repeat } from "rxjs";
import { QuoteCb, QuoteCbSmall } from "../../common/quote-cb";
import { CoinbaseService } from "../../services/coinbase.service";
import { DetailBase, Tuple } from "src/app/common/detail-base";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

@Component({
    selector: "app-cbdetail",
    templateUrl: "./cbdetail.component.html",
    styleUrls: ["./cbdetail.component.scss"],
    animations: [
        trigger("showChart", [
            transition("false => true", [
                style({ opacity: 0 }),
                animate(1000, style({ opacity: 1 })),
            ]),
        ]),
    ],
    standalone: false
})
export class CbdetailComponent extends DetailBase implements OnInit {
  public currpair: string;
  public currQuote: QuoteCb;
  // eslint-disable-next-line @typescript-eslint/naming-convention
  readonly BTCUSD: string;
  // eslint-disable-next-line @typescript-eslint/naming-convention
  readonly ETHUSD: string;
  // eslint-disable-next-line @typescript-eslint/naming-convention
  readonly LTCUSD: string;
  protected chartShow = new BehaviorSubject(false);
  protected todayQuotes: QuoteCbSmall[] = [];
  protected myCurrPair = "";
  private readonly destroy: DestroyRef = inject(DestroyRef);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private serviceCb: CoinbaseService,
    @Inject(LOCALE_ID) private myLocale: string,
  ) {
    super(myLocale);
    this.BTCUSD = this.serviceCb.BTCUSD;
    this.ETHUSD = this.serviceCb.ETHUSD;
    this.LTCUSD = this.serviceCb.LTCUSD;
  }

  ngOnInit() {
    this.chartShow.next(false);
    this.route.params.subscribe((params) => {
      this.currpair = params.currpair;
      this.myCurrPair = this.utils.getCurrpairName(this.currpair);
      this.serviceCb
        .getCurrentQuote()
        .pipe(repeat({ delay: 10000 }), takeUntilDestroyed(this.destroy))
        .subscribe((quote) => (this.currQuote = quote));
      this.serviceCb
        .getTodayQuotes()
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe((quotes) => {
          this.todayQuotes = quotes;
          if (this.currpair === this.serviceCb.BTCUSD) {
            this.updateChartData(
              quotes.map(
                (quote) =>
                  new Tuple<string, number>(quote.createdAt, quote.usd),
              ),
            );
          } else if (this.currpair === this.serviceCb.ETHUSD) {
            this.updateChartData(
              quotes.map(
                (quote) =>
                  new Tuple<string, number>(
                    quote.createdAt,
                    quote.usd / quote.eth,
                  ),
              ),
            );
          } else if (this.currpair === this.serviceCb.LTCUSD) {
            this.updateChartData(
              quotes.map(
                (quote) =>
                  new Tuple<string, number>(
                    quote.createdAt,
                    quote.usd / quote.ltc,
                  ),
              ),
            );
          }
          this.chartShow.next(true);
        });
    });
  }

  back(): void {
    this.router.navigate(["/"]);
  }

  changeTf() {
    this.chartShow.next(false);
    this.currpair = this.route.snapshot.paramMap.get("currpair");
    let quoteObserv: Observable<QuoteCbSmall[]>;
    if (this.timeframe === this.utils.MyTimeFrames.Day7) {
      quoteObserv = this.serviceCb.get7DayQuotes();
    } else if (this.timeframe === this.utils.MyTimeFrames.Day30) {
      quoteObserv = this.serviceCb.get30DayQuotes();
    } else if (this.timeframe === this.utils.MyTimeFrames.Day90) {
      quoteObserv = this.serviceCb.get90DayQuotes();
    } else if (this.timeframe === this.utils.MyTimeFrames.Day180) {
      quoteObserv = this.serviceCb.get6MonthsQuotes();
    } else if (this.timeframe === this.utils.MyTimeFrames.Day365) {
      quoteObserv = this.serviceCb.get1YearQuotes();
    } else {
      quoteObserv = this.serviceCb.getTodayQuotes();
    }

    quoteObserv.pipe(takeUntilDestroyed(this.destroy)).subscribe((quotes) => {
      this.todayQuotes = quotes;

      if (this.currpair === this.serviceCb.BTCUSD) {
        this.updateChartData(
          quotes.map(
            (quote) => new Tuple<string, number>(quote.createdAt, quote.usd),
          ),
        );
      } else if (this.currpair === this.serviceCb.ETHUSD) {
        this.updateChartData(
          quotes.map(
            (quote) =>
              new Tuple<string, number>(quote.createdAt, quote.usd / quote.eth),
          ),
        );
      } else if (this.currpair === this.serviceCb.LTCUSD) {
        this.updateChartData(
          quotes.map(
            (quote) =>
              new Tuple<string, number>(quote.createdAt, quote.usd / quote.ltc),
          ),
        );
      }
      this.chartShow.next(true);
    });
  }
}
