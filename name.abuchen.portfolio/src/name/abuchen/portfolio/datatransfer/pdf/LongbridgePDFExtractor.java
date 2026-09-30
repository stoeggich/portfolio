package name.abuchen.portfolio.datatransfer.pdf;

import static name.abuchen.portfolio.util.TextUtil.trim;

import java.math.BigDecimal;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.datatransfer.ExtractorUtils;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Block;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.DocumentType;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Transaction;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.AccountTransferEntry;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Transaction.Unit;
import name.abuchen.portfolio.money.ExchangeRate;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;

/**
 * @formatter:off
 * @implNote Longbridge Securities (Hong Kong) Limited
 *           The monthly statement (综合账户月结单) is only available in Chinese.
 *
 *           The account currency is HKD. It is not stated in the document, but can be derived from the
 *           exchange rate in the account summary (USD 1,033.63 * 7.8015 = HKD 8,063.86).
 *           All amounts without a currency are booked in the account currency.
 *
 *           Trades do not state a currency. All trades with EST timestamps are US trades in USD.
 *           Securities are only identified by their (Chinese) name. There is no ISIN, WKN or ticker symbol.
 *
 *           Short sales (沽空卖出) and buy to cover (平仓买入) are not supported.
 *
 *           The PDF to text conversion creates Kangxi radicals instead of the regular CJK characters
 *           (e.g. ⽉ instead of 月, ⼊ instead of 入, ⾦ instead of 金). These are matched with a dot.
 *
 * @implSpec The dates are in the format yyyy.MM.dd and are converted to dd.MM.yyyy.
 * @formatter:on
 */
@SuppressWarnings("nls")
public class LongbridgePDFExtractor extends AbstractPDFExtractor
{
    private static final String HKD = "HKD";
    private static final String USD = "USD";

    public LongbridgePDFExtractor(Client client)
    {
        super(client);

        addBankIdentifier("longbridge.hk");

        addAccountStatementTransaction();
    }

    @Override
    public String getLabel()
    {
        return "Longbridge Securities (Hong Kong) Limited";
    }

    private void addAccountStatementTransaction()
    {
        final var type = new DocumentType("综合账户.结单");
        this.addDocumentTyp(type);

        addBuySellTransaction(type);
        addDepositTransaction(type);
        addCurrencyExchangeTransaction(type);
    }

