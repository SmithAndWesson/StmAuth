package steam_auth;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

public class SDAForm extends JFrame {
    public static SDAForm formSDA;
    public static SteamGuardAccount currentAccount = null;
    public static List<SteamGuardAccount> allAccounts = null;
    private static long steamTime = 0;
    private static long currentSteamChunk = 0;
    private static Manifest manifest;
    public static boolean autoAcceptConfirmations = false;

    private JPanel rootPanel;
    private JProgressBar progressBar1;
    private JTextField currentCode;
    private JButton copyButton;
    private JList listAccounts;
    private JButton setupNewAccountButton;
    private JButton deleteAuthenticatorButton;
    private JButton reLoginButton;
    private JButton autoConfirmationButton;

    private Icon ico;

    public SDAForm() {
        setContentPane(rootPanel);
        setTitle("Steam Desktop Authenticator v.1.0");
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
        try {
            Image img = Toolkit.getDefaultToolkit().getImage(new URL("https://www.shareicon.net/data/16x16/2017/02/15/878904_media_512x512.png"));
            setIconImage(img);
            ico = new ImageIcon(img);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        autoConfirmationButton.setBackground(Color.RED);
        autoConfirmationButton.setForeground(Color.WHITE);
        progressBar1.setStringPainted(true);
        progressBar1.setMinimum(0);
        progressBar1.setMaximum(30);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        // создаем слушателей для элементов формы
        createListeners();
        // считываем с диска файлы с данными от аккаунтов
        manifest = Manifest.GetManifest(false);
        // получаем список аккаунтов и выбираем первый из списка
        loadAccountsList();
        // подключаем таймер для обновления Steam Guard Code
        timerSteamGuard_Tick();
    }

    public void createListeners() {
        listAccounts.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                for (int i = 0; i < allAccounts.size(); i++)
                {
                    // Check if index is out of bounds first
                    if (i < 0 || listAccounts.getSelectedIndex() < 0)
                        continue;

                    SteamGuardAccount account = allAccounts.get(i);
                    if (account.AccountName.equals(listAccounts.getModel().getElementAt(listAccounts.getSelectedIndex()))) {
                        currentAccount = account;
                        loadAccountInfo();
                        break;
                    }
                }
            }
        });
        copyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String get = currentCode.getText();
                StringSelection selec= new StringSelection(get);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selec, selec);
            }
        });
        setupNewAccountButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoginForm loginForm = new LoginForm(Dialog.ModalityType.TOOLKIT_MODAL);
                manifest = Manifest.GetManifest(false);
                loadAccountsList();
            }
        });
        reLoginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoginForm loginForm = new LoginForm(null);
                loginForm.androidAccount = currentAccount;
                try {
                    loginForm.btnSteamLogin_Click(LoginForm.LoginType.Refresh);
                } catch (IOException | NoSuchAlgorithmException | URISyntaxException e1) {
                    loginForm.dispose();
                    e1.printStackTrace();
                }
                loginForm.dispose();
            }
        });
        deleteAuthenticatorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String accountName = (String)listAccounts.getModel().getElementAt(listAccounts.getSelectedIndex());
                String verificationText = (String)JOptionPane.showInputDialog(null, "If you really want to remove Steam Guard from " + accountName + ", please put \"12345\"", "Remove Steam Guard", JOptionPane.INFORMATION_MESSAGE, null, null, null);
                while(verificationText != null && !verificationText.equals("12345")) {
                    JOptionPane.showMessageDialog(null, "The verification code is wrong, please try again.", "Wrong code", JOptionPane.WARNING_MESSAGE);
                    verificationText = (String)JOptionPane.showInputDialog(null, "If you really want to remove Steam Guard from " + accountName + ", please put \"12345\"", "Remove Steam Guard", JOptionPane.INFORMATION_MESSAGE, null, null, null);
                }
                if (verificationText != null && verificationText.equals("12345")) {
                    boolean success = currentAccount.DeactivateAuthenticator(2);
                    if (success) {
                        JOptionPane.showMessageDialog(null, "Steam Guard from " + accountName + " removed completely.\nNOTICE: maFile will be deleted after hitting okay. If you need to make a backup, now's the time.", "Remove Steam Guard", 3, ico);
                        manifest.RemoveAccount(currentAccount, true);
                        loadAccountsList();
                    } else {
                        JOptionPane.showMessageDialog(null, "Steam Guard failed to deactivate.", "Remove Steam Guard", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
        autoConfirmationButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                autoAcceptConfirmations = !autoAcceptConfirmations;
                if (autoAcceptConfirmations) {
                    autoConfirmationButton.setBackground(Color.GREEN);
                    autoConfirmationButton.setForeground(Color.BLACK);
                    startAutoConfirmations();
                } else {
                    autoConfirmationButton.setBackground(Color.RED);
                    autoConfirmationButton.setForeground(Color.WHITE);
                }
            }
        });
    }

    /// <summary>
    /// Decrypts files and populates list UI with accounts
    /// </summary>
    private void loadAccountsList() {
        currentAccount = null;

        DefaultListModel listModel = new DefaultListModel();
        listAccounts.setModel(listModel);
        listModel.removeAllElements();
        listAccounts.setSelectedIndex(-1);

        allAccounts = manifest.GetAllAccounts("");

        if (allAccounts.size() > 0) {
            for (int i = 0; i < allAccounts.size(); i++) {
                SteamGuardAccount account = allAccounts.get(i);
                listModel.add(i, account.AccountName);
            }

            listAccounts.setSelectedIndex(0);
        }
    }

    /// <summary>
    /// Load UI with the current account info, this is run every second
    /// </summary>
    private void loadAccountInfo() {
        if (currentAccount != null && steamTime != 0)
        {
            currentCode.setText(currentAccount.GenerateSteamGuardCodeForTime(steamTime));
        }
    }

    private void timerSteamGuard_Tick() {
        Timer timer = new Timer(1000, new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                steamTime = TimeAligner.GetSteamTime();
                currentSteamChunk = steamTime / 30L;
                int secondsUntilChange = (int)(steamTime - (currentSteamChunk * 30L));

                loadAccountInfo();
                if (currentAccount != null)
                {
                    Date d = new Date();
                    progressBar1.setValue(30 - secondsUntilChange);
                    progressBar1.setString(30 - secondsUntilChange + " sec");
                }
            }
        });
        timer.start();
    }

    private void startAutoConfirmations() {

        for (SteamGuardAccount account : allAccounts) {
            account.getSession().AddCookies();
            if (!account.isAlive())
                account.start();
        }
    }

    public static void menuRefreshSession_Click(SteamGuardAccount acoount)
    {
        boolean status = RefreshAccountSession(acoount);
        if (status == true)
        {
            System.out.println("Your session has been refreshed.");
            manifest.SaveAccount(acoount, manifest.Encrypted, null);
        }
        else {
            System.out.println("Failed to refresh your session.\nTry using the \"Login again\" option.");
            LoginForm loginForm = new LoginForm(null);
            loginForm.setVisible(false);
            loginForm.androidAccount = acoount;
            try {
                loginForm.btnSteamLogin_Click(LoginForm.LoginType.Refresh);
            } catch (IOException | NoSuchAlgorithmException | URISyntaxException e) {
                loginForm.dispose();
                e.printStackTrace();
            }
            loginForm.dispose();
        }
    }

    private static boolean RefreshAccountSession(SteamGuardAccount account)
    {
        if (account == null) return false;
        boolean refreshed = account.RefreshSession();
        return refreshed; //No exception thrown means that we either successfully refreshed the session or there was a different issue preventing us from doing so.
    }

    public static void main(String args[]) {
        formSDA = new SDAForm();
        formSDA.pack();
        formSDA.setVisible(true);
    }

    public void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
