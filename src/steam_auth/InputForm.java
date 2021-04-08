package steam_auth;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;

public class InputForm extends JDialog {
    private JPanel rootPanel;
    private JButton cancelButton;
    private JButton acceptButton;
    public JTextField inputTextField;
    public JLabel lableText;

    public boolean resultDialog = false;

    public boolean isOK(){
        return resultDialog;
    }
    public InputForm(JFrame owner, String msg){
        super(owner);
        createListeners();
        setContentPane(rootPanel);
        try {
            Image img = Toolkit.getDefaultToolkit().getImage(new URL("https://www.shareicon.net/data/16x16/2017/02/15/878904_media_512x512.png"));
            setIconImage(img);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }        setSize(400,300);
        setLocationRelativeTo(owner);
        setModalityType(ModalityType.TOOLKIT_MODAL);
        lableText.setText(msg);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, (screen.height - getHeight()) / 2);
        setVisible(true);
    }

    private void createListeners() {
        acceptButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resultDialog = true;
                dispose();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resultDialog = false;
                dispose();
            }
        });
    }
}