    private void addBuySellTransaction(DocumentType type)
    {
        var pdfTransaction = new Transaction<BuySellEntry>();

        // @formatter:off
        // 2024.08.29 2024.08.30 OS2024083022864 买⼊   英伟达 1.00 121.32 121.32 -122.32
        // 2024.08.28 2024.08.29 OS2024082909203 沽空卖出   英伟达 1.00 127.33 127.33 126.31
        // 2024.08.28 2024.08.29 OS2024082921950 平仓买⼊   英伟达 2.00 125.55 251.10 -252.11
        // ...
        // 其他交易费⽤ 0.00
        // @formatter:on
        var firstRelevantLine = new Block("^[\\d]{4}\\.[\\d]{2}\\.[\\d]{2} [\\d]{4}\\.[\\d]{2}\\.[\\d]{2} [A-Z0-9]+ (买.|沽空卖出|平仓买.) .*$", //
                        "^其他交易费. (\\-)?[\\.,\\d]+$");
        type.addBlock(firstRelevantLine);
        firstRelevantLine.set(pdfTransaction);

        pdfTransaction //

                        .subject(() -> new BuySellEntry(PortfolioTransaction.Type.BUY))

                        // @formatter:off
                        // Is type --> "沽空卖出" (short sale) change from BUY to SELL
                        // Is type --> "沽空卖出" (short sale) or "平仓买入" (buy to cover) is not supported
                        // @formatter:on
                        .section("type").optional() //
                        .match("^[\\d]{4}\\.[\\d]{2}\\.[\\d]{2} [\\d]{4}\\.[\\d]{2}\\.[\\d]{2} [A-Z0-9]+ (?<type>(沽空卖出|平仓买.)) .*$") //
                        .assign((t, v) -> {
                            if ("沽空卖出".equals(v.get("type")))
                                t.setType(PortfolioTransaction.Type.SELL);

                            v.markAsFailure(Messages.MsgErrorTransactionTypeNotSupportedOrRequired);
                        })

                        // @formatter:off
                        // 2024.08.29 2024.08.30 OS2024083022864 买⼊   英伟达 1.00 121.32 121.32 -122.32
                        // 下单时间 成交时间 数量 平均价格
                        // 10:35:56 EST 10:36:00 EST 1.00 121.32
                        // @formatter:on
                        .section("year", "month", "day", "note", "name", "shares", "amount", "time") //
                        .match("^(?<year>[\\d]{4})\\.(?<month>[\\d]{2})\\.(?<day>[\\d]{2}) " //
                                        + "[\\d]{4}\\.[\\d]{2}\\.[\\d]{2} " //
                                        + "(?<note>[A-Z0-9]+) " //
                                        + "(买.|沽空卖出|平仓买.)[\\s]+" //
                                        + "(?<name>.*) " //
                                        + "(?<shares>[\\.,\\d]+) " //
                                        + "[\\.,\\d]+ " //
                                        + "[\\.,\\d]+ " //
                                        + "(\\-)?(?<amount>[\\.,\\d]+)$") //
                        .match("^[\\d]{2}:[\\d]{2}:[\\d]{2} EST (?<time>[\\d]{2}:[\\d]{2}:[\\d]{2}) EST [\\.,\\d]+ [\\.,\\d]+$") //
                        .assign((t, v) -> {
                            // All trades with EST timestamps are US trades in USD
                            v.put("currency", asCurrencyCode(USD));

                            t.setSecurity(getOrCreateSecurity(v));

                            t.setDate(asDate(v.get("day") + "." + v.get("month") + "." + v.get("year"), v.get("time")));
                            t.setShares(asShares(v.get("shares")));
                            t.setCurrencyCode(asCurrencyCode(USD));
                            t.setAmount(asAmount(v.get("amount")));
                            t.setNote("Ord.-Nr.: " + trim(v.get("note")));
                        })

                        .wrap(BuySellEntryItem::new);

        addFeesSectionsTransaction(pdfTransaction, type);
    }

    private void addDepositTransaction(DocumentType type)
    {
        // @formatter:off
        // 2024.08.28 存⼊资⾦ 10,000.00
        // @formatter:on
        var depositBlock = new Block("^[\\d]{4}\\.[\\d]{2}\\.[\\d]{2} 存.资. [\\.,\\d]+$");
        type.addBlock(depositBlock);
        depositBlock.set(new Transaction<AccountTransaction>()

                        .subject(() -> new AccountTransaction(AccountTransaction.Type.DEPOSIT))

                        .section("year", "month", "day", "amount") //
                        .match("^(?<year>[\\d]{4})\\.(?<month>[\\d]{2})\\.(?<day>[\\d]{2}) 存.资. (?<amount>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            // Amounts without a currency are booked in the account currency
                            t.setDateTime(asDate(v.get("day") + "." + v.get("month") + "." + v.get("year")));
                            t.setCurrencyCode(asCurrencyCode(HKD));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        .wrap(TransactionItem::new));
    }

    private void addCurrencyExchangeTransaction(DocumentType type)
    {
        // @formatter:off
        // 2024.08.29 货币兑换出账 HKD 换汇⾄ USD @ 0.1279 -10,000.00
        // 2024.08.29 货币兑换⼊账 HKD 换汇⾄ USD @ 0.1279 1,279.00
        // @formatter:on
        var currencyExchangeBlock = new Block("^[\\d]{4}\\.[\\d]{2}\\.[\\d]{2} 货币兑换出账 [A-Z]{3} 换汇. [A-Z]{3} @ [\\.,\\d]+ \\-[\\.,\\d]+$", //
                        "^[\\d]{4}\\.[\\d]{2}\\.[\\d]{2} 货币兑换.账 [A-Z]{3} 换汇. [A-Z]{3} @ [\\.,\\d]+ [\\.,\\d]+$");
        type.addBlock(currencyExchangeBlock);
        currencyExchangeBlock.set(new Transaction<AccountTransferEntry>()

                        .subject(AccountTransferEntry::new)

                        .section("year", "month", "day", "sourceCurrency", "targetCurrency", "exchangeRate", "sourceAmount", "targetAmount") //
                        .match("^(?<year>[\\d]{4})\\.(?<month>[\\d]{2})\\.(?<day>[\\d]{2}) " //
                                        + "货币兑换出账 " //
                                        + "(?<sourceCurrency>[A-Z]{3}) 换汇. (?<targetCurrency>[A-Z]{3}) " //
                                        + "@ (?<exchangeRate>[\\.,\\d]+) " //
                                        + "\\-(?<sourceAmount>[\\.,\\d]+)$") //
                        .match("^[\\d]{4}\\.[\\d]{2}\\.[\\d]{2} 货币兑换.账 [A-Z]{3} 换汇. [A-Z]{3} @ [\\.,\\d]+ (?<targetAmount>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            var source = Money.of(asCurrencyCode(v.get("sourceCurrency")), asAmount(v.get("sourceAmount")));
                            var target = Money.of(asCurrencyCode(v.get("targetCurrency")), asAmount(v.get("targetAmount")));

                            // The document states the rate as target per source currency
                            // (HKD 1 = USD 0.1279), the gross value unit expects the inverse.
                            var exchangeRate = ExchangeRate.inverse(asExchangeRate(v.get("exchangeRate")));

                            t.setDate(asDate(v.get("day") + "." + v.get("month") + "." + v.get("year")));
                            t.getSourceTransaction().setMonetaryAmount(source);
                            t.getTargetTransaction().setMonetaryAmount(target);
                            t.getSourceTransaction().addUnit(new Unit(Unit.Type.GROSS_VALUE, source, target, exchangeRate));
                        })

                        .wrap(t -> new AccountTransferItem(t, true)));
    }

