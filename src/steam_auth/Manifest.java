package steam_auth;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Manifest implements Serializable {
    private static final long serialVersionUID = 8740264612621413542L;

    //[JsonProperty("encrypted")]
    public boolean Encrypted;

    //[JsonProperty("first_run")]
    public boolean FirstRun = true;

    //[JsonProperty("entries")]
    public List<ManifestEntry> Entries;

    //[JsonProperty("periodic_checking")]
    public boolean PeriodicChecking = false;

    //[JsonProperty("periodic_checking_interval")]
    public int PeriodicCheckingInterval = 5;

    //[JsonProperty("periodic_checking_checkall")]
    public boolean CheckAllAccounts = false;

    //[JsonProperty("auto_confirm_market_transactions")]
    public boolean AutoConfirmMarketTransactions = false;

    //[JsonProperty("auto_confirm_trades")]
    public boolean AutoConfirmTrades = false;

    private static Manifest _manifest;

    public static String GetExecutableDir()
    {
        return System.getProperty("user.dir") + "\\src\\steam_auth\\";
    }

    public static Manifest GetManifest(boolean forceLoad)
    {
        // Check if already staticly loaded
        if (_manifest != null && !forceLoad) {
            return _manifest;
        }

        // Find config dir and manifest file
        String maDir = Manifest.GetExecutableDir() + "/maFiles/";
        String maFile = maDir + "manifest.json";

        File maDirPath = new File(maDir);
        File maFilePath = new File(maFile);

        // If there's no config dir, create it
        if(!maDirPath.exists()) {
            _manifest = _generateNewManifest(false);
            return _manifest;
        }

        // If there's no manifest, create it
        if (!maFilePath.exists()) {
            _manifest = _generateNewManifest(true);
            return _manifest;
        }

        try {
            FileInputStream fiStream = new FileInputStream(maFile);
            ObjectInputStream objectStream = new ObjectInputStream(fiStream);

            Manifest _manifest = (Manifest) objectStream.readObject();

            fiStream.close();
            objectStream.close();

            if (_manifest.Encrypted && _manifest.Entries.size() == 0) {
                _manifest.Encrypted = false;
                _manifest.Save();
            }

            _manifest.RecomputeExistingEntries();
            _manifest.RecomputeOutsideEntries();

            return _manifest;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Manifest _generateNewManifest(boolean scanDir)
    {
        // No directory means no manifest file anyways.
        Manifest newManifest = new Manifest();
        newManifest.Encrypted = false;
        newManifest.PeriodicCheckingInterval = 5;
        newManifest.PeriodicChecking = false;
        newManifest.AutoConfirmMarketTransactions = false;
        newManifest.AutoConfirmTrades = false;
        newManifest.Entries = new ArrayList<ManifestEntry>();
        newManifest.FirstRun = true;

        // Take a pre-manifest version and generate a manifest for it.
        if (scanDir)
        {
            String maDir = Manifest.GetExecutableDir() + "/maFiles/";
            File maDirPath = new File(maDir);

            if (maDirPath.exists()) {
                File[] files = maDirPath.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!(file.getPath().lastIndexOf(".maFile") >= 0))
                            continue;
                        try {
                            FileInputStream fiStream = new FileInputStream(file.getPath());
                            ObjectInputStream objectStream = new ObjectInputStream(fiStream);
                            SteamGuardAccount account = (SteamGuardAccount) objectStream.readObject();

                            ManifestEntry newEntry = new ManifestEntry();
                            newEntry.Filename = file.getName();
                            newEntry.SteamID = account.getSession().SteamID;

                            newManifest.Entries.add(newEntry);

                            fiStream.close();
                            objectStream.close();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (newManifest.Entries.size() > 0)
                {
                    newManifest.Save();
                }
            }
        }

        if (newManifest.Save())
        {
            return newManifest;
        }

        return null;
    }

    private void RecomputeExistingEntries()
    {
        List<ManifestEntry> newEntries = new ArrayList<ManifestEntry>();
        String maDir = Manifest.GetExecutableDir() + "/maFiles/";

        for (ManifestEntry entry : this.Entries)
        {
            String filename = maDir + entry.Filename;
            File f = new File(filename);
            if (f.exists()) {
                newEntries.add(entry);
            }
        }

        this.Entries = newEntries;

        if (this.Entries.size() == 0) {
            this.Encrypted = false;
        }
    }

    private void RecomputeOutsideEntries()
    {
        String maDir = Manifest.GetExecutableDir() + "/maFiles/";
        File maDirPath = new File(maDir);

        if (maDirPath.exists()) {
            File[] files = maDirPath.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!(file.getPath().lastIndexOf(".maFile") >= 0))
                        continue;
                    try {
                        FileInputStream fiStream = new FileInputStream(file.getPath());
                        ObjectInputStream objectStream = new ObjectInputStream(fiStream);
                        SteamGuardAccount account = (SteamGuardAccount) objectStream.readObject();

                        ManifestEntry newEntry = new ManifestEntry();
                        newEntry.Filename = file.getName();
                        newEntry.SteamID = account.getSession().SteamID;

                        if (!this.Entries.contains(newEntry)) {
                            this.Entries.add(newEntry);
                        }

                        fiStream.close();
                        objectStream.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (this.Entries.size() > 0) {
                this.Save();
            }
        }
    }

    public boolean RemoveAccount(SteamGuardAccount account, boolean deleteMaFile) {
        ManifestEntry entry = null;
        for (ManifestEntry e : this.Entries) {
            if (e.SteamID == account.getSession().SteamID) {
                entry = e;
                break;
            }
        }

        if (entry == null) return true; // If something never existed, did you do what they asked?

        String maDir = Manifest.GetExecutableDir() + "\\maFiles\\";
        String filename = maDir + entry.Filename;
        this.Entries.remove(entry);

        if (this.Entries.size() == 0) {
            this.Encrypted = false;
        }

        if (this.Save() && deleteMaFile) {
            try {
                Files.delete(Paths.get(filename));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }

        return false;
    }

    public boolean SaveAccount(SteamGuardAccount account, boolean encrypt, String passKey)
    {
        if (encrypt && (passKey.isEmpty() || passKey == null)) return false;
        if (!encrypt && this.Encrypted) return false;

        String salt = null;
        String iV = null;

        String maDir = Manifest.GetExecutableDir() + "/maFiles/";
        String filename = String.valueOf(account.getSession().SteamID) + ".maFile";

        ManifestEntry newEntry = new ManifestEntry();
        newEntry.SteamID = account.getSession().SteamID;
        newEntry.IV = iV;
        newEntry.Salt = salt;
        newEntry.Filename = filename;

        boolean foundExistingEntry = false;
        for (int i = 0; i < this.Entries.size(); i++) {
            if (this.Entries.get(i).SteamID == account.getSession().SteamID) {
                this.Entries.set(i, newEntry);
                foundExistingEntry = true;
                break;
            }
        }

        if (!foundExistingEntry) {
            this.Entries.add(newEntry);
        }

        boolean wasEncrypted = this.Encrypted;
        this.Encrypted = encrypt || this.Encrypted;

        if (!this.Save()) {
            this.Encrypted = wasEncrypted;
            return false;
        }

        try {
            FileOutputStream fileOutput = new FileOutputStream(maDir + filename);
            ObjectOutputStream outputStream = new ObjectOutputStream(fileOutput);

            outputStream.writeObject(account);

            fileOutput.close();
            outputStream.close();

            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean Save() {
        String maDir = Manifest.GetExecutableDir() + "/maFiles/";
        String filename = maDir + "manifest.json";

        File maDirPath = new File(maDir);

        if (!maDirPath.exists()) {
            maDirPath.mkdir();
        }

        try {
            FileOutputStream fileOutput = new FileOutputStream(filename);
            ObjectOutputStream outputStream = new ObjectOutputStream(fileOutput);

            outputStream.writeObject(this);

            fileOutput.close();
            outputStream.close();

            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public static class ManifestEntry implements Serializable {
        private static final long serialVersionUID = 4807116196338011157L;

        //[JsonProperty("encryption_iv")]
        public String IV;

        //[JsonProperty("encryption_salt")]
        public String Salt;

        //[JsonProperty("filename")]
        public String Filename;

        //[JsonProperty("steamid")]
        public long SteamID;

        @Override
        public boolean equals(Object obj) {
            ManifestEntry manifestEntry = (ManifestEntry)obj;
            String SteamID = String.valueOf(this.SteamID);
            String objSteamID = String.valueOf(manifestEntry.SteamID);
            return SteamID.equals(objSteamID);
        }
    }

    public List<SteamGuardAccount> GetAllAccounts(String passKey)
    {
        List<SteamGuardAccount> accounts = new ArrayList<SteamGuardAccount>();
        if (passKey == null && this.Encrypted) {
            accounts.add(new SteamGuardAccount());
            return accounts;
        }
        String maDir = Manifest.GetExecutableDir() + "/maFiles/";

        for (ManifestEntry entry : this.Entries) {
            try {
                FileInputStream fiStream = new FileInputStream(maDir + entry.Filename);
                ObjectInputStream objectStream = new ObjectInputStream(fiStream);
                SteamGuardAccount account = (SteamGuardAccount) objectStream.readObject();

                if (account == null) continue;
                accounts.add(account);

                fiStream.close();
                objectStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return accounts;
    }
}
