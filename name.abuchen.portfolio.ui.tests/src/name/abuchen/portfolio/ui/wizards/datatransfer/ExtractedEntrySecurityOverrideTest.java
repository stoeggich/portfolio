package name.abuchen.portfolio.ui.wizards.datatransfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import name.abuchen.portfolio.datatransfer.Extractor;
import name.abuchen.portfolio.datatransfer.ImportAction;
import name.abuchen.portfolio.datatransfer.ImportAction.Status.Code;
import name.abuchen.portfolio.datatransfer.actions.DetectDuplicatesAction;
import name.abuchen.portfolio.junit.AccountBuilder;
import name.abuchen.portfolio.junit.PortfolioBuilder;
import name.abuchen.portfolio.junit.SecurityBuilder;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.InvestmentPlan;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Values;

@SuppressWarnings("nls")
public class ExtractedEntrySecurityOverrideTest
{
    private static final String DATE = "2024-01-02";

    private Client client;
    private Account account;
    private Portfolio portfolio;
    private Security existing;
    private Security planned;
    private Security newSecurity;

    private ExtractedEntry securityEntry;
    private ExtractedEntry purchase;
    private List<ExtractedEntry> entries;

    private ImportAction.Context context;

    @Before
    public void setup()
    {
        client = new Client();
        account = new AccountBuilder(CurrencyUnit.EUR).addTo(client);
        existing = new SecurityBuilder(CurrencyUnit.EUR).addTo(client);
        portfolio = new PortfolioBuilder(account)
                        .buy(existing, DATE, Values.Share.factorize(1), Values.Amount.factorize(1000)).addTo(client);

        // an existing security with a transaction of an investment plan
        planned = new SecurityBuilder(CurrencyUnit.EUR).addTo(client);
        var plan = new InvestmentPlan("plan");
        plan.setSecurity(planned);
        plan.setPortfolio(portfolio);
        client.addPlan(plan);

        var planTransaction = new PortfolioTransaction();
        planTransaction.setType(PortfolioTransaction.Type.BUY);
        planTransaction.setDateTime(LocalDateTime.parse(DATE + "T00:00"));
        planTransaction.setSecurity(planned);
        planTransaction.setShares(Values.Share.factorize(1));
        planTransaction.setCurrencyCode(CurrencyUnit.EUR);
        planTransaction.setAmount(Values.Amount.factorize(1000));
        portfolio.addTransaction(planTransaction);
        plan.getTransactions().add(planTransaction);

        // extracted again from the same document, but the extractor did not
        // recognize the existing security and creates a new one
        newSecurity = new SecurityBuilder(CurrencyUnit.EUR).get();

        var entry = new BuySellEntry();
        entry.setType(PortfolioTransaction.Type.BUY);
        entry.setDate(LocalDateTime.parse(DATE + "T00:00"));
        entry.setSecurity(newSecurity);
        entry.setShares(Values.Share.factorize(1));
        entry.setCurrencyCode(CurrencyUnit.EUR);
        entry.setAmount(Values.Amount.factorize(1000));

        securityEntry = new ExtractedEntry(new Extractor.SecurityItem(newSecurity));
        purchase = new ExtractedEntry(new Extractor.BuySellEntryItem(entry));
        purchase.setSecurityDependency(securityEntry);
        entries = List.of(securityEntry, purchase);

        context = new ImportAction.Context()
        {
            @Override
            public Account getAccount(String currencyCode)
            {
                return account;
            }

            @Override
            public Portfolio getPortfolio()
            {
                return portfolio;
            }

            @Override
            public Account getSecondaryAccount(String currencyCode)
            {
                return account;
            }

            @Override
            public Portfolio getSecondaryPortfolio()
            {
                return portfolio;
            }
        };
    }

    /** as "Use existing security" of the table */
    private void useExistingSecurity()
    {
        useExistingSecurity(existing);
    }

    private void useExistingSecurity(Security security)
    {
        securityEntry.setImported(false);
        purchase.setSecurityOverride(security);
    }

    /** as the checks of the review page */
    private void check()
    {
        var restore = ExtractedEntry.applySecurityOverrides(entries);
        try
        {
            purchase.clearStatus();
            purchase.addStatus(purchase.getItem().apply(new DetectDuplicatesAction(client), context));
        }
        finally
        {
            restore.run();
        }
    }

    private Code checkForDuplicates()
    {
        var restore = ExtractedEntry.applySecurityOverrides(entries);
        try
        {
            return purchase.getItem().apply(new DetectDuplicatesAction(client), context).getCode();
        }
        finally
        {
            restore.run();
        }
    }

    @Test
    public void testNewSecurityIsNoDuplicate()
    {
        assertThat(checkForDuplicates(), is(Code.OK));
    }

    @Test
    public void testDuplicateIsDetectedWithExistingSecurity()
    {
        useExistingSecurity();

        assertThat(checkForDuplicates(), is(Code.WARNING));
    }

    @Test
    public void testOriginalSecurityIsRestored()
    {
        useExistingSecurity();

        checkForDuplicates();

        assertThat(purchase.getItem().getSecurity(), is(newSecurity));
        assertThat(purchase.getSecurityOverride(), is(existing));
    }

    @Test
    public void testSecurityEntryKeepsNewSecurity()
    {
        useExistingSecurity();
        securityEntry.setSecurityOverride(existing);

        var restore = ExtractedEntry.applySecurityOverrides(entries);
        assertThat(securityEntry.getItem().getSecurity(), is(newSecurity));
        assertThat(purchase.getItem().getSecurity(), is(existing));

        restore.run();
        assertThat(purchase.getItem().getSecurity(), is(newSecurity));
    }

    @Test
    public void testResetOverrideIsNoDuplicateAgain()
    {
        useExistingSecurity();
        assertThat(checkForDuplicates(), is(Code.WARNING));

        // as "Import" of the security entry
        securityEntry.setImported(true);
        purchase.setSecurityOverride(null);

        assertThat(checkForDuplicates(), is(Code.OK));
    }

    @Test
    public void testInvestmentPlanMatchIsImported()
    {
        useExistingSecurity(planned);
        check();

        assertThat(purchase.getMaxCode(), is(Code.WARNING));
        assertThat(purchase.getItem().isInvestmentPlanItem(), is(true));
        assertThat(purchase.isImported(), is(true));
    }

    @Test
    public void testInvestmentPlanMatchIsResetForAnotherSecurity()
    {
        useExistingSecurity(planned);
        check();

        // the other security has a regular duplicate
        useExistingSecurity(existing);
        check();

        assertThat(purchase.getMaxCode(), is(Code.WARNING));
        assertThat(purchase.getItem().isInvestmentPlanItem(), is(false));
        assertThat(purchase.isImported(), is(false));
    }
}
