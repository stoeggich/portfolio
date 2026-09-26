package name.abuchen.portfolio.datatransfer.pdf.morganstanley;

import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.dividend;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasAmount;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasCurrencyCode;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasDate;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasExDate;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasFees;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasGrossValue;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasIsin;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasName;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasNote;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasShares;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasSource;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasTaxes;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasTicker;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasWkn;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.inboundDelivery;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.purchase;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.removal;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.sale;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.security;
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

import name.abuchen.portfolio.datatransfer.actions.AssertImportActions;
import name.abuchen.portfolio.datatransfer.pdf.MorganStanleyPDFExtractor;
import name.abuchen.portfolio.datatransfer.pdf.PDFInputFile;
import name.abuchen.portfolio.model.Client;

@SuppressWarnings("nls")
public class MorganStanleyPDFExtractorTest
{
    @Test
    public void testDividendReinvestment01()
    {
        var extractor = new MorganStanleyPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "DividendReinvestment01.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(1L));
        assertThat(countAccountTransactions(results), is(1L));
        assertThat(countAccountTransfers(results), is(0L));
        assertThat(countItemsWithFailureMessage(results), is(0L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(3));
        new AssertImportActions().check(results, "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn("775265677"), hasTicker("ABC"), //
                        hasName("ABC ABC ABC ABC"), //
                        hasCurrencyCode("USD"))));

        // check dividends transaction
        assertThat(results, hasItem(dividend( //
                        hasDate("2022-06-15T00:00"), hasExDate(null), //
                        hasShares(29.000), //
                        hasSource("DividendReinvestment01.txt"), //
                        hasNote(null), //
                        hasAmount("USD", 36.37), hasGrossValue("USD", 47.85), //
                        hasTaxes("USD", 11.48), hasFees("USD", 0.00))));

        // check purchase transaction
        assertThat(results, hasItem(purchase( //
                        hasDate("2022-06-13T00:00"), hasShares(0.267), //
                        hasSource("DividendReinvestment01.txt"), //
                        hasNote("Order Reference #: 0845006"), //
                        hasAmount("USD", 36.37), hasGrossValue("USD", 36.37), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));
    }

    @Test
    public void testDividendReinvestment02()
    {
        var extractor = new MorganStanleyPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "DividendReinvestment02.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(1L));
        assertThat(countAccountTransactions(results), is(1L));
        assertThat(countAccountTransfers(results), is(0L));
        assertThat(countItemsWithFailureMessage(results), is(0L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(3));
        new AssertImportActions().check(results, "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn("926151023"), hasTicker("ABC"), //
                        hasName("ABC ABC ABC ABC"), //
                        hasCurrencyCode("USD"))));

        // check dividends transaction
        assertThat(results, hasItem(dividend( //
                        hasDate("2023-06-15T00:00"), hasExDate(null), //
                        hasShares(30.191), //
                        hasSource("DividendReinvestment02.txt"), //
                        hasNote(null), //
                        hasAmount("USD", 42.60), hasGrossValue("USD", 50.12), //
                        hasTaxes("USD", 7.52), hasFees("USD", 0.00))));

        // check purchase transaction
        assertThat(results, hasItem(purchase( //
                        hasDate("2023-06-13T00:00"), hasShares(0.310), //
                        hasSource("DividendReinvestment02.txt"), //
                        hasNote("Order Reference #: 94509"), //
                        hasAmount("USD", 42.60), hasGrossValue("USD", 42.60), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));
    }

    @Test
    public void testRelease01()
    {
        var extractor = new MorganStanleyPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "Release01.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(1L));
        assertThat(countAccountTransactions(results), is(2L));
        assertThat(countAccountTransfers(results), is(0L));
        assertThat(countItemsWithFailureMessage(results), is(0L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(4));
        new AssertImportActions().check(results, "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn(null), hasTicker("TSf"), //
                        hasName("NspX UnMmjcPt GRmgNwoa WccH"), //
                        hasCurrencyCode("USD"))));

