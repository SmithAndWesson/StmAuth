package steam_auth;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;

import static steam_auth.AuthenticatorLinker.LinkResult.AwaitingFinalization;
import static steam_auth.UserLogin.LoginResult.BadCredentials;

public class LoginForm extends JDialog {
    private JPanel rootPanel;
    private JButton loginButton;
    private JTextField textPassword;
    private JTextField textUsername;

    public static SteamGuardAccount androidAccount;
    public static String username;
    public static String password;

    public LoginForm(ModalityType type) {
        setContentPane(rootPanel);
        createListeners();
        setLocationRelativeTo(null);
        pack();
        if (type != null)
            setModalityType(ModalityType.TOOLKIT_MODAL);
        setTitle("Login");
        try {
            Image img = Toolkit.getDefaultToolkit().getImage(new URL("https://www.shareicon.net/data/16x16/2017/02/15/878904_media_512x512.png"));
            setIconImage(img);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
        if (type != null)
            setVisible(true);
    }

    public void createListeners() {
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    btnSteamLogin_Click(LoginType.Initial);
                } catch (IOException | NoSuchAlgorithmException | URISyntaxException e1) {
                    e1.printStackTrace();
                }
            }
        });
    }

    public void btnSteamLogin_Click(LoginType LoginReason) throws IOException, NoSuchAlgorithmException, URISyntaxException {

        if (LoginReason == LoginType.Refresh)
        {
            System.out.println("Relogin to Steam account \"" + androidAccount.AccountName + "\"");
            username = androidAccount.AccountName;
            password = androidAccount.getSession().AccountPassword;
            RefreshLogin(username, password);
            return;
        }

        username = textUsername.getText();
        password = textPassword.getText();

        UserLogin userLogin = new UserLogin(username, password);
        UserLogin.LoginResult response = BadCredentials;

        while ((response = userLogin.DoLogin()) != UserLogin.LoginResult.LoginOkay) {
            InputForm dialog = null;
            switch (response) {
                case NeedEmail:
                    dialog = new InputForm(null, "Enter the code sent to your email:");
                    if (!dialog.resultDialog) {
                        this.dispose();
                        return;
                    }
                    userLogin.EmailCode = dialog.inputTextField.getText();
                    break;

                case NeedCaptcha:
                    break;

                case Need2FA:
                    JOptionPane.showMessageDialog(this, "This account already has a mobile authenticator linked to it.\\nRemove the old authenticator from your Steam account before adding a new one.");
                    return;

                case BadRSA:
                    JOptionPane.showMessageDialog(this, "Error logging in: Steam returned \"BadRSA\".");
                    return;

                case BadCredentials:
                    JOptionPane.showMessageDialog(this, "Error logging in: Username or password was incorrect.");
                    return;

                case TooManyFailedLogins:
                    JOptionPane.showMessageDialog(this, "Error logging in: Too many failed logins, try again later.");
                    return;

                case GeneralFailure:
                    JOptionPane.showMessageDialog(this, "Error logging in: Steam returned \"GeneralFailure\".");
                    return;
            }
        }

        //Login succeeded
        SessionData session = userLogin.Session;
        AuthenticatorLinker linker = new AuthenticatorLinker(session);

        AuthenticatorLinker.LinkResult linkResponse = AuthenticatorLinker.LinkResult.GeneralFailure;

        while ((linkResponse = linker.AddAuthenticator()) != AwaitingFinalization) {
            switch (linkResponse)
            {
                case MustProvidePhoneNumber:
                    String phoneNumber = "";
                    while (!PhoneNumberOkay(phoneNumber)) {
                        InputForm dialog = new InputForm(null, "Enter your phone number in the following format: +{cC} phoneNumber. EG, +1 123-456-7890");
                        if (!dialog.resultDialog) {
                            this.dispose();
                            return;
                        }
                        phoneNumber = FilterPhoneNumber(dialog.inputTextField.getText());
                    }
                    linker.PhoneNumber = phoneNumber;
                    break;

                case RateLimitExceeded:
                    JOptionPane.showMessageDialog(this, "Error! Your account called API too many times.");
                    linker.PhoneNumber = null;
                    return;

                case MustRemovePhoneNumber:
                    linker.PhoneNumber = null;
                    break;

                case MustConfirmEmail:
                    JOptionPane.showMessageDialog(this, "Please check your email, and click the link Steam sent you before continuing.");
                    break;

                case GeneralFailure:
                    JOptionPane.showMessageDialog(this, "Error adding your phone number. Steam returned \"GeneralFailure\".");
                    return;
            }
        }

        Manifest manifest = Manifest.GetManifest(false);

        //Save the file immediately; losing this would be bad.
        if (!manifest.SaveAccount(linker.LinkedAccount, false, null))
        {
            manifest.RemoveAccount(linker.LinkedAccount, true);
            JOptionPane.showMessageDialog(this, "Unable to save mobile authenticator file. The mobile authenticator has not been linked.");
            return;
        }

        JOptionPane.showMessageDialog(this, "The Mobile Authenticator has not yet been linked. Before finalizing the authenticator, please write down your revocation code: " + linker.LinkedAccount.RevocationCode);

        AuthenticatorLinker.FinalizeResult finalizeResponse = AuthenticatorLinker.FinalizeResult.GeneralFailure;
        while (finalizeResponse != AuthenticatorLinker.FinalizeResult.Success)
        {
            InputForm dialog = new InputForm(null, "Please input the SMS code sent to your phone.");
            if (!dialog.resultDialog) {
                manifest.RemoveAccount(linker.LinkedAccount, true);
                this.dispose();
                return;
            }

            String smsCode = dialog.inputTextField.getText();
            finalizeResponse = linker.FinalizeAddAuthenticator(smsCode);

            switch (finalizeResponse)
            {
                case BadSMSCode:
                    JOptionPane.showMessageDialog(this, "The verification SMS code is incorrect, please try again.");
                    continue;

                case UnableToGenerateCorrectCodes:
                    JOptionPane.showMessageDialog(this, "Unable to generate the proper codes to finalize this authenticator. The authenticator should not have been linked. In the off-chance it was, please write down your revocation code, as this is the last chance to see it: " + linker.LinkedAccount.RevocationCode);
                    manifest.RemoveAccount(linker.LinkedAccount, true);
                    return;

                case GeneralFailure:
                    JOptionPane.showMessageDialog(this, "Unable to finalize this authenticator. The authenticator should not have been linked. In the off-chance it was, please write down your revocation code, as this is the last chance to see it: " + linker.LinkedAccount.RevocationCode);
                    manifest.RemoveAccount(linker.LinkedAccount, true);
                    return;
            }
        }

        //Linked, finally. Re-save with FullyEnrolled property.
        manifest.SaveAccount(linker.LinkedAccount, false, null);
        JOptionPane.showMessageDialog(this, "Mobile authenticator successfully linked. Please write down your revocation code: " + linker.LinkedAccount.RevocationCode);
        this.dispose();
    }

    public static boolean PhoneNumberOkay(String phoneNumber)
    {
        if (phoneNumber == null || phoneNumber.length() == 0) return false;
        if (!phoneNumber.startsWith("+")) return false;
        return true;
    }

    public static String FilterPhoneNumber(String phoneNumber)
    {
        return phoneNumber.replaceAll("-", "").replaceAll("\\(", "").replaceAll("\\)", "");
    }

    public void RefreshLogin(String username, String password) throws IOException {
        long steamTime = TimeAligner.GetSteamTime();
        Manifest man = Manifest.GetManifest(false);

        androidAccount.FullyEnrolled = true;

        UserLogin mUserLogin = new UserLogin(username, password);
        UserLogin.LoginResult response = BadCredentials;

        while ((response = mUserLogin.DoLogin()) != UserLogin.LoginResult.LoginOkay)
        {
            switch (response)
            {
                case NeedEmail:
                    InputForm dialog = new InputForm(null, "Enter the code sent to your email:");
                    if (!dialog.resultDialog) {
                        this.dispose();
                        return;
                    }
                    mUserLogin.EmailCode = dialog.inputTextField.getText();
                    break;

                case NeedCaptcha:
                    // Sorry, I don't know what do i need to do here.
                    break;

                case Need2FA:
                    mUserLogin.TwoFactorCode = androidAccount.GenerateSteamGuardCodeForTime(steamTime);
                    break;

                case BadRSA:
                    if (this.isVisible())
                        JOptionPane.showMessageDialog(this, "Error logging in: Steam returned \"BadRSA\".");
                    else
                        System.out.println("Error logging in: Steam returned \"BadRSA\".");
                    return;

                case BadCredentials:
                    if (this.isVisible())
                        JOptionPane.showMessageDialog(this, "Error logging in: Username or password was incorrect.");
                    else
                        System.out.println("Error logging in: Username or password was incorrect.");
                    return;

                case TooManyFailedLogins:
                    if (this.isVisible())
                        JOptionPane.showMessageDialog(this, "Error logging in: Too many failed logins, try again later.");
                    else
                        System.out.println("Error logging in: Too many failed logins, try again later.");
                    return;

                case GeneralFailure:
                    if (this.isVisible())
                        JOptionPane.showMessageDialog(this, "Error logging in: Steam returned \"GeneralFailure\".");
                    else
                        System.out.println("Error logging in: Steam returned \"GeneralFailure\".");
                    return;
            }
        }

        androidAccount.setSession(mUserLogin.Session);

        HandleManifest(man, true);
    }

    private static void HandleManifest(Manifest man, boolean IsRefreshing)
    {
        String passKey = null;

        man.SaveAccount(androidAccount, passKey != null, passKey);
        if (IsRefreshing)
        {
            System.out.println("Your login session was refreshed.");
        }
        else
        {
            System.out.println("Mobile authenticator successfully linked. Please write down your revocation code: " + androidAccount.RevocationCode);
        }
    }

    public enum LoginType
    {
        Initial,
        Android,
        Refresh
    }

}
