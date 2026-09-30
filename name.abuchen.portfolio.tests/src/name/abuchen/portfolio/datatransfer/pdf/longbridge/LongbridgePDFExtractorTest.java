package name.abuchen.portfolio.datatransfer.pdf.longbridge;

import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.deposit;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasAmount;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasCurrencyCode;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasDate;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasFees;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasForexGrossValue;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasGrossValue;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasIsin;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasName;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasNote;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasShares;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasSource;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasTaxes;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasTicker;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasWkn;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.inboundCash;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.outboundCash;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.purchase;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.sale;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.security;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.withFailureMessage;
import static name.abuchen.portfolio.datatransfer.ExtractorTestUtilities.countAccountTransactions;
import static name.abuchen.portfolio.datatransfer.ExtractorTestUtilities.countAccountTransfers;
import static name.abuchen.portfolio.datatransfer.ExtractorTestUtilities.countBuySell;
import static name.abuchen.portfolio.datatransfer.ExtractorTestUtilities.countItemsWithFailureMessage;
import static name.abuchen.portfolio.datatransfer.ExtractorTestUtilities.countSecurities;
import static name.abuchen.portfolio.datatransfer.ExtractorTestUtilities.countSkippedItems;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.datatransfer.actions.AssertImportActions;
import name.abuchen.portfolio.datatransfer.pdf.LongbridgePDFExtractor;
import name.abuchen.portfolio.datatransfer.pdf.PDFInputFile;
import name.abuchen.portfolio.model.Client;

@SuppressWarnings("nls")
public class LongbridgePDFExtractorTest
{
    @Test
    public void testKontoauszug01()
    {
        var extractor = new LongbridgePDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "Kontoauszug01.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(7L));
        assertThat(countAccountTransactions(results), is(1L));
        assertThat(countAccountTransfers(results), is(1L));
        assertThat(countItemsWithFailureMessage(results), is(5L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(10));
        new AssertImportActions().check(results, "HKD", "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn(null), hasTicker(null), //
                        hasName("英伟达"), //
                        hasCurrencyCode("USD"))));

        // check 1st failure message (short sale)
        assertThat(results, hasItem(withFailureMessage( //
                        Messages.MsgErrorTransactionTypeNotSupportedOrRequired, //
                        sale( //
                                        hasDate("2024-08-28T10:08:09"), hasShares(1.00), //
                                        hasSource("Kontoauszug01.txt"), //
                                        hasNote("Ord.-Nr.: OS2024082909203"), //
                                        hasAmount("USD", 126.31), hasGrossValue("USD", 127.33), //
                                        hasTaxes("USD", 0.00), hasFees("USD", 0.00 + 1.00 + 0.00 + 0.01 + 0.01 + 0.00)))));

        // check 2nd failure message (short sale)
        assertThat(results, hasItem(withFailureMessage( //
                        Messages.MsgErrorTransactionTypeNotSupportedOrRequired, //
                        sale( //
                                        hasDate("2024-08-28T09:46:41"), hasShares(1.00), //
                                        hasSource("Kontoauszug01.txt"), //
                                        hasNote("Ord.-Nr.: OS2024082909466"), //
                                        hasAmount("USD", 125.94), hasGrossValue("USD", 126.96), //
                                        hasTaxes("USD", 0.00), hasFees("USD", 0.00 + 1.00 + 0.00 + 0.01 + 0.01 + 0.00)))));

        // check 3rd failure message (buy to cover)
        assertThat(results, hasItem(withFailureMessage( //
                        Messages.MsgErrorTransactionTypeNotSupportedOrRequired, //
                        purchase( //
                                        hasDate("2024-08-28T12:02:18"), hasShares(2.00), //
                                        hasSource("Kontoauszug01.txt"), //
                                        hasNote("Ord.-Nr.: OS2024082921950"), //
                                        hasAmount("USD", 252.11), hasGrossValue("USD", 251.10), //
                                        hasTaxes("USD", 0.00), hasFees("USD", 0.00 + 1.00 + 0.01 + 0.00 + 0.00 + 0.00)))));

        // check 4th failure message (short sale)
        assertThat(results, hasItem(withFailureMessage( //
                        Messages.MsgErrorTransactionTypeNotSupportedOrRequired, //
                        sale( //
                                        hasDate("2024-08-28T12:07:19"), hasShares(1.00), //
                                        hasSource("Kontoauszug01.txt"), //
                                        hasNote("Ord.-Nr.: OS2024082925321"), //
                                        hasAmount("USD", 124.50), hasGrossValue("USD", 125.52), //
                                        hasTaxes("USD", 0.00), hasFees("USD", 0.00 + 1.00 + 0.00 + 0.01 + 0.01 + 0.00)))));

        // check 5th failure message (buy to cover)
        assertThat(results, hasItem(withFailureMessage( //
                        Messages.MsgErrorTransactionTypeNotSupportedOrRequired, //
                        purchase( //
                                        hasDate("2024-08-28T15:31:27"), hasShares(1.00), //
                                        hasSource("Kontoauszug01.txt"), //
                                        hasNote("Ord.-Nr.: OS2024082925674"), //
                                        hasAmount("USD", 127.49), hasGrossValue("USD", 126.49), //
                                        hasTaxes("USD", 0.00), hasFees("USD", 0.00 + 1.00 + 0.00 + 0.00 + 0.00 + 0.00)))));

        // check 1st purchase transaction
        assertThat(results, hasItem(purchase( //
                        hasDate("2024-08-29T10:36:00"), hasShares(1.00), //
                        hasSource("Kontoauszug01.txt"), //
                        hasNote("Ord.-Nr.: OS2024083022864"), //
                        hasAmount("USD", 122.32), hasGrossValue("USD", 121.32), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00 + 1.00 + 0.00 + 0.00 + 0.00 + 0.00))));

        // check 2nd purchase transaction
        assertThat(results, hasItem(purchase( //
                        hasDate("2024-08-29T14:28:26"), hasShares(1.00), //
                        hasSource("Kontoauszug01.txt"), //
                        hasNote("Ord.-Nr.: OS2024083022927"), //
                        hasAmount("USD", 120.20), hasGrossValue("USD", 119.20), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00 + 1.00 + 0.00 + 0.00 + 0.00 + 0.00))));

        // check deposit transaction
        assertThat(results, hasItem(deposit(hasDate("2024-08-28"), hasAmount("HKD", 10000.00), //
                        hasSource("Kontoauszug01.txt"), hasNote(null))));

        // check cash transfer transaction (currency exchange)
        assertThat(results, hasItem(outboundCash(hasDate("2024-08-29"), hasAmount("HKD", 10000.00), //
                        hasForexGrossValue("USD", 1279.00), //
                        hasSource("Kontoauszug01.txt"), hasNote(null))));
        assertThat(results, hasItem(inboundCash(hasDate("2024-08-29"), hasAmount("USD", 1279.00), //
                        hasSource("Kontoauszug01.txt"), hasNote(null))));
    }
}