        // check delivery inbound (Einlieferung) transaction
        assertThat(results, hasItem(inboundDelivery( //
                        hasDate("2015-12-01T00:00"), hasShares(7.00), //
                        hasSource("Release01.txt"), //
                        hasNote("Award ID: 88382462"), //
                        hasAmount("USD", 983.68), hasGrossValue("USD", 983.68), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));

        // check sell-to-cover transaction
        assertThat(results, hasItem(sale( //
                        hasDate("2015-12-01T00:00"), hasShares(3.00), //
                        hasSource("Release01.txt"), //
                        hasNote("Award ID: 88382462"), //
                        hasAmount("USD", 421.58), hasGrossValue("USD", 421.58), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));

        // check removal of the withheld taxes
        assertThat(results, hasItem(removal( //
                        hasDate("2015-12-01"), hasAmount("USD", 354.12), //
                        hasSource("Release01.txt"), //
                        hasNote("Tax withheld to cover | Award ID: 88382462"))));
    }

    @Test
    public void testRelease02()
    {
        var extractor = new MorganStanleyPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "Release02.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(1L));
        assertThat(countAccountTransactions(results), is(2L));
        assertThat(countAccountTransfers(results), is(0L));
        assertThat(countItemsWithFailureMessage(results), is(0L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(4));
        new AssertImportActions().check(results, "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn("728031340"), hasTicker("ygl"), //
                        hasName("BQzW twIsSBkn AwakjMDU lMih"), //
                        hasCurrencyCode("USD"))));

        // check delivery inbound (Einlieferung) transaction
        assertThat(results, hasItem(inboundDelivery( //
                        hasDate("2022-05-01T00:00"), hasShares(2.00), //
                        hasSource("Release02.txt"), //
                        hasNote("Award ID: 80824152"), //
                        hasAmount("USD", 267.53), hasGrossValue("USD", 267.53), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));

        // check sell-to-cover transaction
        assertThat(results, hasItem(sale( //
                        hasDate("2022-05-01T00:00"), hasShares(1.00), //
                        hasSource("Release02.txt"), //
                        hasNote("Award ID: 80824152"), //
                        hasAmount("USD", 133.77), hasGrossValue("USD", 133.77), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));

        // check removal of the withheld taxes
        assertThat(results, hasItem(removal( //
                        hasDate("2022-05-01"), hasAmount("USD", 133.77), //
                        hasSource("Release02.txt"), //
                        hasNote("Tax withheld to cover | Award ID: 80824152"))));
    }

    @Test
    public void testRelease03()
    {
        var extractor = new MorganStanleyPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "Release03.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(1L));
        assertThat(countAccountTransactions(results), is(2L));
        assertThat(countAccountTransfers(results), is(0L));
        assertThat(countItemsWithFailureMessage(results), is(0L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(4));
        new AssertImportActions().check(results, "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn("459200101"), hasTicker("IBM"), //
                        hasName("INTL BUSINESS MACHINES CORP"), //
                        hasCurrencyCode("USD"))));

        // check delivery inbound (Einlieferung) transaction
        assertThat(results, hasItem(inboundDelivery( //
                        hasDate("2026-07-10T00:00"), hasShares(19.00), //
                        hasSource("Release03.txt"), //
                        hasNote("Award ID: 1234567A"), //
                        hasAmount("USD", 5569.57), hasGrossValue("USD", 5569.57), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));

        // check sell-to-cover transaction
        assertThat(results, hasItem(sale( //
                        hasDate("2026-07-10T00:00"), hasShares(9.00), //
                        hasSource("Release03.txt"), //
                        hasNote("Award ID: 1234567A"), //
                        hasAmount("USD", 2638.22), hasGrossValue("USD", 2638.22), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));

        // check removal of the withheld taxes
        assertThat(results, hasItem(removal( //
                        hasDate("2026-07-10"), hasAmount("USD", 2638.22), //
                        hasSource("Release03.txt"), //
                        hasNote("Tax withheld to cover | Award ID: 1234567A"))));
    }

    @Test
    public void testRelease04()
    {
        var extractor = new MorganStanleyPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "Release04.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(1L));
        assertThat(countAccountTransactions(results), is(2L));
        assertThat(countAccountTransfers(results), is(0L));
        assertThat(countItemsWithFailureMessage(results), is(0L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(4));
        new AssertImportActions().check(results, "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn("459200101"), hasTicker("IBM"), //
                        hasName("INTL BUSINESS MACHINES CORP"), //
                        hasCurrencyCode("USD"))));

        // check delivery inbound (Einlieferung) transaction
        assertThat(results, hasItem(inboundDelivery( //
                        hasDate("2025-07-10T00:00"), hasShares(57.00), //
                        hasSource("Release04.txt"), //
                        hasNote("Award ID: 12345678"), //
                        hasAmount("USD", 16276.64), hasGrossValue("USD", 16276.64), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));

        // check sell-to-cover transaction
        assertThat(results, hasItem(sale( //
                        hasDate("2025-07-10T00:00"), hasShares(26.00), //
                        hasSource("Release04.txt"), //
                        hasNote("Award ID: 12345678"), //
                        hasAmount("USD", 7424.43), hasGrossValue("USD", 7424.43), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));

        // check removal of the withheld taxes
        assertThat(results, hasItem(removal( //
                        hasDate("2025-07-10"), hasAmount("USD", 7424.43), //
                        hasSource("Release04.txt"), //
                        hasNote("Tax withheld to cover | Award ID: 12345678"))));
    }

    @Test
    public void testQuarterlyStatement01()
    {
        var extractor = new MorganStanleyPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "QuarterlyStatement01.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(0L));
        assertThat(countAccountTransactions(results), is(2L));
        assertThat(countAccountTransfers(results), is(0L));
        assertThat(countItemsWithFailureMessage(results), is(0L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(3));
        new AssertImportActions().check(results, "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn(null), hasTicker(null), //
                        hasName("INTL BUSINESS MACHINES CORP"), //
                        hasCurrencyCode("USD"))));

        // check dividends transaction
        assertThat(results, hasItem(dividend( //
                        hasDate("2026-03-10T00:00"), hasShares(93.055), //
                        hasSource("QuarterlyStatement01.txt"), //
                        hasNote(null), //
                        hasAmount("USD", 132.88), hasGrossValue("USD", 156.33), //
                        hasTaxes("USD", 23.45), hasFees("USD", 0.00))));

        // check disbursement of the dividend
        assertThat(results, hasItem(removal( //
                        hasDate("2026-03-11"), hasAmount("USD", 132.88), //
                        hasSource("QuarterlyStatement01.txt"), hasNote(null))));
    }

    @Test
    public void testQuarterlyStatement02()
    {
        var extractor = new MorganStanleyPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "QuarterlyStatement02.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(0L));
        assertThat(countAccountTransactions(results), is(2L));
        assertThat(countAccountTransfers(results), is(0L));
        assertThat(countItemsWithFailureMessage(results), is(0L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(3));
        new AssertImportActions().check(results, "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn(null), hasTicker(null), //
                        hasName("INTL BUSINESS MACHINES CORP"), //
                        hasCurrencyCode("USD"))));

        // check dividends transaction
        assertThat(results, hasItem(dividend( //
                        hasDate("2026-06-10T00:00"), hasShares(93.055), //
                        hasSource("QuarterlyStatement02.txt"), //
                        hasNote(null), //
                        hasAmount("USD", 133.67), hasGrossValue("USD", 157.26), //
                        hasTaxes("USD", 23.59), hasFees("USD", 0.00))));

        // check disbursement of the dividend
        assertThat(results, hasItem(removal( //
                        hasDate("2026-06-11"), hasAmount("USD", 133.67), //
                        hasSource("QuarterlyStatement02.txt"), hasNote(null))));
    }

    @Test
    public void testQuarterlyStatement03()
    {
        var extractor = new MorganStanleyPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "QuarterlyStatement03.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(1L));
        assertThat(countAccountTransactions(results), is(1L));
        assertThat(countAccountTransfers(results), is(0L));
        assertThat(countItemsWithFailureMessage(results), is(0L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(3));
        new AssertImportActions().check(results, "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn(null), hasTicker(null), //
                        hasName("INTL BUSINESS MACHINES CORP"), //
                        hasCurrencyCode("USD"))));

        // check dividends transaction
        assertThat(results, hasItem(dividend( //
                        hasDate("2023-03-10T00:00"), hasShares(29.858), //
                        hasSource("QuarterlyStatement03.txt"), //
                        hasNote(null), //
                        hasAmount("USD", 41.88), hasGrossValue("USD", 49.27), //
                        hasTaxes("USD", 7.39), hasFees("USD", 0.00))));

        // check dividend reinvestment
        assertThat(results, hasItem(purchase( //
                        hasDate("2023-03-13T00:00"), hasShares(0.333), //
                        hasSource("QuarterlyStatement03.txt"), //
                        hasNote(null), //
                        hasAmount("USD", 41.88), hasGrossValue("USD", 41.88), //
                        hasTaxes("USD", 0.00), hasFees("USD", 0.00))));
    }

    @Test
    public void testQuarterlyStatement04()
    {
        var extractor = new MorganStanleyPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<>();

        var results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "QuarterlyStatement04.txt"), errors);

