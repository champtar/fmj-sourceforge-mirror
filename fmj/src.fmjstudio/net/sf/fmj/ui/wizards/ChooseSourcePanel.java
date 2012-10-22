/*
 * ChooseSourcePanel.java
 *
 * Created on June 12, 2007, 4:34 PM
 */

package net.sf.fmj.ui.wizards;

import javax.media.*;
import javax.swing.*;

import net.sf.fmj.ui.application.*;
import net.sf.fmj.utility.*;

/**
 * 
 * @author Ken Larson
 */
public class ChooseSourcePanel extends javax.swing.JPanel
{
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton buttonBrowseFile;

    private javax.swing.JButton buttonChooseCaptureDevice;

    private javax.swing.JLabel jLabel1;

    private javax.swing.JTextField textFieldURL;

    /** Creates new form ChooseSourcePanel */
    public ChooseSourcePanel()
    {
        initComponents();
    }

    private void buttonBrowseFileActionPerformed(java.awt.event.ActionEvent evt)
    {// GEN-FIRST:event_buttonBrowseFileActionPerformed
        final JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            final String path = URLUtils
                    .createUrlStr(chooser.getSelectedFile());
            textFieldURL.setText(path);
        }
    }// GEN-LAST:event_buttonBrowseFileActionPerformed

    private void buttonChooseCaptureDeviceActionPerformed(
            java.awt.event.ActionEvent evt)
    {// GEN-FIRST:event_buttonChooseCaptureDeviceActionPerformed
        MediaLocator locator = CaptureDeviceBrowser.run(null); // TODO: correct
                                                               // parent frame
        if (locator != null)
        {
            textFieldURL.setText(locator.toExternalForm());
        }

    }// GEN-LAST:event_buttonChooseCaptureDeviceActionPerformed
     // End of variables declaration//GEN-END:variables

    public javax.swing.JTextField getTextFieldURL()
    {
        return textFieldURL;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed"
    // desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        textFieldURL = new javax.swing.JTextField();
        buttonBrowseFile = new javax.swing.JButton();
        buttonChooseCaptureDevice = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();

        setLayout(new java.awt.GridBagLayout());

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(textFieldURL, gridBagConstraints);

        buttonBrowseFile.setText("File...");
        buttonBrowseFile.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                buttonBrowseFileActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(buttonBrowseFile, gridBagConstraints);

        buttonChooseCaptureDevice.setText("Capture...");
        buttonChooseCaptureDevice
                .addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent evt)
                    {
                        buttonChooseCaptureDeviceActionPerformed(evt);
                    }
                });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(buttonChooseCaptureDevice, gridBagConstraints);

        jLabel1.setText("Source URL:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(jLabel1, gridBagConstraints);

    }// </editor-fold>//GEN-END:initComponents

}
