package name.abuchen.portfolio.datatransfer.pdf;

import static name.abuchen.portfolio.util.TextUtil.concatenate;
import static name.abuchen.portfolio.util.TextUtil.trim;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

import name.abuchen.portfolio.datatransfer.ExtractorUtils;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Block;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.DocumentType;
import name.abuchen.portfolio.datatransfer.pdf.PDFParser.Transaction;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.util.Pair;

/**
 * @formatter:off
 * @implNote Morgan Stanley Smith Barney LLC (Global Stock Plan Services) is a US-based financial services company.
 *           The currency is USD --> $.
 *
 * @implSpec All security currencies are USD --> $.
 *           The CUSIP number is the WKN number with 9 letters.
 *
 *           Dividend reinvestment confirmations contain the dividend and the purchase of the net dividend.
 *           Both transactions are created from the same document.
 *           The dividend is booked on the settlement date, the purchase on the trade date.
 *           The number of shares of the dividend is the share balance before the reinvestment.
 *
 *           Release detail reports (restricted stock units) create three transactions:
 *           - delivery inbound of the released quantity at the fair market value (FMV) at vest,
 *           - sale of the quantity withheld to pay the taxes (sell-to-cover, withhold to cover),
 *           - removal of the tax amount.
 *           This keeps the cost basis at FMV and the net holding at the net quantity.
 *           A residual balance (sale proceeds minus taxes) remains on the account.
 *
 *           Quarterly statements contain dividends (the credit is the gross amount,
 *           the withholding tax is a separate line), the disbursement of the proceeds (removal)
 *           and the reinvestment of dividends (purchase).
 *           The statement neither contains the CUSIP nor the ticker symbol, only the issuer name.
 *           The number of shares of a dividend is the opening balance plus all shares
 *           released or reinvested before the dividend date.
 *           Release lines of the statement are not imported. They only contain the net quantity,
 *           the release is imported from the release detail report instead.
 *           Dividend reinvestments are contained in both the statement and the
 *           dividend reinvestment confirmation. Import only one of them for the same period.
 * @formatter:on
 */
@SuppressWarnings("nls")
public class MorganStanleyPDFExtractor extends AbstractPDFExtractor
{
    public MorganStanleyPDFExtractor(Client client)
    {
        super(client);

        addBankIdentifier("Morgan Stanley Smith Barney LLC");

        addDividendReinvestmentTransaction();
        addReleaseTransaction();
        addQuarterlyStatementTransaction();
    }

    @Override
    public String getLabel()
    {
        return "Morgan Stanley Smith Barney LLC";
    }

