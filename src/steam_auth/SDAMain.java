package steam_auth;

import java.util.List;

public class SDAMain {
    public static SteamGuardAccount currentAccount = null;
    private static List<SteamGuardAccount> allAccounts = null;
    private static Manifest manifest;

    private static void loadAccountsList() {
        currentAccount = null;
        allAccounts = manifest.GetAllAccounts("");
    }

    private static void loadAccountInfo() {
        if (currentAccount != null) {
            String text = currentAccount.GenerateSteamGuardCode();
            System.out.println(text + "\t" + currentAccount.AccountName);
        }
    }

    public static void main(String[] args) {
        manifest = Manifest.GetManifest(false);
        loadAccountsList();
        currentAccount = allAccounts.get(0);
        loadAccountInfo();
        while (true) {
            try {
                List<Confirmation> confirmations = currentAccount.FetchConfirmations();
            } catch (SteamGuardAccount.WGTokenInvalidException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