        assertThat(errors, empty());
        assertThat(countSecurities(results), is(1L));
        assertThat(countBuySell(results), is(0L));
        assertThat(countAccountTransactions(results), is(2L));
        assertThat(countAccountTransfers(results), is(0L));
        assertThat(countItemsWithFailureMessage(results), is(0L));
        assertThat(countSkippedItems(results), is(0L));
        assertThat(results.size(), is(3));
        new AssertImportActions().check(results, "USD");

        // check security
        assertThat(results, hasItem(security( //
                        hasIsin(null), hasWkn(null), hasTicker(null), //
                        hasName("INTL BUSINESS MACHINES CORP"), //
                        hasCurrencyCode("USD"))));

        // check dividends transaction (shares include the release before the
        // dividend date, the release itself is not imported)
        assertThat(results, hasItem(dividend( //
                        hasDate("2025-09-10T00:00"), hasShares(93.055), //
                        hasSource("QuarterlyStatement04.txt"), //
                        hasNote(null), //
                        hasAmount("USD", 132.88), hasGrossValue("USD", 156.33), //
                        hasTaxes("USD", 23.45), hasFees("USD", 0.00))));

        // check disbursement of the dividend
        assertThat(results, hasItem(removal( //
                        hasDate("2025-09-11"), hasAmount("USD", 132.88), //
                        hasSource("QuarterlyStatement04.txt"), hasNote(null))));
    }
}