    private void addDividendReinvestmentTransaction()
    {
        final var type = new DocumentType("Dividend Reinvestment", //
                        documentContext -> documentContext //
                                        // @formatter:off
                                        // CUSIP: 775265677 Order Reference #: 0845006
                                        // @formatter:on
                                        .section("wkn").optional() //
                                        .match("^CUSIP:[\\s]+(?<wkn>[A-Z0-9]{9})([\\s].*)?$") //
                                        .assign((ctx, v) -> ctx.put("wkn", v.get("wkn"))));

        this.addDocumentTyp(type);

        // @formatter:off
        // You Bought 0.267 shares at $136.1234 on Trade Date 13-Jun-2022
        // @formatter:on
        var dividendBlock = new Block("^You Bought [\\.,\\d]+ shares at \\p{Sc}[\\.,\\d]+ on Trade Date .*$");
        type.addBlock(dividendBlock);
        var dividendTransaction = new Transaction<AccountTransaction>();
        dividendBlock.set(dividendTransaction);

        dividendTransaction //

                        .subject(() -> new AccountTransaction(AccountTransaction.Type.DIVIDENDS))

                        // @formatter:off
                        // Security Name: ABC ABC ABC ABC Gross Proceeds: $47.85
                        // Trading Symbol: ABC Less Transaction Expense: $11.48
                        // @formatter:on
                        .section("name", "currency", "tickerSymbol") //
                        .documentContextOptionally("wkn") //
                        .match("^Security Name: (?<name>.*) Gross Proceeds: (?<currency>\\p{Sc})[\\.,\\d]+$") //
                        .match("^Trading Symbol: (?<tickerSymbol>[A-Za-z0-9]{1,6}(?:\\.[A-Za-z]{1,4})?) Less Transaction Expense: .*$") //
                        .assign((t, v) -> t.setSecurity(getOrCreateSecurity(v)))

                        // @formatter:off
                        // Current Share Balance: 29.000
                        // @formatter:on
                        .section("shares") //
                        .match("^Current Share Balance: (?<shares>[\\.,\\d]+)$") //
                        .assign((t, v) -> t.setShares(asShares(v.get("shares"))))

                        // @formatter:off
                        // Settlement Date: 15-Jun-2022
                        // @formatter:on
                        .section("date") //
                        .match("^Settlement Date: (?<date>[\\d]{2}\\-[\\w]{3}\\-[\\d]{4})$") //
                        .assign((t, v) -> t.setDateTime(asDate(v.get("date"), Locale.US)))

                        // @formatter:off
                        // Net Proceeds: $36.37
                        // @formatter:on
                        .section("currency", "amount") //
                        .match("^Net Proceeds: (?<currency>\\p{Sc})(?<amount>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        .wrap(TransactionItem::new);

        addTaxesSectionsTransaction(dividendTransaction, type);

        // @formatter:off
        // You Bought 0.267 shares at $136.1234 on Trade Date 13-Jun-2022
        // @formatter:on
        var buyBlock = new Block("^You Bought [\\.,\\d]+ shares at \\p{Sc}[\\.,\\d]+ on Trade Date .*$");
        type.addBlock(buyBlock);
        buyBlock.set(new Transaction<BuySellEntry>()

                        .subject(() -> new BuySellEntry(PortfolioTransaction.Type.BUY))

                        // @formatter:off
                        // Security Name: ABC ABC ABC ABC Gross Proceeds: $47.85
                        // Trading Symbol: ABC Less Transaction Expense: $11.48
                        // @formatter:on
                        .section("name", "currency", "tickerSymbol") //
                        .documentContextOptionally("wkn") //
                        .match("^Security Name: (?<name>.*) Gross Proceeds: (?<currency>\\p{Sc})[\\.,\\d]+$") //
                        .match("^Trading Symbol: (?<tickerSymbol>[A-Za-z0-9]{1,6}(?:\\.[A-Za-z]{1,4})?) Less Transaction Expense: .*$") //
                        .assign((t, v) -> t.setSecurity(getOrCreateSecurity(v)))

                        // @formatter:off
                        // You Bought 0.267 shares at $136.1234 on Trade Date 13-Jun-2022
                        // @formatter:on
                        .section("shares") //
                        .match("^You Bought (?<shares>[\\.,\\d]+) shares at \\p{Sc}[\\.,\\d]+ on Trade Date .*$") //
                        .assign((t, v) -> t.setShares(asShares(v.get("shares"))))

                        // @formatter:off
                        // You Bought 0.267 shares at $136.1234 on Trade Date 13-Jun-2022
                        // @formatter:on
                        .section("date") //
                        .match("^You Bought [\\.,\\d]+ shares at \\p{Sc}[\\.,\\d]+ on Trade Date (?<date>[\\d]{2}\\-[\\w]{3}\\-[\\d]{4})$") //
                        .assign((t, v) -> t.setDate(asDate(v.get("date"), Locale.US)))

                        // @formatter:off
                        // Net Proceeds: $36.37
                        // @formatter:on
                        .section("currency", "amount") //
                        .match("^Net Proceeds: (?<currency>\\p{Sc})(?<amount>[\\.,\\d]+)$") //
                        .assign((t, v) -> {
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        // @formatter:off
                        // CUSIP: 775265677 Order Reference #: 0845006
                        // @formatter:on
                        .section("note").optional() //
                        .match("^.* (?<note>Order Reference #: .*)$") //
                        .assign((t, v) -> t.setNote(trim(v.get("note"))))

                        .wrap(BuySellEntryItem::new));
    }

    private void addReleaseTransaction()
    {
        final var type = new DocumentType("Release Detail Report", //
                        documentContext -> documentContext //
                                        // @formatter:off
                                        // CUSIP: 728031340
                                        // @formatter:on
                                        .section("wkn").optional() //
                                        .match("^CUSIP:[\\s]+(?<wkn>[A-Z0-9]{9})([\\s].*)?$") //
                                        .assign((ctx, v) -> ctx.put("wkn", v.get("wkn"))));

        this.addDocumentTyp(type);

        // @formatter:off
        // The release is booked as delivery inbound of the released quantity at FMV,
        // followed by the sale of the withheld quantity (sell-to-cover) and the removal of the tax amount.
        // @formatter:on

        var deliveryBlock = new Block("^Summary for Release$");
        type.addBlock(deliveryBlock);
        deliveryBlock.set(new Transaction<PortfolioTransaction>()

                        .subject(() -> new PortfolioTransaction(PortfolioTransaction.Type.DELIVERY_INBOUND))

                        // @formatter:off
                        // Security Name: BQzW twIsSBkn AwakjMDU lMih Withheld Quantity: 1.0000
                        // Trading Symbol: ygl x Withheld Quantity Value Per Share: $133.77
                        // *FMV @ Vest: $133.7650
                        //
                        // Security Name: NspX UnMmjcPt GRmgNwoa WccH Withheld Quantity: 3.0000
                        // Trading Symbol: TSf
                        // *FMV @ Vest / FMV Date: $140.5250 / 01-Dec-2015
                        // @formatter:on
                        .section("name", "tickerSymbol", "currency") //
                        .documentContextOptionally("wkn") //
                        .match("^Security Name: (?<name>.*) Withheld Quantity: [\\.,\\d]+$") //
                        .match("^Trading Symbol: (?<tickerSymbol>[A-Za-z0-9]{1,6}(?:\\.[A-Za-z]{1,4})?)( .*)?$") //
                        .match("^\\*FMV @ Vest.*: (?<currency>\\p{Sc})[\\.,\\d]+.*$") //
                        .assign((t, v) -> t.setSecurity(getOrCreateSecurity(v)))

                        // @formatter:off
                        // Quantity Released: 2.0000
                        // @formatter:on
                        .section("shares") //
                        .match("^Quantity Released: (?<shares>[\\.,\\d]+)[\\s]*$") //
                        .assign((t, v) -> t.setShares(asShares(v.get("shares"))))

                        // @formatter:off
                        // Release Date: 01-May-2022 **Total Tax Amount Due: $133.77
                        // Release Date: 01-Dec-2015 Federal Tax 42.8574 % $421.58
                        // @formatter:on
                        .section("date") //
                        .match("^Release Date: (?<date>[\\d]{2}\\-[\\w]{3}\\-[\\d]{4})( .*)?$") //
                        .assign((t, v) -> t.setDateTime(asDate(v.get("date"), Locale.US)))

                        // @formatter:off
                        // Total Gain (FMV x Quantity Released): $267.53
                        // @formatter:on
                        .section("currency", "amount") //
                        .match("^Total Gain \\(FMV x Quantity Released\\): (?<currency>\\p{Sc})(?<amount>[\\.,\\d]+)[\\s]*$") //
                        .assign((t, v) -> {
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        // @formatter:off
                        // Award ID: 80824152 Tax % Tax Paid
                        // Award ID: 88382462
                        // @formatter:on
                        .section("note").optional() //
                        .match("^(?<note>Award ID: [A-Z0-9]+).*$") //
                        .assign((t, v) -> t.setNote(trim(v.get("note"))))

                        .wrap(TransactionItem::new));

        var saleBlock = new Block("^Summary for Release$");
        type.addBlock(saleBlock);
        saleBlock.set(new Transaction<BuySellEntry>()

                        .subject(() -> new BuySellEntry(PortfolioTransaction.Type.SELL))

                        // @formatter:off
                        // Security Name: BQzW twIsSBkn AwakjMDU lMih Withheld Quantity: 1.0000
                        // Trading Symbol: ygl x Withheld Quantity Value Per Share: $133.77
                        // *FMV @ Vest: $133.7650
                        // @formatter:on
                        .section("name", "tickerSymbol", "currency") //
                        .documentContextOptionally("wkn") //
                        .match("^Security Name: (?<name>.*) Withheld Quantity: [\\.,\\d]+$") //
                        .match("^Trading Symbol: (?<tickerSymbol>[A-Za-z0-9]{1,6}(?:\\.[A-Za-z]{1,4})?)( .*)?$") //
                        .match("^\\*FMV @ Vest.*: (?<currency>\\p{Sc})[\\.,\\d]+.*$") //
                        .assign((t, v) -> t.setSecurity(getOrCreateSecurity(v)))

                        // @formatter:off
                        // Quantity Withheld: 1.0000
                        // Quantity Withheld: (3.0000)
                        // @formatter:on
                        .section("shares") //
                        .match("^Quantity Withheld: \\(?(?<shares>[\\.,\\d]+)\\)?[\\s]*$") //
                        .assign((t, v) -> t.setShares(asShares(v.get("shares"))))

                        // @formatter:off
                        // Release Date: 01-May-2022 **Total Tax Amount Due: $133.77
                        // @formatter:on
                        .section("date") //
                        .match("^Release Date: (?<date>[\\d]{2}\\-[\\w]{3}\\-[\\d]{4})( .*)?$") //
                        .assign((t, v) -> t.setDate(asDate(v.get("date"), Locale.US)))

                        // @formatter:off
                        // Plan Name: 3900 Withheld Quantity Value: $133.77
                        // Award Date: 16-Jun-2011 Withheld Quantity Value: $421.58
                        // @formatter:on
                        .section("currency", "amount") //
                        .match("^.* Withheld Quantity Value: (?<currency>\\p{Sc})(?<amount>[\\.,\\d]+)[\\s]*$") //
                        .assign((t, v) -> {
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        // @formatter:off
                        // Award ID: 80824152 Tax % Tax Paid
                        // @formatter:on
                        .section("note").optional() //
                        .match("^(?<note>Award ID: [A-Z0-9]+).*$") //
                        .assign((t, v) -> t.setNote(trim(v.get("note"))))

                        .wrap(t -> t.getPortfolioTransaction().getShares() == 0 ? null : new BuySellEntryItem(t)));

        var removalBlock = new Block("^Summary for Release$");
        type.addBlock(removalBlock);
        removalBlock.set(new Transaction<AccountTransaction>()

                        .subject(() -> new AccountTransaction(AccountTransaction.Type.REMOVAL))

                        // @formatter:off
                        // Release Date: 01-May-2022 **Total Tax Amount Due: $133.77
                        // @formatter:on
                        .section("date") //
                        .match("^Release Date: (?<date>[\\d]{2}\\-[\\w]{3}\\-[\\d]{4})( .*)?$") //
                        .assign((t, v) -> t.setDateTime(asDate(v.get("date"), Locale.US)))

                        // @formatter:off
                        // Total Tax Amount Due: $133.77
                        // Total Tax Amount: $354.12
                        // @formatter:on
                        .section("currency", "amount") //
                        .match("^Total Tax Amount( Due)?: (?<currency>\\p{Sc})(?<amount>[\\.,\\d]+)[\\s]*$") //
                        .assign((t, v) -> {
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                            t.setNote("Tax withheld to cover");
                        })

                        // @formatter:off
                        // Award ID: 80824152 Tax % Tax Paid
                        // @formatter:on
                        .section("note").optional() //
                        .match("^(?<note>Award ID: [A-Z0-9]+).*$") //
                        .assign((t, v) -> t.setNote(concatenate(t.getNote(), trim(v.get("note")), " | ")))

                        .wrap(t -> t.getAmount() == 0 ? null : new TransactionItem(t)));
    }

    private void addQuarterlyStatementTransaction()
    {
        final var type = new DocumentType("SHARE PURCHASE AND HOLDINGS", (context, lines) -> {
            // @formatter:off
            // Issuer Description: INTL BUSINESS MACHINES CORP P.O. Box 182616 1-800-367-4777; 1-801-617-7414
            // Share Price $294.7800 $282.1600
            // Number of Shares 62.055 93.055
            // 7/10/25 Release 31.000 $285.5550
            // 3/13/23 Dividend Reinvested 0.333 $125.7774 (49.27) (41.88)
            // 9/10/25 Dividend Credit $156.33 $156.33
            // @formatter:on
            var pName = Pattern.compile("^Issuer Description: (?<name>.*) P\\.O\\. Box .*$");
            var pCurrency = Pattern.compile("^Share Price (?<currency>\\p{Sc})[\\.,\\d]+ .*$");
            var pOpening = Pattern.compile("^Number of Shares (?<opening>[\\.,\\d]+) [\\.,\\d]+$");
            var pShareChange = Pattern.compile("^(?<date>[\\d]{1,2}/[\\d]{1,2}/[\\d]{2}) (Release|Dividend Reinvested) (?<shares>[\\.,\\d]+) .*$");
            var pDividend = Pattern.compile("^(?<date>[\\d]{1,2}/[\\d]{1,2}/[\\d]{2}) Dividend Credit .*$");

            var opening = BigDecimal.ZERO;
            var shareChanges = new ArrayList<Pair<LocalDateTime, BigDecimal>>();
            var dividendDates = new ArrayList<String>();

            for (String line : lines)
            {
                var m = pName.matcher(line);
                if (m.matches() && !context.containsKey("name"))
                    context.put("name", trim(m.group("name")));

                m = pCurrency.matcher(line);
                if (m.matches() && !context.containsKey("currency"))
                    context.put("currency", m.group("currency"));

                m = pOpening.matcher(line);
                if (m.matches())
                    opening = asSharesBigDecimal(m.group("opening"));

                m = pShareChange.matcher(line);
                if (m.matches())
                    shareChanges.add(new Pair<>(asStatementDate(m.group("date")), asSharesBigDecimal(m.group("shares"))));

                m = pDividend.matcher(line);
                if (m.matches())
                    dividendDates.add(m.group("date"));
            }

            // The number of shares entitled to the dividend is the opening
            // balance plus all shares added before the dividend date
            for (String date : dividendDates)
            {
                var dividendDate = asStatementDate(date);
                var shares = opening;
                for (var change : shareChanges)
                {
                    if (change.getLeft().isBefore(dividendDate))
                        shares = shares.add(change.getRight());
                }
                context.put("shares_" + date, shares.toPlainString());
            }
        });

        this.addDocumentTyp(type);

        // @formatter:off
        // 3/10/26 Dividend Credit $156.33 $156.33
        // 3/10/26 Withholding Tax (23.45)
        // @formatter:on
        var dividendBlock = new Block("^[\\d]{1,2}/[\\d]{1,2}/[\\d]{2} Dividend Credit .*$");
        type.addBlock(dividendBlock);
        dividendBlock.set(new Transaction<AccountTransaction>()

                        .subject(() -> new AccountTransaction(AccountTransaction.Type.DIVIDENDS))

                        .section("date", "amount") //
                        .documentContext("name", "currency") //
                        .match("^(?<date>[\\d]{1,2}/[\\d]{1,2}/[\\d]{2}) Dividend Credit \\p{Sc}(?<amount>[\\.,\\d]+) .*$") //
                        .assign((t, v) -> {
                            t.setSecurity(getOrCreateSecurity(v));
                            t.setDateTime(asStatementDate(v.get("date")));
                            t.setShares(asShares(type.getCurrentContext().get("shares_" + v.get("date"))));
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        .section("tax").optional() //
                        .documentContext("currency") //
                        .match("^[\\d]{1,2}/[\\d]{1,2}/[\\d]{2} Withholding Tax \\((?<tax>[\\.,\\d]+)\\)$") //
                        .assign((t, v) -> {
                            // the dividend credit is the gross amount
                            t.setAmount(t.getAmount() - asAmount(v.get("tax")));
                            processTaxEntries(t, v, type);
                        })

                        .wrap(TransactionItem::new));

        // @formatter:off
        // 3/11/26 Proceeds Disbursement (132.88)
        // @formatter:on
        var removalBlock = new Block("^[\\d]{1,2}/[\\d]{1,2}/[\\d]{2} Proceeds Disbursement .*$");
        type.addBlock(removalBlock);
        removalBlock.set(new Transaction<AccountTransaction>()

                        .subject(() -> new AccountTransaction(AccountTransaction.Type.REMOVAL))

                        .section("date", "amount") //
                        .documentContext("currency") //
                        .match("^(?<date>[\\d]{1,2}/[\\d]{1,2}/[\\d]{2}) Proceeds Disbursement \\((?<amount>[\\.,\\d]+)\\)$") //
                        .assign((t, v) -> {
                            t.setDateTime(asStatementDate(v.get("date")));
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        .wrap(TransactionItem::new));

        // @formatter:off
        // 3/13/23 Dividend Reinvested 0.333 $125.7774 (49.27) (41.88)
        // @formatter:on
        var buyBlock = new Block("^[\\d]{1,2}/[\\d]{1,2}/[\\d]{2} Dividend Reinvested .*$");
        type.addBlock(buyBlock);
        buyBlock.set(new Transaction<BuySellEntry>()

                        .subject(() -> new BuySellEntry(PortfolioTransaction.Type.BUY))

                        .section("date", "shares", "amount") //
                        .documentContext("name", "currency") //
                        .match("^(?<date>[\\d]{1,2}/[\\d]{1,2}/[\\d]{2}) Dividend Reinvested (?<shares>[\\.,\\d]+) \\p{Sc}[\\.,\\d]+ \\([\\.,\\d]+\\) \\((?<amount>[\\.,\\d]+)\\)$") //
                        .assign((t, v) -> {
                            t.setSecurity(getOrCreateSecurity(v));
                            t.setDate(asStatementDate(v.get("date")));
                            t.setShares(asShares(v.get("shares")));
                            t.setCurrencyCode(asCurrencyCode(v.get("currency")));
                            t.setAmount(asAmount(v.get("amount")));
                        })

                        .wrap(BuySellEntryItem::new));
    }

    /**
     * Dates in the quarterly statement have the format M/d/yy (e.g. 3/10/26).
     * The US date formatters expect a two-digit month.
     */
    private LocalDateTime asStatementDate(String date)
    {
        return asDate(date.replaceFirst("^([\\d])/", "0$1/"), Locale.US);
    }

    private BigDecimal asSharesBigDecimal(String value)
    {
        return ExtractorUtils.convertToNumberBigDecimal(value, Values.Share, "en", "US");
    }

    private <T extends Transaction<?>> void addTaxesSectionsTransaction(T transaction, DocumentType type)
    {
        transaction //

                        // @formatter:off
                        // IRS Backup Withholding: $11.48
                        // IRS Nonresident Alien Withholding: $7.52
                        // @formatter:on
                        .section("currency", "tax").multipleTimes().optional() //
                        .match("^IRS .*Withholding: (?<currency>\\p{Sc})(?<tax>[\\.,\\d]+)$") //
                        .assign((t, v) -> processTaxEntries(t, v, type));
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
}