    private <T extends Transaction<?>> void addFeesSectionsTransaction(T transaction, DocumentType type)
    {
        transaction //

                        // @formatter:off
                        // The first value is the commission before discount, the second value is the commission charged.
                        // 佣⾦ 0.99 0.00
                        // @formatter:on
                        .section("fee").optional() //
                        .match("^佣. (\\-)?[\\.,\\d]+ (\\-)?(?<fee>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            v.put("currency", asCurrencyCode(USD));
                            processFeeEntries(t, v, type);
                        })

                        // @formatter:off
                        // 平台费 1.00
                        // @formatter:on
                        .section("fee").optional() //
                        .match("^平台费 (\\-)?(?<fee>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            v.put("currency", asCurrencyCode(USD));
                            processFeeEntries(t, v, type);
                        })

                        // @formatter:off
                        // 交收费 0.01
                        // @formatter:on
                        .section("fee").optional() //
                        .match("^交收费 (\\-)?(?<fee>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            v.put("currency", asCurrencyCode(USD));
                            processFeeEntries(t, v, type);
                        })

                        // @formatter:off
                        // 证券交易委员会费 0.01
                        // @formatter:on
                        .section("fee").optional() //
                        .match("^证券交易委员会费 (\\-)?(?<fee>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            v.put("currency", asCurrencyCode(USD));
                            processFeeEntries(t, v, type);
                        })

                        // @formatter:off
                        // 交易活动收费 0.01
                        // @formatter:on
                        .section("fee").optional() //
                        .match("^交易活动收费 (\\-)?(?<fee>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            v.put("currency", asCurrencyCode(USD));
                            processFeeEntries(t, v, type);
                        })

                        // @formatter:off
                        // 其他交易费⽤ 0.00
                        // @formatter:on
                        .section("fee").optional() //
                        .match("^其他交易费. (\\-)?(?<fee>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            v.put("currency", asCurrencyCode(USD));
                            processFeeEntries(t, v, type);
                        });
    }

    @Override
    protected long asAmount(String value)
    {
        return ExtractorUtils.convertToNumberLong(value, Values.Amount, "en", "US");
    }

    @Override
    protected long asShares(String value)
    {
        return ExtractorUtils.convertToNumberLong(value, Values.Share, "en", "US");
    }

    @Override
    protected BigDecimal asExchangeRate(String value)
    {
        return ExtractorUtils.convertToNumberBigDecimal(value, Values.Share, "en", "US");
    }
}
